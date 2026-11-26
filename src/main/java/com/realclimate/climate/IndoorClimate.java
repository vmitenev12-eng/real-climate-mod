package com.realclimate.climate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Воздушная масса помещения.
 *
 * Комната больше не переключается мгновенно между уличной температурой и
 * температурой у кондиционера. Для каждого игрока ведётся «своя» температура
 * воздуха вокруг него; она медленно дрейфует к равновесию помещения,
 * которое считается из источников тепла, вентиляционных решёток и утечек
 * через открытые двери, окна и щели из миниблоков. Поэтому переходы через
 * двери плавные: воздух «сохраняется» и постепенно смешивается с уличным,
 * а у открытой двери возникает сквозняк.
 *
 * Приточные устройства (внутренние блоки сплит-систем, решётки вентиляции)
 * учитываются отдельно от пассивных источников: вентилятор перемешивает
 * весь объём комнаты, поэтому их струя не размазывается по объёму. Каждое
 * такое устройство — термостат: оно само докручивает температуру струи,
 * пока комната не выйдет на уставку.
 *
 * Ограждающие конструкции учитываются по-настоящему: каждая граница комнаты
 * вносит теплопроводность area·λ/толщина (ThermalProperties), поэтому
 * деревянная бытовка держит тепло, каменный дом — плохо, тонкая стенка
 * из миниблоков — хуже всего.
 */
public final class IndoorClimate {
    private static final String AIR_KEY = "realclimate_air";
    private static final String ENC_KEY = "realclimate_enc";
    private static final int MAX_ROOM_CELLS = 2048;

    private IndoorClimate() {}

    /** 0 — на открытом воздухе, 1 — полностью внутри помещения. */
    public static double shelter(Level level, BlockPos pos) {
        if (level.canSeeSky(pos)) return 0.0;
        int sky = level.getBrightness(LightLayer.SKY, pos);
        double s = (15 - sky) / 15.0;
        return Math.max(0.0, Math.min(1.0, (s - 0.25) / 0.5));
    }

