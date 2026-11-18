package com.realclimate.climate;

import java.time.LocalDate;

/** Климатическая норма по широте и дню года (аналитическая замена таблице исторических температур). */
public final class ClimateModel {
    private ClimateModel() {}

    /** Среднегодовая температура, °C: ~+27 на экваторе, ~-18 на полюсе. */
    public static double annualMean(double lat) {
        double s = Math.sin(Math.toRadians(lat));
        return 27.0 - 45.0 * s * s;
    }

    /** Амплитуда сезонных колебаний, °C. */
    public static double seasonalAmplitude(double lat) { return 0.22 * Math.abs(lat); }

    /** Суточная амплитуда, °C. */
    public static double diurnalAmplitude(double lat) { return 3.0 + 3.0 * Math.cos(Math.toRadians(lat)); }

    /** Средняя суточная температура на дату: пик лета ~19 июля на севере, зеркально на юге. */
    public static double dailyMean(double lat, LocalDate date) {
        double phase = Math.cos(2 * Math.PI * (date.getDayOfYear() - 200) / 365.0);
        return annualMean(lat) + seasonalAmplitude(lat) * phase * Math.signum(lat);
    }

    /** Суточный ход: максимум в 15:00, минимум в 03:00. */
    public static double diurnal(double lat, int hour) {
        return diurnalAmplitude(lat) * Math.cos(2 * Math.PI * (hour - 15) / 24.0);
    }
}
