package com.realclimate.climate;

/** Одномерный градиентный шум (Перлин). Детерминирован по seed, значения в [-1; 1]. */
public final class Perlin1D {
    private final long seed;
    public Perlin1D(long seed) { this.seed = seed; }

    private double grad(long i) {
        long h = i * 0x9E3779B97F4A7C15L + seed * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 33); h *= 0xFF51AFD7ED558CCDL; h ^= (h >>> 33);
        return ((h & 0xFFFFL) / 32767.5) - 1.0;
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    public double noise(double x) {
        long i = (long) Math.floor(x);
        double f = x - i;
        double a = grad(i) * f;
        double b = grad(i + 1) * (f - 1.0);
        return Math.max(-1.0, Math.min(1.0, 2.0 * (a + (b - a) * fade(f))));
    }
}