    /**
     * Сквозняк в точке: насколько близко открытый путь наружу (открытая дверь,
     * окно, щель из миниблоков). 0 — сквозняка нет, 1 — стоим в проёме.
     */
    public static double draftFactor(Level level, BlockPos pos) {
        double draft = 0.0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) continue;
                    for (Direction direction : Direction.values()) {
                        if (!MiniBlockIntegration.allowsAirPassage(level, cursor, direction)) continue;
                        BlockPos outside = cursor.relative(direction);
                        if (!level.isLoaded(outside) || !level.canSeeSky(outside)) continue;
                        draft = Math.max(draft,
                                MiniBlockIntegration.openGapRatio(level, cursor, direction));
                    }
                }
            }
        }
        return Math.max(0.0, Math.min(1.0, draft));
    }

    /**
     * Равновесная температура воздуха в помещении вокруг точки. Учитывает
     * источники тепла, внутренние блоки сплит-систем, подключённые
     * вентиляционные решётки, утечки наружу и теплопроводность ограждающих
     * конструкций (стены, окна, тонкие стенки из миниблоков). Если объём не
     * замкнут — равновесие стремится к уличной температуре.
     */
    public static double roomEquilibrium(Level level, BlockPos origin, double rawAmbient) {
        RoomScan scan = scanRoom(level, origin, rawAmbient);
        if (scan == null) return rawAmbient;
        return equilibrium(scan, rawAmbient);
    }

    private static double equilibrium(RoomScan scan, double rawAmbient) {
        double spread = 1.0 + Math.sqrt(scan.volume) / 8.0;
        // Утечка нормируется на размер комнаты: открытая дверь в большом зале
        // сквозит и тянет тепло, но не превращает весь объём в улицу.
        double norm = Math.max(5.0, scan.volume * 0.04);
        double leak = Math.min(1.0, scan.openings / norm * 1.2 + (scan.overflow ? 0.5 : 0.0));
        // Ограждающие конструкции: envelope = Σ (площадь × λ / толщина).
        // Большой envelope — тонкие/металлические/стеклянные стены, тепло уходит.
        double envelopeDamping = 1.0 / (1.0 + scan.envelope * 0.12);
        // Пассивные источники (радиаторы, печи, факелы, лёд): тепло разлетается
        // по объёму и вымывается утечками и теплопроводностью стен.
        double passive = (scan.heat / spread) * (1.0 - leak) * envelopeDamping;
        // Приток от вентиляторов: струя перемешивает всю комнату и не делится
        // на объём. Несколько устройств не суммируют мощность — берётся средняя
        // температура струи, а охват растёт с их числом. Утечки струю смывают.
        double forced = 0.0;
        if (scan.acCount > 0) {
            double coupling = Math.min(0.95, 0.75 + 0.10 * (scan.acCount - 1));
            forced += (scan.acEmissionSum / scan.acCount - rawAmbient) * coupling;
        }
        if (scan.grilleCount > 0) {
            double coupling = Math.min(0.9, 0.65 + 0.15 * (scan.grilleCount - 1));
            forced += (scan.grilleEmissionSum / scan.grilleCount - rawAmbient) * coupling;
        }
        return rawAmbient + passive + forced * (1.0 - leak);
    }

    /** Текущая температура воздуха вокруг игрока (с инерцией помещения). */
    public static double getAir(Player player) {
        return player.getPersistentData().contains(AIR_KEY)
                ? player.getPersistentData().getDouble(AIR_KEY)
                : Double.NaN;
    }

    /**
     * Насколько игрок «внутри помещения» по ТОПОЛОГИИ комнаты (скан объёма),
     * а не по освещённости: 0 — улица или широкий проём, 1 — глухая комната.
     * В отличие от shelter() (свет неба), остеклённое окно не превращает
     * комнату в «улицу»: свет проходит сквозь стекло, а воздух — нет.
     * Именно это значение использует HUD, поэтому его показания сходятся
     * с термодатчиками, считающими то же равновесие по тому же скану.
     */
    public static double enclosure(Player player) {
        return player.getPersistentData().contains(ENC_KEY)
                ? player.getPersistentData().getDouble(ENC_KEY)
                : Double.NaN;
    }

    /** Продвинуть температуру воздуха игрока к равновесию. Вызывается раз в 40 тиков. */
    public static void updatePlayer(ServerPlayer player) {
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        double raw = ClimateService.temperature(player.getX(), player.getZ(), player.getY());
        if (!level.canSeeSky(pos) && pos.getY() < level.getSeaLevel() - 8) {
            raw = raw * 0.4 + 6.0;   // глубокие пещеры стабилизируются около +10 °C
        }
        double current = getAir(player);
        if (Double.isNaN(current)) current = raw;
        RoomScan scan = scanRoom(level, pos, raw);
        double equilibrium = scan == null ? raw : equilibrium(scan, raw);
        // Герметичность по скану объёма: 0 — открыто наружу, 1 — замкнутая
        // комната. Нормировка на размер комнаты: одна открытая дверь не
        // превращает большой зал в «улицу». Сглаживаем, чтобы у проёма
        // не было мгновенных переключений.
        double encTarget = scan == null ? 0.0
                : 1.0 - Math.min(1.0, scan.openings / Math.max(5.0, scan.volume * 0.04)
                        + (scan.overflow ? 0.5 : 0.0));
        double enc = enclosure(player);
        if (Double.isNaN(enc)) enc = encTarget;
        enc += (encTarget - enc) * 0.25;
        player.getPersistentData().putDouble(ENC_KEY, enc);
        // На улице воздух следует за погодой быстро, в замкнутой комнате —
        // инерционно. У открытой двери сквозняк ускоряет обмен в обе стороны.
        double draft = draftFactor(level, pos);
        double rate = 0.30 * (1.0 - enc) + 0.05 * enc;
        rate = Math.min(1.0, rate + draft * 0.15);
        current += (equilibrium - current) * rate;
        player.getPersistentData().putDouble(AIR_KEY, current);
    }

    private static RoomScan scanRoom(Level level, BlockPos origin, double rawAmbient) {
        Set<Long> room = new HashSet<>();
        Set<Long> boundary = new HashSet<>();
        double openings = 0.0;
        double heat = 0.0;
        double envelope = 0.0;
        int acCount = 0;
        double acEmissionSum = 0.0;
        int grilleCount = 0;
        double grilleEmissionSum = 0.0;
        boolean overflow = false;

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = origin.immutable();
        room.add(start.asLong());
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos cell = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = cell.relative(direction);
                long key = neighbor.asLong();
                if (room.contains(key) || boundary.contains(key)) continue;
                if (!level.isLoaded(neighbor)) { overflow = true; continue; }
                BlockState state = level.getBlockState(neighbor);
                if (state.isAir()) {
                    if (level.canSeeSky(neighbor)) {
                        // открытый проход наружу: не расширяемся за пределы постройки
                        boundary.add(key);
                        openings += 0.5;
                        continue;
                    }
                    if (room.size() >= MAX_ROOM_CELLS) { overflow = true; continue; }
                    room.add(key);
                    queue.add(neighbor.immutable());
                    continue;
                }
                boundary.add(key);
                if (MiniBlockIntegration.allowsAirPassage(level, neighbor, direction)) {
                    openings += MiniBlockIntegration.openGapRatio(level, neighbor, direction);
                }
                // Приточные устройства — отдельными термами (их струя
                // перемешивает комнату и не делится на объём), чтобы не
                // дублировать их вклад в пассивном тепле.
                if (state.getBlock() instanceof com.realclimate.block.ACIndoorBlock
                        && state.hasProperty(com.realclimate.block.ACIndoorBlock.ACTIVE)
                        && state.getValue(com.realclimate.block.ACIndoorBlock.ACTIVE)
                        && level.getBlockEntity(neighbor) instanceof com.realclimate.tileentity.ACIndoorBlockEntity ac
                        && ac.isEnabled()) {
                    acCount++;
                    double emission = ac.getEmission();
                    acEmissionSum += Double.isNaN(emission) ? ac.getTargetTemp() : emission;
                } else if (state.getBlock() instanceof com.realclimate.block.GrilleBlock
                        && level.getBlockEntity(neighbor) instanceof com.realclimate.tileentity.GrilleBlockEntity grille
                        && grille.isOpen() && VentilationService.hasActiveAirflow(level, neighbor)) {
                    grilleCount++;
                    double emission = grille.getEmission();
                    if (Double.isNaN(emission)) {
                        emission = VentilationService.airflowTemperature(level, neighbor, grille.getTargetTemp());
                    }
                    grilleEmissionSum += emission;
                } else {
                    heat += BodyTemperature.sourceStrength(level, neighbor, state);
                }
                // Теплопроводность ограждения: тонкая стенка из миниблоков
                // пропускает тепло пропорционально своей толщине.
                envelope += ThermalProperties.faceConductance(level, neighbor, direction);
            }
        }
        return new RoomScan(room.size(), openings, heat, envelope,
                acCount, acEmissionSum, grilleCount, grilleEmissionSum, overflow);
    }

    private record RoomScan(int volume, double openings, double heat, double envelope,
                            int acCount, double acEmissionSum,
                            int grilleCount, double grilleEmissionSum, boolean overflow) {}
}
