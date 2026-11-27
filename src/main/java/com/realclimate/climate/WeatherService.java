package com.realclimate.climate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Живая погода из интернета (Open-Meteo, ключ не нужен): реальная температура,
 * почасовой прогноз, настоящий часовой пояс и точные восход/закат для клетки
 * 0.5°×0.5° вокруг точки мира. Запросы идут в фоновом потоке и кэшируются на
 * 30 минут — тик сервера никогда не ждёт сеть. Пока данных нет или интернет
 * недоступен, все обращения возвращают null/нет, и вызывающие места молча
 * работают на автономной климатической модели. Отключается конфигом
 * online_weather (например, у владельца сервера нет выхода в сеть).
 */
public final class WeatherService {
    private static final long TTL_MS = 30L * 60_000L;
    private static final long RETRY_MS = 60_000L;
    private static final Map<Long, Entry> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "realclimate-weather");
        t.setDaemon(true);
        return t;
    });
    private static volatile HttpClient http;

    private WeatherService() {}

    private static final class Entry {
        final double lat, lon;
        volatile long fetchedAt = 0;
        volatile boolean loading = false;
        /** "yyyy-MM-dd'T'HH:00" местного времени точки -> температура, °C. */
        volatile Map<String, Double> byHour;
        volatile int utcOffsetSec = Integer.MIN_VALUE;
        volatile double sunriseHour = Double.NaN; // местный час сегодня
        volatile double sunsetHour = Double.NaN;
        Entry(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }

    private static boolean enabled() {
        try {
            return ClimateConfig.WEATHER_ONLINE.get();
        } catch (Throwable t) {
            return true;
        }
    }

    /** Запись кэша для клетки 0.5°; при протухании запускает фоновое обновление. */
    private static Entry entry(double lat, double lon) {
        double la = Math.round(lat * 2.0) / 2.0;
        double lo = Math.round(lon * 2.0) / 2.0;
        long key = (Math.round(la * 2.0) + 360L) * 2048L + (Math.round(lo * 2.0) + 720L);
        Entry e = CACHE.computeIfAbsent(key, k -> new Entry(la, lo));
        long now = System.currentTimeMillis();
        if (enabled() && !e.loading && now - e.fetchedAt > TTL_MS) {
            e.loading = true;
            e.fetchedAt = now; // при ошибке load() отмотает, чтобы повтор был через минуту
            EXEC.submit(() -> load(e));
        }
        return e;
    }

    private static void load(Entry e) {
        try {
            if (http == null) {
                http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            }
            String url = String.format(Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                            + "&hourly=temperature_2m&daily=sunrise,sunset&timezone=auto&forecast_days=2",
                    e.lat, e.lon);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).build();
            String body = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();

            Map<String, Double> byHour = new HashMap<>();
            JsonObject hourly = root.getAsJsonObject("hourly");
            JsonArray times = hourly.getAsJsonArray("time");
            JsonArray temps = hourly.getAsJsonArray("temperature_2m");
            for (int i = 0; i < times.size(); i++) {
                if (!temps.get(i).isJsonNull()) {
                    byHour.put(times.get(i).getAsString(), temps.get(i).getAsDouble());
                }
            }
            e.byHour = byHour;
            e.utcOffsetSec = root.get("utc_offset_seconds").getAsInt();
            JsonObject daily = root.getAsJsonObject("daily");
            e.sunriseHour = hm(daily.getAsJsonArray("sunrise").get(0).getAsString());
            e.sunsetHour = hm(daily.getAsJsonArray("sunset").get(0).getAsString());
            e.fetchedAt = System.currentTimeMillis();
        } catch (Throwable t) {
            // Офлайн или API недоступен: тихо остаёмся на модели, повтор через минуту.
            e.fetchedAt = System.currentTimeMillis() - TTL_MS + RETRY_MS;
        } finally {
            e.loading = false;
        }
    }

    /** "2026-11-27T09:19" -> 9.317 часа. */
    private static double hm(String iso) {
        return Integer.parseInt(iso.substring(11, 13))
                + Integer.parseInt(iso.substring(14, 16)) / 60.0;
    }

    /** Ключ часа "yyyy-MM-dd'T'HH:00" для местного времени точки. */
    private static String hourKey(int utcOffsetSec, long epochMs) {
        long sec = (epochMs / 1000L) + utcOffsetSec;
        long days = Math.floorDiv(sec, 86_400L);
        int secOfDay = (int) Math.floorMod(sec, 86_400L);
        java.time.LocalDate d = java.time.LocalDate.ofEpochDay(days);
        return String.format(Locale.ROOT, "%04d-%02d-%02dT%02d:00",
                d.getYear(), d.getMonthValue(), d.getDayOfMonth(), secOfDay / 3600);
    }

    /** Реальная температура на улице прямо сейчас, °C; null — данных нет. */
    public static Double temperatureNow(double lat, double lon) {
        Entry e = entry(lat, lon);
        Map<String, Double> byHour = e.byHour;
        if (byHour == null || e.utcOffsetSec == Integer.MIN_VALUE) return null;
        return byHour.get(hourKey(e.utcOffsetSec, System.currentTimeMillis()));
    }

    /** Почасовой прогноз на 6 часов от текущего местного часа; null — данных нет. */
    public static double[] forecast6(double lat, double lon) {
        Entry e = entry(lat, lon);
        Map<String, Double> byHour = e.byHour;
        if (byHour == null || e.utcOffsetSec == Integer.MIN_VALUE) return null;
        long now = System.currentTimeMillis();
        double[] out = new double[6];
        for (int i = 0; i < 6; i++) {
            Double t = byHour.get(hourKey(e.utcOffsetSec, now + i * 3_600_000L));
            if (t == null) return null;
            out[i] = t;
        }
        return out;
    }

    /** Настоящий часовой пояс точки, секунд от UTC; null — неизвестен (офлайн). */
    public static Integer utcOffsetSeconds(double lat, double lon) {
        Entry e = entry(lat, lon);
        return e.utcOffsetSec == Integer.MIN_VALUE ? null : e.utcOffsetSec;
    }

    /** Точные восход и закат сегодня по местному времени {час, час}; null — нет данных. */
    public static double[] sunriseSunset(double lat, double lon) {
        Entry e = entry(lat, lon);
        if (Double.isNaN(e.sunriseHour)) return null;
        return new double[]{e.sunriseHour, e.sunsetHour};
    }

    /** Есть ли свежие данные из сети для точки (пометка в /climate). */
    public static boolean online(double lat, double lon) {
        return entry(lat, lon).byHour != null;
    }

    public static void clear() {
        CACHE.clear();
    }
}
