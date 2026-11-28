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

    /** Высота (Y), выше которой начинается снижение температуры с высотой (горы). */
    public static final double LAPSE_START_Y = 100.0;
    /** Падение температуры выше LAPSE_START_Y, °C на блок (реальный градиент 6,5 °C на километр). */
    public static final double LAPSE_PER_BLOCK = 0.0065;

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
        Double real = WeatherService.temperatureNow(lat, lon);
        double temp;
        if (real != null) {
            // Живая погода из сети: реальное значение на улице прямо сейчас.
            temp = real;
        } else {
            // Офлайн: климатическая модель. Суточный ход — по местному солнечному
            // времени долготы (был «сырой» UTC-час: дневной пик в Архангельске
            // приходился на 18:00 вместо 15:00 по местному).
            int hour = (int) RealTimeService.localHours(x);
            temp = ClimateModel.dailyMean(lat, LocalDate.now())
                    + ClimateModel.diurnal(lat, hour);
            temp += Math.sin(Math.toRadians(lon * 0.37)) * 1.2;
        }
        // Базовая температура — живая погода (или формула широты); охлаждение от высоты начинается
        // только выше Y=100 и идёт плавно (6,5 °C на 1000 блоков подъёма).
        temp -= Math.max(0.0, height - LAPSE_START_Y) * LAPSE_PER_BLOCK;
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
        // При живой погоде осадки уже учтены в реальном значении — не вычитаем дважды.
        double metersPerBlock = Math.max(1.0, ClimateConfig.scale());
        if (!WeatherService.online(Geo.latitude(pos.getZ(), metersPerBlock),
                Geo.longitude(pos.getX(), metersPerBlock))) {
            if (level.isRaining()) temp -= level.isThundering() ? 5.0 : 3.0;
            if (level.isThundering()) temp -= 1.5;
        }
        return temp;
    }

    public static double getHumidity(Level level, BlockPos pos) {
        return humidity(pos.getX(), pos.getZ());
    }

    /** Прогноз на 6 часов вперёд для точки: живые данные open-meteo, а без
     * сети — климатическая модель (сезонная норма + суточный ход). */
    public static Forecast forecast(double lat, double lon) {
        return new Forecast(lat, lon);
    }

    public static double getBodyTemp(UUID uuid) {
        return bodyCache.getOrDefault(uuid, BodyTemperature.NORMAL);
    }

    public static void setBodyTemp(UUID uuid, double temp) {
        bodyCache.put(uuid, temp);
    }

}
