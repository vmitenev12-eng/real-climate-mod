package com.realclimate.climate;

import com.realclimate.RealClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.time.LocalDate;
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
    }

    public static void stop() {
        started = false;
        bodyCache.clear();
    }

    public static double scale() {
        return globalScale;
    }

    public static double temperature(double x, double z, double height) {
        double metersPerBlock = Math.max(1.0, ClimateConfig.scale());
        double lat = Geo.latitude(z, metersPerBlock);
        double lon = Geo.longitude(x, metersPerBlock);
        int hour = (int) ((System.currentTimeMillis() / 3_600_000L) % 24);
        double temp = ClimateModel.dailyMean(lat, LocalDate.now())
                + ClimateModel.diurnal(lat, hour);
        temp -= Math.max(0.0, height - 64.0) * 0.0065;
        temp += Math.sin(Math.toRadians(lon * 0.37)) * 1.2;
        return temp;
    }

    public static double humidity(double x, double z) {
        return Math.max(0, Math.min(100, ClimateConfig.BASE_HUMIDITY.get() + Math.sin(x / 300.0) * 15.0));
    }

    public static double getTemperature(Level level, BlockPos pos) {
        // Единая температура мира: temperature() уже включает суточный ход
        // по реальному времени. Раньше здесь diurnal добавлялся второй раз
        // с часом из игрового времени (со сдвигом на 6 часов) — отсюда
        // двойные амплитуды и скачки от блока к блоку.
        double temp = temperature(pos.getX(), pos.getZ(), pos.getY());
        if (level.isRaining()) temp -= level.isThundering() ? 5.0 : 3.0;
        if (level.isThundering()) temp -= 1.5;
        return temp;
    }

    public static double getHumidity(Level level, BlockPos pos) {
        return humidity(pos.getX(), pos.getZ());
    }

    /** Прогноз на 6 часов вперёд для широты точки: напрямую из климатической
     * модели (сезонная норма на сегодня + суточный ход), без грубой таблицы зон. */
    public static Forecast forecast(double lat) {
        return new Forecast(lat);
    }

    public static double getBodyTemp(UUID uuid) {
        return bodyCache.getOrDefault(uuid, BodyTemperature.NORMAL);
    }

    public static void setBodyTemp(UUID uuid, double temp) {
        bodyCache.put(uuid, temp);
    }

}
