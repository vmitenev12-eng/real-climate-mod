package com.realclimate.climate;

import com.realclimate.RealClimate;
import net.minecraft.core.BlockPos;
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
 */
@Mod.EventBusSubscriber(modid = RealClimate.MODID)
public final class SnowMeltService {
    private static final int RADIUS = 24;
    private static final int SAMPLES = 64;
    private static final int SOURCE_RADIUS = 4;

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
        return temp + Math.max(-25.0, Math.min(25.0, local));
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
                float chance = (float) Math.min(1.0, (temp - 0.5) / 12.0);
                if (random.nextFloat() >= chance) continue;
                int layers = state.getValue(SnowLayerBlock.LAYERS);
                BlockState next = layers <= 1
                        ? Blocks.AIR.defaultBlockState()
                        : state.setValue(SnowLayerBlock.LAYERS, layers - 1);
                level.setBlock(pos, next, 3);
            } else if (state.is(Blocks.SNOW_BLOCK)) {
                // Плотный снежный блок тает долго и только в устойчивый плюс.
                if (temp <= 6.0) continue;
                float chance = (float) Math.min(0.25, (temp - 6.0) / 120.0);
                if (random.nextFloat() >= chance) continue;
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            } else if (state.is(Blocks.ICE)) {
                if (temp <= 3.0) continue;
                float chance = (float) Math.min(0.5, (temp - 3.0) / 20.0);
                if (random.nextFloat() >= chance) continue;
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
            } else if (state.is(Blocks.WATER) && state.getValue(LiquidBlock.LEVEL) == 0) {
                // Замерзание воды — и под открытым небом, и в помещении
                // (например, бассейн в охлаждаемой комнате при уличном плюсе).
                if (!ClimateConfig.WATER_FREEZE.get()) continue;
                if (temp >= -0.5) continue;
                float chance = (float) Math.min(1.0, (-0.5 - temp) / 12.0);
                if (random.nextFloat() >= chance) continue;
                level.setBlock(pos, Blocks.ICE.defaultBlockState(), 3);
            }
        }
    }
}
