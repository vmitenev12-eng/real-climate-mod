package com.realclimate.climate;

public enum ClimateZone {
    EQUATORIAL("Экваториальный"), TROPICAL("Тропический"), SUBTROPICAL("Субтропический"),
    TEMPERATE("Умеренный"), SUBARCTIC("Субарктический"), POLAR("Полярный");

    public final String title;
    ClimateZone(String title) { this.title = title; }

    public static ClimateZone of(double lat) {
        double a = Math.abs(lat);
        if (a < 10) return EQUATORIAL;
        if (a < 23.5) return TROPICAL;
        if (a < 35) return SUBTROPICAL;
        if (a < 55) return TEMPERATE;
        if (a < 66.5) return SUBARCTIC;
        return POLAR;
    }
}
