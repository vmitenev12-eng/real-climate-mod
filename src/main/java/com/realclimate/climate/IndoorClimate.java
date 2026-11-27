package com.realclimate.climate;

import com.realclimate.block.ACIndoorBlock;
import com.realclimate.block.GrilleBlock;
import com.realclimate.tileentity.ACIndoorBlockEntity;
import com.realclimate.tileentity.GrilleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;

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
 *
 * Тепловая масса помещения: стены, пол и потолок накапливают тепло/холод.
 * Когда приборы выключены, комната не остывает мгновенно — масса отдаёт
 * тепло обратно в воздух и постепенно развеивается через ограждения
 * (шерсть/дерево — медленно, камень — быстрее, металл — почти сразу).
 * Открытая дверь или дыра в стене резко ускоряет выветривание: воздух
 * стремительно принимает уличную температуру. Если дверь приоткрыли
 * и закрыли — прогретые стены быстро возвращают воздух почти к прежней
 * температуре, и проветривание почти незаметно.
 *
 * Масса ведётся по ячейкам 8×8×8, но ВСЕ ячейки одной просканированной
 * комнаты обновляются синхронно и делят одну усреднённую температуру:
 * кондиционер греет/охлаждает комнату целиком, а не свой угол — поэтому
 * термодатчики, пульт и HUD в любой точке комнаты сходятся, а не живут
 * минутами в разных «погодах».
 *
 * Одна комната — один скан (кэш помещений): обход объёма стартует из точки
 * замера, и в большом зале (> старого лимита 2048 клеток) обход из разных
 * углов захватывал РАЗНЫЕ части комнаты — HUD посреди зала не «видел»
 * кондиционеров у стены, а три датчика жили в трёх разных «погодах».
 * Теперь первый полный обход запоминается, и все приборы и игроки внутри
 * того же объёма пользуются одним и тем же сканом. Температура струй
 * (кондиционеры, решётки) перечитывается живьём при каждом обращении,
 * поэтому регуляторы не теряют отклик. Геометрия инвалидируется мгновенно
 * при изменении блоков в комнате и на её границе (дверь, сломанная стена),
 * а лимит обхода поднят до 16384 клеток — целый торговый зал помещается
 * целиком.
 *
 * Переполнение скана (здание больше лимита обхода) — это НЕ улица:
 * большой торговый центр или больница остаются помещением, поэтому
 * штраф за переполнение минимальный, а герметичность не страдает вовсе.
 * Переполненные зоны тоже кэшируются: гигантское здание делится на
 * согласованные «климатические зоны», единые внутри себя.
 */
public final class IndoorClimate {
    private static final String AIR_KEY = "realclimate_air";
    private static final String ENC_KEY = "realclimate_enc";
    private static final int MAX_ROOM_CELLS = 16384;
    /** Сколько тиков скан помещения считается свежим без сигнала об изменении блоков. */
    private static final long ROOM_CACHE_TTL_TICKS = 40;
    /** Карманные полости меньше этого объёма не кэшируем — их обход и так мгновенный. */
    private static final int MIN_CACHED_ROOM_VOLUME = 8;
    /** Порог ячеек индекса кэша, после которого вычищаем устаревшие записи. */
    private static final int ROOM_CACHE_CELL_LIMIT = 4096;

    /**
     * Тепловая масса мира, привязанная к блокам 8×8×8: стены помещения
     * помнят свою температуру между замерами и отдают её воздуху.
     * Только серверная сторона — клиентские замеры сюда не пишут.
     */
    private static final Map<MassKey, MassCell> MASS_CELLS = new ConcurrentHashMap<>();

    /**
     * Кэш сканов помещений: ячейка 8×8×8 -> помещения, чей объём её касается.
     * Позволяет всем приборам и игрокам одной комнаты пользоваться одним
     * и тем же обходом, а не сканировать заново из каждой точки замера.
     */
    private static final Map<CellKey, CopyOnWriteArrayList<CachedRoom>> ROOM_CACHE = new ConcurrentHashMap<>();

    private IndoorClimate() {}

