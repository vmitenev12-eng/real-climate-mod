package com.realclimate.climate;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class Forecast {
    public static final int HOURS = 6;
    public final LocalDateTime slotStart;
    private final double[] temps = new double[HOURS];

    /**
     * Прогноз на 6 часов для точки: если есть сеть — живые почасовые данные
     * open-meteo для этих координат (WeatherService); без сети — климатическая
     * модель (сезонная норма широты на сегодня + суточный ход). Часовые слоты
     * — по местному времени точки, а не по часам сервера. Раньше база бралась
     * из таблицы зон 0..3, а приходил floor(широты) — любой реальный градус
     * попадал в default, и прогноз всегда был тропическим (+25...+30).
     */
    public Forecast(double lat, double lon) {
        int civilHour = (int) SolarService.civilHours(lat, lon);
        this.slotStart = LocalDate.now().atStartOfDay().plusHours(civilHour);
        double[] real = WeatherService.forecast6(lat, lon);
        if (real != null) {
            for (int i = 0; i < HOURS; i++) temps[i] = real[i];
        } else {
            double base = ClimateModel.dailyMean(lat, LocalDate.now());
            for (int i = 0; i < HOURS; i++) {
                // Плавный суточный ход: максимум в 15:00, минимум в 03:00.
                temps[i] = base + ClimateModel.diurnal(lat, civilHour + i);
            }
        }
    }

    public double at(int hourIndex) {
        if (hourIndex < 0 || hourIndex >= HOURS) return temps[0];
        return temps[hourIndex];
    }
}
