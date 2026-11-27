package com.realclimate.climate;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class Forecast {
    public static final int HOURS = 6;
    public final LocalDateTime slotStart;
    private final double[] temps = new double[HOURS];

    /**
     * Прогноз по климатической модели: база — среднесуточная норма для широты
     * и текущей даты (ClimateModel.dailyMean), суточный ход — ClimateModel.diurnal
     * той же широты. Раньше база бралась из таблицы зон 0..3, а приходил
     * floor(широты) — любой реальный градус попадал в default, и прогноз
     * всегда был тропическим (+25...+30) даже в субарктике.
     */
    public Forecast(double lat) {
        this.slotStart = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        double base = ClimateModel.dailyMean(lat, LocalDate.now());
        for (int i = 0; i < HOURS; i++) {
            // Плавный суточный ход: максимум в 15:00, минимум в 03:00.
            temps[i] = base + ClimateModel.diurnal(lat, slotStart.getHour() + i);
        }
    }

    public double at(int hourIndex) {
        if (hourIndex < 0 || hourIndex >= HOURS) return temps[0];
        return temps[hourIndex];
    }

}