    /** Очистка тепловой массы и кэша помещений при остановке сервера. */
    public static void clear() {
        MASS_CELLS.clear();
        ROOM_CACHE.clear();
    }

    /**
     * Инвалидация кэша помещений при изменении блока: помечаем все
     * закэшированные комнаты, чей объём содержит сам блок или его соседей
     * по граням (стена/дверь сами не входят в объём — он воздушный, — но
     * примыкают к нему вплотную). Следующий замер пересканирует комнату.
     */
    public static void invalidateRoomAt(Level level, BlockPos pos) {
        invalidateCell(level, pos);
        for (Direction direction : Direction.values()) {
            invalidateCell(level, pos.relative(direction));
        }
    }

    private static void invalidateCell(Level level, BlockPos pos) {
        CopyOnWriteArrayList<CachedRoom> list = ROOM_CACHE.get(new CellKey(level.dimension(), packCell(pos)));
        if (list == null || list.isEmpty()) return;
        long key = pos.asLong();
        for (CachedRoom cached : list) {
            if (cached.room.contains(key)) cached.invalidated.set(true);
        }
    }

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
     * конструкций (стены, окна, тонкие стенки из миниблоков), а также
     * тепловую массу прогретых/охлаждённых стен. Если объём не замкнут —
 * равновесие стремится к уличной температуре.
     */
    public static double roomEquilibrium(Level level, BlockPos origin, double rawAmbient) {
        RoomScan scan = findRoom(level, origin);
        if (scan == null) return rawAmbient;
        return equilibriumWithMass(level, origin, scan, rawAmbient);
    }

    /** Диагностика помещения для команды /climate body. */
    public static String roomDebug(Level level, BlockPos pos) {
        RoomScan scan = findRoom(level, pos);
        if (scan == null) return "комната не найдена";
        DeviceTerms dev = resolveDevices(level, scan);
        return String.format("объём %d | кондиционеров %d/%d | решёток %d/%d | утечки %.1f (небо %.1f, щели %.1f) | ограждение %.1f | переполнение %s | на границе: %s | над головой: %s",
                scan.volume, dev.acCount, scan.acPositions.size(), dev.grilleCount, scan.grillePositions.size(),
                scan.openings, scan.skyOpenings, scan.openings - scan.skyOpenings, scan.envelope,
                scan.overflow ? "ДА" : "нет",
                scan.boundaryDevices.isEmpty() ? "ничего" : scan.boundaryDevices,
                ceilingAbove(level, pos));
    }

    /** Первый не-воздушный блок над точкой замера (до 12 блоков вверх): что
     * именно находится между игроком и крышей — потолок, решётка или иное.
     * Плюс честно говорим, считает ли высотная карта воздух под этим блоком
     * открытым небом — так видно слепоту MOTION_BLOCKING. */
    private static String ceilingAbove(Level level, BlockPos pos) {
        for (int dy = 1; dy <= 12; dy++) {
            BlockPos cur = pos.above(dy);
            if (!level.isLoaded(cur)) return "?";
            BlockState state = level.getBlockState(cur);
            if (!state.isAir()) {
                net.minecraft.resources.ResourceLocation id =
                        net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
                return (id == null ? "?" : id.toString()) + " (+" + dy + "), небо под ним: "
                        + (level.canSeeSky(cur.below()) ? "да" : "нет");
            }
        }
        return "чистое небо (12+ блоков воздуха)";
    }

    /** Высотная карта MOTION_BLOCKING слепа к блокам без ванильной коллизии
     * (контейнеры LittleTiles) и местами врёт в колонках, где раньше стояли
     * миниблоки: воздух под реальной крышей/решёткой ошибочно «видит небо»,
     * и обход останавливался, не дойдя до блока над ним. Страховка: если
     * прямо над клеткой стоит любой не-воздушный блок — это не улица.
     * Смотрим ровно на 1 вверх: глубже нельзя, иначе навесы и кроны деревьев
     * начнут считаться «крышей» и обход уйдёт сканировать улицу. */
    private static boolean skyReallyOpen(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        if (!level.isLoaded(above)) return true;
        return level.getBlockState(above).isAir();
    }

