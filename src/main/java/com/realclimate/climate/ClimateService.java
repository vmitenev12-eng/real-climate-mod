package com.realclimate.climate;

import com.realclimate.RealClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = RealClimate.MODID, value = Dist.DEDICATED_SERVER)
public final class ClimateService {
    private static final Map<UUID, Double> bodyCache = new ConcurrentHashMap<>();
    private static double globalScale = 1.0;
    private static boolean started = false;

    private ClimateService() {}

    public static void start(long seed) {
        globalScale = 1.0 + (seed % 1000) / 10000.0;
        started = true;
        FMLJavaModLoadingContext.get().getModEventBus().register(new ClimateTicker());
    }

    public static void stop() {
        started = false;
        bodyCache.clear();
        FMLJavaModLoadingContext.get().getModEventBus().unregister(new ClimateTicker());
    }

    public static double scale() {
        return globalScale;
    }

    public static double temperature(double x, double z, double height) {
        return ClimateConfig.BASE_TEMP.get() + Math.sin(x / 500.0) * 5.0 + Math.cos(z / 500.0) * 3.0;
    }

    public static double humidity(double x, double z) {
        return Math.max(0, Math.min(100, ClimateConfig.BASE_HUMIDITY.get() + Math.sin(x / 300.0) * 15.0));
    }

    public static double getTemperature(Level level, BlockPos pos) {
        return temperature(pos.getX(), pos.getZ(), pos.getY());
    }

    public static double getHumidity(Level level, BlockPos pos) {
        return humidity(pos.getX(), pos.getZ());
    }

    public static Forecast forecast(int band) {
        double base = switch (band) {
            case 0 -> -10.0; // Арктика
            case 1 -> 0.0;   // Субарктика
            case 2 -> 10.0;  // Умеренный
            case 3 -> 20.0;  // Субтропики
            default -> 25.0; // Тропики
        };
        return new Forecast(base);
    }

    public static double getBodyTemp(UUID uuid) {
        return bodyCache.getOrDefault(uuid, BodyTemperature.NORMAL);
    }

    public static void setBodyTemp(UUID uuid, double temp) {
        bodyCache.put(uuid, temp);
    }

    private static final class ClimateTicker {
        @SubscribeEvent
        public void onTick(TickEvent.LevelTickEvent e) {
            if (!started || e.phase != TickEvent.Phase.END) return;
        }
    }
}
