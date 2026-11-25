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
 * Ограждающие конструкции учитываются по-настоящему: каждая граница комнаты
 * вносит теплопроводность area·λ/толщина (ThermalProperties), поэтому
 * деревянная бытовка держит тепло, каменный дом — плохо, тонкая стенка
 * из миниблоков — хуже всего.
 */
public final class IndoorClimate {
    private static final String AIR_KEY = "realclimate_air";
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
     * источники тепла, подключённые вентиляционные решётки, утечки наружу и
     * теплопроводность ограждающих конструкций (стены, окна, тонкие стенки
     * из миниблоков). Если объём не замкнут — равновесие стремится к уличной
     * температуре.
     */
    public static double roomEquilibrium(Level level, BlockPos origin, double rawAmbient) {
        RoomScan scan = scanRoom(level, origin, rawAmbient);
        if (scan == null) return rawAmbient;
        double spread = 1.0 + Math.sqrt(scan.volume) / 8.0;
        double leak = Math.min(1.0, scan.openings * 0.25 + (scan.overflow ? 0.5 : 0.0));
        // Каждая решётка тянет воздух к температуре своей системы, но мощность
        // системы делится между всеми её решётками: (flow - raw) / systemSize.
        // Несколько решёток в одной системе не складывают температуру.
        double grilleSaturation = scan.grilleCount <= 1 ? 0.6 : Math.min(1.0, 0.6 + 0.2 * (scan.grilleCount - 1));
        double grilleTerm = scan.grilleTempSum * grilleSaturation;
        // Ограждающие конструкции: envelope = Σ (площадь × λ / толщина).
        // Большой envelope — тонкие/металлические/стеклянные стены, тепло уходит.
        double envelopeDamping = 1.0 / (1.0 + scan.envelope * 0.12);
        return rawAmbient + (scan.heat / spread + grilleTerm) * (1.0 - leak) * envelopeDamping;
    }

    /** Текущая температура воздуха вокруг игрока (с инерцией помещения). */
    public static double getAir(Player player) {
        return player.getPersistentData().contains(AIR_KEY)
                ? player.getPersistentData().getDouble(AIR_KEY)
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
        double shelter = shelter(level, pos);
        double equilibrium = roomEquilibrium(level, pos, raw);
        // На улице воздух меняется быстро, в помещении — инерционно.
        // У открытой двери сквозняк ускоряет обмен в обе стороны.
        double draft = draftFactor(level, pos);
        double rate = 0.30 * (1.0 - shelter) + 0.04 * shelter;
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
        int grilleCount = 0;
        double grilleTempSum = 0.0;
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
                heat += BodyTemperature.sourceStrength(level, neighbor, state);
                // Теплопроводность ограждения: тонкая стенка из миниблоков
                // пропускает тепло пропорционально своей толщине.
                envelope += ThermalProperties.faceConductance(level, neighbor, direction);
                if (state.getBlock() instanceof com.realclimate.block.GrilleBlock
                        && level.getBlockEntity(neighbor) instanceof com.realclimate.tileentity.GrilleBlockEntity grille
                        && grille.isOpen() && VentilationService.hasActiveAirflow(level, neighbor)) {
                    grilleCount++;
                    double flow = VentilationService.airflowTemperature(level, neighbor, grille.getTargetTemp());
                    int systemSize = VentilationService.grilleSystemSize(level, neighbor);
                    grilleTempSum += (flow - rawAmbient) / systemSize;
                }
            }
        }
        return new RoomScan(room.size(), openings, heat, envelope, grilleCount, grilleTempSum, overflow);
    }

    private record RoomScan(int volume, double openings, double heat, double envelope,
                            int grilleCount, double grilleTempSum, boolean overflow) {}
}