    /**
     * Найти помещение для точки: сначала кэш (тот же объём уже обошли —
     * переиспользуем тот же скан, чтобы все точки комнаты видели один
     * климат), иначе полный обход и регистрация в кэше.
     */
    private static RoomScan findRoom(Level level, BlockPos origin) {
        long now = level.getGameTime();
        long originKey = origin.asLong();
        CopyOnWriteArrayList<CachedRoom> list = ROOM_CACHE.get(new CellKey(level.dimension(), packCell(origin)));
        if (list != null) {
            for (CachedRoom cached : list) {
                if (isStale(cached, now)) continue;
                if (cached.room.contains(originKey)) return cached.toScan();
            }
        }
        RoomScan scan = scanRoom(level, origin);
        if (scan != null && scan.volume >= MIN_CACHED_ROOM_VOLUME) {
            registerRoom(level, scan, now);
        }
        return scan;
    }

    private static boolean isStale(CachedRoom cached, long now) {
        return cached.invalidated.get() || now < cached.gameTime
                || now - cached.gameTime > ROOM_CACHE_TTL_TICKS;
    }

    /** Регистрация свежего скана: по всем ячейкам 8×8×8 объёма, с вытеснением протухших записей. */
    private static void registerRoom(Level level, RoomScan scan, long now) {
        CachedRoom cached = new CachedRoom(scan, now);
        for (long packed : scan.massKeys) {
            CopyOnWriteArrayList<CachedRoom> list = ROOM_CACHE.computeIfAbsent(
                    new CellKey(level.dimension(), packed), k -> new CopyOnWriteArrayList<>());
            list.removeIf(c -> isStale(c, now));
            list.add(0, cached);
        }
        if (ROOM_CACHE.size() > ROOM_CACHE_CELL_LIMIT) {
            ROOM_CACHE.values().forEach(l -> l.removeIf(c -> isStale(c, now)));
            ROOM_CACHE.entrySet().removeIf(e -> e.getValue().isEmpty());
        }
    }

    private static double equilibrium(Level level, RoomScan scan, double rawAmbient) {
        DeviceTerms dev = resolveDevices(level, scan);
        double spread = 1.0 + Math.sqrt(scan.volume) / 8.0;
        double leak = leakOf(scan);
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
        if (dev.acCount > 0) {
            double coupling = Math.min(0.95, 0.75 + 0.10 * (dev.acCount - 1));
            forced += (dev.acEmissionSum / dev.acCount - rawAmbient) * coupling;
        }
        if (dev.grilleCount > 0) {
            double coupling = Math.min(0.9, 0.65 + 0.15 * (dev.grilleCount - 1));
            forced += (dev.grilleEmissionSum / dev.grilleCount - rawAmbient) * coupling;
        }
        return rawAmbient + passive + forced * (1.0 - leak);
    }

    /** Потолочная решётка магистрали (vent_ceiling_grille) — декоративный
     * терминал без блока-сущности; распознаём по id, как трассировка воздуховода. */
    private static boolean isCeilingGrille(BlockState state) {
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && id.getNamespace().equals("real_climate")
                && id.getPath().equals("vent_ceiling_grille");
    }

