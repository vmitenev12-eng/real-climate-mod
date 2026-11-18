package com.realclimate.climate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Асинхронное «второе ядро»: один фоновый поток, спит до события (смена окна 00/06/12/18
 * или заход игрока в новую полосу широты). Главный поток только читает готовые снимки.
 */
public final class ClimateService {
    private static volatile ScheduledExecutorService exec;
    private static volatile long seed;
    private static final Map<Integer, Forecast> CACHE = new ConcurrentHashMap<>();
    private static final Set<Integer> PENDING = ConcurrentHashMap.newKeySet();

    private ClimateService() {}

    public static synchronized void start(long worldSeed) {
        stop();
        seed = worldSeed;
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RealClimate-Async");
            t.setDaemon(true);
            return t;
        });
        scheduleNextSlot();
    }

    public static synchronized void stop() {
        if (exec != null) exec.shutdownNow();
        exec = null;
        CACHE.clear();
        PENDING.clear();
    }

    private static void scheduleNextSlot() {
        ScheduledExecutorService e = exec;
        if (e == null) return;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = Forecast.slotOf(now).plusHours(Forecast.HOURS);
        long delay = Math.max(1000, Duration.between(now, next).toMillis() + 500);
        e.schedule(() -> {
            LocalDateTime n = LocalDateTime.now();
            for (Integer band : CACHE.keySet()) CACHE.put(band, Forecast.generate(seed, band, Forecast.slotOf(n)));
            scheduleNextSlot();
        }, delay, TimeUnit.MILLISECONDS);
    }

    /** Дёшево вызывать из главного потока: если прогноза нет или он устарел, считает в фоне. */
    public static void request(int band) {
        ScheduledExecutorService e = exec;
        if (e == null) return;
        Forecast f = CACHE.get(band);
        if (f != null && f.covers(LocalDateTime.now())) return;
        if (!PENDING.add(band)) return;
        try {
            e.execute(() -> {
                try { CACHE.put(band, Forecast.generate(seed, band, Forecast.slotOf(LocalDateTime.now()))); }
                finally { PENDING.remove(band); }
            });
        } catch (RejectedExecutionException ex) { PENDING.remove(band); }
    }

    /** Готовый прогноз или null (тогда запрос уже отправлен в фон). */
    public static Forecast forecast(int band) {
        Forecast f = CACHE.get(band);
        if (f == null || !f.covers(LocalDateTime.now())) request(band);
        return f;
    }

    /** Температура воздуха в точке, °C. Пока прогноз считается, возвращает климатическую норму (мгновенная формула). */
    public static double temperature(double x, double z, double metersPerBlock) {
        double lat = Geo.latitude(z, metersPerBlock);
        Forecast f = forecast(Geo.band(lat));
        LocalDateTime now = LocalDateTime.now();
        if (f != null) return f.current(now);
        return ClimateModel.dailyMean(lat, now.toLocalDate()) + ClimateModel.diurnal(lat, now.getHour());
    }
}
