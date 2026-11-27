package com.realclimate.climate;

/**
 * Перевод координат Minecraft в широту/долготу. Точке X=0, Z=0 соответствует реальная точка из конфига
 * (geo.origin_latitude, geo.origin_longitude), по умолчанию 0°, 0°. Север — это -Z, восток — это +X.
 */
public final class Geo {
    public static final double METERS_PER_DEGREE = 111_320.0;
    private Geo() {}

    public static double originLatitude() {
        try {
            return ClimateConfig.ORIGIN_LAT.get();
        } catch (Throwable t) {
            return 0.0;
        }
    }

    public static double originLongitude() {
        try {
            return ClimateConfig.ORIGIN_LON.get();
        } catch (Throwable t) {
            return 0.0;
        }
    }

    public static double latitude(double z, double metersPerBlock) {
        double lat = originLatitude() - z * metersPerBlock / METERS_PER_DEGREE;
        return Math.max(-90.0, Math.min(90.0, lat));
    }

    public static double longitude(double x, double metersPerBlock) {
        double lon = originLongitude() + x * metersPerBlock / METERS_PER_DEGREE;
        lon = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        return lon;
    }

    /** Мировая координата X для заданной долготы (обратное преобразование к longitude). */
    public static double xForLongitude(double lon, double metersPerBlock) {
        return (lon - originLongitude()) * METERS_PER_DEGREE / metersPerBlock;
    }

    /** Целочисленная полоса широты (кэш прогноза считается на полосу в 1°). */
    public static int band(double lat) { return (int) Math.floor(lat); }
}