    /**
     * Живой пересчёт вклада приточных устройств: геометрия комнаты берётся
     * из кэша, но состояние кондиционеров и решёток читается заново при
     * каждом замере — термостаты докручивают струю каждую секунду, и кэш
     * не должен их тормозить. Скан запоминает ПОЗИЦИИ устройств на границе
     * (в любом состоянии), а фильтры ACTIVE/enabled/открыта применяются здесь.
     */
    private static DeviceTerms resolveDevices(Level level, RoomScan scan) {
        int acCount = 0;
        double acEmissionSum = 0.0;
        for (BlockPos pos : scan.acPositions) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ACIndoorBlock
                    && state.hasProperty(ACIndoorBlock.ACTIVE)
                    && state.getValue(ACIndoorBlock.ACTIVE)
                    && level.getBlockEntity(pos) instanceof ACIndoorBlockEntity ac
                    && ac.isEnabled()) {
                acCount++;
                double emission = ac.getEmission();
                acEmissionSum += Double.isNaN(emission) ? ac.getTargetTemp() : emission;
            }
        }
        int grilleCount = 0;
        double grilleEmissionSum = 0.0;
        for (BlockPos pos : scan.grillePositions) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof GrilleBlock) {
                if (level.getBlockEntity(pos) instanceof GrilleBlockEntity grille
                        && grille.isOpen() && VentilationService.hasActiveAirflow(level, pos)) {
                    grilleCount++;
                    double emission = grille.getEmission();
                    if (Double.isNaN(emission)) {
                        emission = VentilationService.airflowTemperature(level, pos, grille.getTargetTemp());
                    }
                    grilleEmissionSum += emission;
                }
            } else if (isCeilingGrille(state) && VentilationService.hasActiveAirflow(level, pos)) {
                // У потолочной решётки нет блока-сущности и своего термостата:
                // из магистрали дует ровно та температура, что гонит наружный
                // блок (уставка пульта). Без активного потока — не устройство.
                grilleCount++;
                grilleEmissionSum += VentilationService.airflowTemperature(
                        level, pos, ClimateService.getTemperature(level, pos));
            }
        }
        return new DeviceTerms(acCount, acEmissionSum, grilleCount, grilleEmissionSum);
    }

    /** Утечка нормируется на размер комнаты: открытая дверь в большом зале
     * сквозит и тянет тепло, но не превращает весь объём в улицу.
     * Переполнение скана (огромное здание, длинные коридоры) — это НЕ улица,
     * а лишь «объём не поместился в лимит обхода», поэтому штраф за него
     * небольшой: чисто страховка от бесконечных пещер и края прогруженных
     * чанков. */
    private static double leakOf(RoomScan scan) {
        double norm = Math.max(8.0, scan.volume * 0.04);
        return Math.min(1.0, scan.openings / norm * 1.2 + (scan.overflow ? 0.2 : 0.0));
    }

    /**
     * Равновесие с учётом тепловой массы ограждений: нагретые стены тянут
     * воздух к своей температуре. Связь сильная у массивных стен и слабая
     * у тонких металлических, а при утечках (открытая дверь, дыра наружу)
     * почти исчезает — воздух тут же становится уличным.
     */
    private static double equilibriumWithMass(Level level, BlockPos origin, RoomScan scan, double rawAmbient) {
        double eq = equilibrium(level, scan, rawAmbient);
        if (level.isClientSide) return eq;
        double leak = leakOf(scan);
        double wallFactor = 1.0 / (1.0 + scan.envelope / Math.max(30.0, scan.volume) * 0.05);
        double coupling = 0.88 * wallFactor * (1.0 - leak);
        return thermalMass(level, eq, rawAmbient, coupling, scan);
    }

    /**
     * Температура воздуха с учётом стен и обновление массы ВСЕХ ячеек
     * комнаты разом. Усреднённая масса комнаты тянет воздух к себе
     * (air = eq + coupling·(масса − улица)), и каждая ячейка 8×8×8
     * догоняет этот общий воздух — поэтому у кондиционера, у датчика
     * и под ногами игрока одна и та же температура. Масса догоняет
     * воздух, который сама же и формирует, поэтому эффективная скорость
     * остывания пропорциональна (1 − coupling): хорошо держащие тепло
     * стены остывают медленнее всего. Скорость выветривания растёт
     * с теплопроводностью ограждения и с утечками.
     */
    private static double thermalMass(Level level, double eq, double raw,
                                      double coupling, RoomScan scan) {
        long now = level.getGameTime();
        ResourceKey<Level> dimension = level.dimension();

        // Средняя температура массы по ячейкам комнаты. Ещё не тронутые
        // ячейки в среднее не входят — свежая комната стартует с eq, скачка нет.
        double massSum = 0.0;
        int massCount = 0;
        for (long packed : scan.massKeys) {
            MassCell cell = MASS_CELLS.get(unpackCell(dimension, packed));
            if (cell != null) {
                massSum += cell.temp;
                massCount++;
            }
        }
        double avgMass = massCount > 0 ? massSum / massCount : eq;
        double air = eq + coupling * (avgMass - raw);

        // Все ячейки комнаты тянутся к общему воздуху с одной скоростью.
        double lossRate = 0.3
                + Math.min(2.5, scan.envelope / Math.max(20.0, scan.volume) * 0.5)
                + Math.min(2.5, scan.openings * 0.3)
                + (scan.overflow ? 1.0 : 0.0);
        for (long packed : scan.massKeys) {
            MassCell cell = MASS_CELLS.computeIfAbsent(
                    unpackCell(dimension, packed), key -> new MassCell(air, now));
            long dt = now - cell.gameTime;
            cell.gameTime = now;
            if (dt > 0) {
                dt = Math.min(dt, 2400);
                double k = 1.0 - Math.exp(-dt * lossRate * 3.0 / 6000.0);
                cell.temp += (air - cell.temp) * k;
            }
        }
        // Периодическая чистка старых ячеек, чтобы карта не разрасталась.
        if (MASS_CELLS.size() > 512) {
            MASS_CELLS.entrySet().removeIf(e -> now - e.getValue().gameTime > 24000);
        }
        return air;
    }

    /** Координаты ячейки 8×8×8, упакованные в long (>>3 == floorDiv для отрицательных). */
    private static long packCell(BlockPos pos) {
        return BlockPos.asLong(pos.getX() >> 3, pos.getY() >> 3, pos.getZ() >> 3);
    }

    private static MassKey unpackCell(ResourceKey<Level> dimension, long packed) {
        return new MassKey(dimension, BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
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
     * Переполнение скана герметичность НЕ снижает: большое здание целиком
     * остается помещением, иначе HUD в нём расходится с датчиками.
     */
    public static double enclosure(Player player) {
        return player.getPersistentData().contains(ENC_KEY)
                ? player.getPersistentData().getDouble(ENC_KEY)
                : Double.NaN;
    }

    /**
     * Локальная струя приборов: рядом с дующим кондиционером или открытой
     * решёткой с потоком воздух заметно ближе к температуре струи, чем средняя
     * по комнате — как тепло рядом с включённым радиатором. Затухает с
     * расстоянием (~5 блоков), суммарный предел ±3 °C: струя ощущается
     * вплотную, но не превращает угол в морозилку.
     *
     * Влияет только на ОЩУЩАЕМУЮ температуру (HUD игрока, показания
     * термодатчиков). Регуляторы самих приборов меряют объём комнаты без
     * этого терма — иначе кондиционер измерял бы собственное дыхание
     * и терял обратную связь с комнатой.
     */
    public static double localStream(Level level, BlockPos pos, double reference) {
        double delta = 0.0;
        for (BlockPos cur : BlockPos.betweenClosed(pos.offset(-4, -4, -4), pos.offset(4, 4, 4))) {
            BlockState state = level.getBlockState(cur);
            double emission = Double.NaN;
            if (state.getBlock() instanceof ACIndoorBlock
                    && state.hasProperty(ACIndoorBlock.ACTIVE)
                    && state.getValue(ACIndoorBlock.ACTIVE)
                    && level.getBlockEntity(cur) instanceof ACIndoorBlockEntity ac
                    && ac.isEnabled()) {
                emission = ac.getEmission();
                if (Double.isNaN(emission)) emission = ac.getTargetTemp();
            } else if (state.getBlock() instanceof GrilleBlock
                    && level.getBlockEntity(cur) instanceof GrilleBlockEntity grille
                    && grille.isOpen() && VentilationService.hasActiveAirflow(level, cur)) {
                emission = grille.getEmission();
                if (Double.isNaN(emission)) {
                    emission = VentilationService.airflowTemperature(level, cur, grille.getTargetTemp());
                }
            } else if (isCeilingGrille(state) && VentilationService.hasActiveAirflow(level, cur)) {
                // Струя из потолочной решётки ощущается так же, как из обычной.
                emission = VentilationService.airflowTemperature(
                        level, cur, ClimateService.getTemperature(level, cur));
            }
            if (Double.isNaN(emission)) continue;
            double dist = Math.sqrt(cur.distSqr(pos));
            if (dist >= 5.0) continue;
            double w = 1.0 - dist / 5.0;
            delta += (emission - reference) * 0.05 * w;
        }
        return Math.max(-3.0, Math.min(3.0, delta));
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
        RoomScan scan = findRoom(level, pos);
        double equilibrium = scan == null ? raw : equilibriumWithMass(level, pos, scan, raw);
        // Струя кондиционера/решётки рядом с игроком ощущается отдельно
        // от средней температуры комнаты (как тепло у радиатора).
        equilibrium += localStream(level, pos, equilibrium);
        // Герметичность по скану объёма: 0 — открыто наружу, 1 — замкнутая
        // комната. Нормировка на размер комнаты: одна открытая дверь не
        // превращает большой зал в «улицу». Сглаживаем, чтобы у проёма
        // не было мгновенных переключений.
        double encTarget = scan == null ? 0.0
                : 1.0 - Math.min(1.0, scan.openings / Math.max(8.0, scan.volume * 0.04));
        double enc = enclosure(player);
        if (Double.isNaN(enc)) enc = encTarget;
        enc += (encTarget - enc) * 0.25;
        player.getPersistentData().putDouble(ENC_KEY, enc);
        // На улице воздух следует за погодой быстро, в замкнутой комнате —
        // тоже заметно (полная сходимость ~20-30 секунд): долговременную
        // инерцию помещения теперь несёт тепловая масса стен, а не воздух
        // игрока. У открытой двери сквозняк ускоряет обмен в обе стороны.
        double draft = draftFactor(level, pos);
        double rate = 0.30 * (1.0 - enc) + 0.22 * enc;
        rate = Math.min(1.0, rate + draft * 0.15);
        current += (equilibrium - current) * rate;
        player.getPersistentData().putDouble(AIR_KEY, current);
    }

    private static RoomScan scanRoom(Level level, BlockPos origin) {
        Set<Long> room = new HashSet<>();
        Set<Long> boundary = new HashSet<>();
        Set<Long> massKeys = new HashSet<>();
        List<BlockPos> acPositions = new ArrayList<>();
        List<BlockPos> grillePositions = new ArrayList<>();
        double openings = 0.0;
        double skyOpenings = 0.0;
        double heat = 0.0;
        double envelope = 0.0;
        boolean overflow = false;
        Map<String, Integer> boundaryBlocks = new HashMap<>();

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = origin.immutable();
        room.add(start.asLong());
        massKeys.add(packCell(start));
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
                    if (level.canSeeSky(neighbor) && skyReallyOpen(level, neighbor)) {
                        // открытый проход наружу: не расширяемся за пределы постройки
                        boundary.add(key);
                        openings += 0.5;
                        skyOpenings += 0.5;
                        continue;
                    }
                    if (room.size() >= MAX_ROOM_CELLS) { overflow = true; continue; }
                    room.add(key);
                    massKeys.add(packCell(neighbor));
                    queue.add(neighbor.immutable());
                    continue;
                }
                boundary.add(key);
                // Диагностика: какие блоки вообще касаются комнаты
                // (видно в /climate body — «на границе: …»). Ваниль сворачиваем
                // в один счётчик, блоки модов — поимённо: так видно и
                // LittleTiles-контейнеры, и декорации других модов.
                net.minecraft.resources.ResourceLocation bid =
                        net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
                if (bid != null) {
                    String ns = bid.getNamespace();
                    String name = ns.equals("minecraft") ? "minecraft"
                            : ns.equals("real_climate") ? bid.getPath()
                            : ns + ":" + bid.getPath();
                    boundaryBlocks.merge(name, 1, Integer::sum);
                }
                // Дующая приточная решётка — не дыра на улицу: магистраль нагнетает
                // воздух В комнату, её вклад уже учтён термом струи. А вот решётка
                // без потока (система выключена) — честная щель в воздуховод.
                boolean supplyActive = (state.getBlock() instanceof GrilleBlock || isCeilingGrille(state))
                        && VentilationService.hasActiveAirflow(level, neighbor);
                if (!supplyActive && MiniBlockIntegration.allowsAirPassage(level, neighbor, direction)) {
                    openings += MiniBlockIntegration.openGapRatio(level, neighbor, direction);
                }
                // Приточные устройства запоминаем позициями — их состояние
                // (ACTIVE, струя, открытие) перечитывается живьём при каждом
                // замере (resolveDevices), чтобы кэш комнаты не отставал от
                // регуляторов. Их вклад в равновесие — отдельным термом
                // (струя перемешивает комнату и не делится на объём), поэтому
                // в пассивное тепло они не входят.
                if (state.getBlock() instanceof ACIndoorBlock
                        && level.getBlockEntity(neighbor) instanceof ACIndoorBlockEntity) {
                    acPositions.add(neighbor.immutable());
                } else if ((state.getBlock() instanceof GrilleBlock
                        && level.getBlockEntity(neighbor) instanceof GrilleBlockEntity)
                        || isCeilingGrille(state)) {
                    // Потолочная решётка (vent_ceiling_grille) — тоже приточное
                    // устройство, хоть и без блока-сущности: терминал магистрали.
                    grillePositions.add(neighbor.immutable());
                } else {
                    heat += BodyTemperature.sourceStrength(level, neighbor, state);
                }
                // Теплопроводность ограждения: тонкая стенка из миниблоков
                // пропускает тепло пропорционально своей толщине.
                envelope += ThermalProperties.faceConductance(level, neighbor, direction);
            }
        }
        StringBuilder devices = new StringBuilder();
        boundaryBlocks.entrySet().stream()
                .sorted((a, b) -> {
                    boolean am = a.getKey().equals("minecraft"), bm = b.getKey().equals("minecraft");
                    if (am != bm) return am ? 1 : -1;   // ваниль — в конец списка
                    return Integer.compare(b.getValue(), a.getValue());
                })
                .forEach(e -> {
                    if (devices.length() > 0) devices.append(", ");
                    devices.append(e.getKey()).append("×").append(e.getValue());
                });
        return new RoomScan(room.size(), openings, skyOpenings, heat, envelope, overflow,
                room, massKeys, acPositions, grillePositions, devices.toString());
    }

    private record RoomScan(int volume, double openings, double skyOpenings, double heat, double envelope,
                            boolean overflow, Set<Long> room, Set<Long> massKeys,
                            List<BlockPos> acPositions, List<BlockPos> grillePositions,
                            String boundaryDevices) {}

    private record DeviceTerms(int acCount, double acEmissionSum,
                               int grilleCount, double grilleEmissionSum) {}

    private record MassKey(ResourceKey<Level> dimension, int x, int y, int z) {}

    private record CellKey(ResourceKey<Level> dimension, long packed) {}

    /** Закэшированный обход помещения: геометрия неизменна, инвалидация — флагом. */
    private static final class CachedRoom {
        final Set<Long> room;
        final Set<Long> massKeys;
        final double openings;
        final double skyOpenings;
        final double heat;
        final double envelope;
        final boolean overflow;
        final List<BlockPos> acPositions;
        final List<BlockPos> grillePositions;
        final String boundaryDevices;
        final long gameTime;
        final AtomicBoolean invalidated = new AtomicBoolean();

        CachedRoom(RoomScan scan, long gameTime) {
            this.room = scan.room;
            this.massKeys = scan.massKeys;
            this.openings = scan.openings;
            this.skyOpenings = scan.skyOpenings;
            this.heat = scan.heat;
            this.envelope = scan.envelope;
            this.overflow = scan.overflow;
            this.acPositions = scan.acPositions;
            this.grillePositions = scan.grillePositions;
            this.boundaryDevices = scan.boundaryDevices;
            this.gameTime = gameTime;
        }

        RoomScan toScan() {
            return new RoomScan(room.size(), openings, skyOpenings, heat, envelope, overflow,
                    room, massKeys, acPositions, grillePositions, boundaryDevices);
        }
    }

    private static final class MassCell {
        double temp;
        long gameTime;

        MassCell(double temp, long gameTime) {
            this.temp = temp;
            this.gameTime = gameTime;
        }
    }
}
