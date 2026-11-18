package com.realclimate.climate;

/** Перевод координат Minecraft в широту/долготу. X=0,Z=0 — экватор. Север — это -Z. */
public final class Geo {
    public static final double METERS_PER_DEGREE = 111_320.0;
    private Geo() {}

    public static double latitude(double z, double metersPerBlock) {
        double lat = -z * metersPerBlock / METERS_PER_DEGREE;
        return Math.max(-90.0, Math.min(90.0, lat));
    }

    public static double longitude(double x, double metersPerBlock) {
        double lon = x * metersPerBlock / METERS_PER_DEGREE;
        lon = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        return lon;
    }

    /** Целочисленная полоса широты (кэш прогноза считается на полосу в 1°). */
    public static int band(double lat) { return (int) Math.floor(lat); }
}
