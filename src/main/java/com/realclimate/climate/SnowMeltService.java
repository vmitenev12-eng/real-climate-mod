package com.realclimate.climate;

import com.realclimate.RealClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Таяние и замерзание по ФАКТИЧЕСКОЙ температуре точки: климат мира плюс
 * локальные источники — радиатор, печка и факел греют, лёд, снег и включённый
 * на холод кондиционер морозят. Поэтому снег тает рядом с работающим
 * радиатором даже в уличный мороз, а вода в охлаждаемом помещении замерзает,
 * хотя на улице плюс. Ванильная механика морозит только открытую небу
 * поверхность — здесь проверяются случайные точки вокруг игроков независимо
 * от неба. Нагрузка минимальная.
 *
 * Скорость зависит от температуры: у включённого на максимум радиатора снег
 * сходит по несколько слоёв за проход и таяние расползается «лужицей» на
 * соседние блоки, а вдали — едва подтаивает. Вода на сильном морозе
 * замерзает от берегов к середине.
 */
@Mod.EventBusSubscriber(modid = RealClimate.MODID)
public final class SnowMeltService {
    private static final int RADIUS = 28;
    private static final int SAMPLES = 80;
    private static final int SOURCE_RADIUS = 6;

    private SnowMeltService() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = RealTimeService.server();
        if (server == null || server.getTickCount() % 40 != 0) return;
        if (!ClimateConfig.SNOW_MELT.get()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickAround(player);
        }
    }

    /** Климатическая температура точки плюс вклад ближних источников тепла/холода. */
    private static double effectiveTemp(ServerLevel level, BlockPos pos) {
        double temp = ClimateService.getTemperature(level, pos);
        double local = 0.0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SOURCE_RADIUS; dx <= SOURCE_RADIUS; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -SOURCE_RADIUS; dz <= SOURCE_RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    double strength = BodyTemperature.sourceStrength(level, cursor, level.getBlockState(cursor));
                    if (strength == 0.0) continue;
                    int d = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    local += strength * (1.0 - d / (double) (SOURCE_RADIUS + 1));
                }
            }
        }
        return temp + Math.max(-40.0, Math.min(40.0, local));
    }

    private static void tickAround(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        BlockPos center = player.blockPosition();
        for (int i = 0; i < SAMPLES; i++) {
            BlockPos pos = center.offset(
                    random.nextInt(RADIUS * 2 + 1) - RADIUS,
                    random.nextInt(17) - 8,
                    random.nextInt(RADIUS * 2 + 1) - RADIUS);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            double temp = effectiveTemp(level, pos);

            if (state.is(Blocks.SNOW)) {
                if (temp <= 0.5) continue;
                // Скорость растёт с температурой: едва выше нуля — редкое
                // подтаивание, рядом с горячим радиатором — почти каждый проход
                // и сразу по несколько слоёв.
                float chance = (float) Math.min(1.0, (temp - 0.5) / 8.0);
                if (random.nextFloat() >= chance) continue;
                meltSnowLayers(level, pos, state, 1 + (temp > 12.0 ? 1 : 0) + (temp > 24.0 ? 1 : 0));
                // Тепло расползается лужицей: соседний снег подтаивает следом,
                // поэтому вокруг радиатора видна именно «проплешина», а не
                // равномерное редение всего сугроба.
                if (temp > 8.0) {
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos n = pos.relative(dir);
                        BlockState ns = level.getBlockState(n);
                        if (ns.is(Blocks.SNOW) && random.nextFloat() < chance * 0.7f) {
                            meltSnowLayers(level, n, ns, 1);
                        }
                    }
                }
            } else if (state.is(Blocks.SNOW_BLOCK)) {
                // Плотный снежный блок тает долго: сначала оседает в слои,
                // потом слои сходят послойно — виден прогресс.
                if (temp <= 4.0) continue;
                float chance = (float) Math.min(0.5, (temp - 4.0) / 48.0);
                if (random.nextFloat() >= chance) continue;
                level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 7), 3);
            } else if (state.is(Blocks.ICE)) {
                if (temp <= 2.0) continue;
                float chance = (float) Math.min(0.75, (temp - 2.0) / 16.0);
                if (random.nextFloat() >= chance) continue;
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
            } else if (state.is(Blocks.WATER) && state.getValue(LiquidBlock.LEVEL) == 0) {
                // Замерзание воды — и под открытым небом, и в помещении
                // (например, бассейн в охлаждаемой комнате при уличном плюсе).
                if (!ClimateConfig.WATER_FREEZE.get()) continue;
                if (temp >= -0.5) continue;
                float chance = (float) Math.min(1.0, (-0.5 - temp) / 8.0);
                if (random.nextFloat() >= chance) continue;
                level.setBlock(pos, Blocks.ICE.defaultBlockState(), 3);
                // На сильном морозе лёд нарастает от краёв к середине.
                if (temp < -4.0) {
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos n = pos.relative(dir);
                        BlockState ns = level.getBlockState(n);
                        if (ns.is(Blocks.WATER) && ns.getValue(LiquidBlock.LEVEL) == 0
                                && random.nextFloat() < 0.5f) {
                            level.setBlock(n, Blocks.ICE.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    private static void meltSnowLayers(ServerLevel level, BlockPos pos, BlockState state, int layersToRemove) {
        int layers = state.getValue(SnowLayerBlock.LAYERS);
        int remain = layers - layersToRemove;
        level.setBlock(pos, remain <= 0
                ? Blocks.AIR.defaultBlockState()
                : state.setValue(SnowLayerBlock.LAYERS, remain), 3);
    }
}
