package com.realclimate.climate;

import com.realclimate.RealClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Снег и лёд тают по нашей температуре: выше ~0.5 °C снег начинает таять,
 * скорость растёт с температурой. Лёд требует теплее, тает медленнее,
 * снежный блок — ещё медленнее. Проверяются случайные точки вокруг игроков,
 * нагрузка минимальная.
 */
@Mod.EventBusSubscriber(modid = RealClimate.MODID)
public final class SnowMeltService {
    private static final int RADIUS = 24;
    private static final int SAMPLES = 64;

    private SnowMeltService() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = RealTimeService.server();
        if (server == null || server.getTickCount() % 40 != 0) return;
        if (!ClimateConfig.SNOW_MELT.get()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            meltAround(player);
        }
    }

    private static void meltAround(ServerPlayer player) {
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
            double temp = ClimateService.getTemperature(level, pos);

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
            }
        }
    }
}
