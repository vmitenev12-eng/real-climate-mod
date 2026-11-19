package com.realclimate.climate;

import java.time.LocalDateTime;

public final class Forecast {
    public static final int HOURS = 6;
    public final LocalDateTime slotStart;
    private final double[] temps = new double[HOURS];

    public Forecast(double baseTemp) {
        this.slotStart = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        for (int i = 0; i < HOURS; i++) {
            // Плавное изменение температуры в течение дня
            double hour = slotStart.getHour() + i;
            double dailyVariation = Math.sin((hour - 6.0) / 24.0 * 2.0 * Math.PI) * 5.0;
            temps[i] = baseTemp + dailyVariation;
        }
    }

    public double at(int hourIndex) {
        if (hourIndex < 0 || hourIndex >= HOURS) return temps[0];
        return temps[hourIndex];
    }

}
