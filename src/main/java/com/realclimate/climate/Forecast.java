package com.realclimate.climate;

import java.time.LocalDateTime;

/** Неизменяемый прогноз на 6 часов для полосы широты. Температура меняется раз в час. */
public final class Forecast {
    public static final int HOURS = 6;
    public final int band;
    public final LocalDateTime slotStart;
    private final float[] temps;

    private Forecast(int band, LocalDateTime slotStart, float[] temps) {
        this.band = band; this.slotStart = slotStart; this.temps = temps;
    }

    /** Начало 6-часового окна (00, 06, 12, 18). */
    public static LocalDateTime slotOf(LocalDateTime t) {
        return t.withHour(t.getHour() / HOURS * HOURS).withMinute(0).withSecond(0).withNano(0);
    }

    public static Forecast generate(long worldSeed, int band, LocalDateTime slotStart) {
        double lat = band + 0.5;
        Perlin1D slow = new Perlin1D(worldSeed ^ (band * 0x9E3779B1L));
        Perlin1D fast = new Perlin1D(~worldSeed + band);
        double amp = 2.0 + 2.5 * Math.abs(Math.sin(Math.toRadians(lat)));
        float[] out = new float[HOURS];
        for (int i = 0; i < HOURS; i++) {
            LocalDateTime t = slotStart.plusHours(i);
            double hoursAbs = t.toLocalDate().toEpochDay() * 24.0 + t.getHour();
            double weather = slow.noise(hoursAbs / 20.0) * amp + fast.noise(hoursAbs / 3.0) * 0.8;
            out[i] = (float) (ClimateModel.dailyMean(lat, t.toLocalDate())
                    + ClimateModel.diurnal(lat, t.getHour()) + weather);
        }
        return new Forecast(band, slotStart, out);
    }

    public boolean covers(LocalDateTime now) { return slotOf(now).equals(slotStart); }

    public float at(int hourIndex) { return temps[Math.max(0, Math.min(HOURS - 1, hourIndex))]; }

    public float current(LocalDateTime now) { return at(now.getHour() - slotStart.getHour()); }
}
