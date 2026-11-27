package com.realclimate.climate;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClimateConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CLIMATE;
    public static final ForgeConfigSpec.DoubleValue BASE_TEMP;
    public static final ForgeConfigSpec.DoubleValue BASE_HUMIDITY;
    public static final ForgeConfigSpec.BooleanValue REAL_TIME;
    public static final ForgeConfigSpec.ConfigValue<String> TIME_ANCHOR;
    public static final ForgeConfigSpec.DoubleValue ORIGIN_LAT;
    public static final ForgeConfigSpec.DoubleValue ORIGIN_LON;
    public static final ForgeConfigSpec.BooleanValue SNOW_MELT;
    public static final ForgeConfigSpec.BooleanValue WATER_FREEZE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("climate");
        ENABLE_CLIMATE = builder.comment("Включить климатическую систему").define("enabled", true);
        BASE_TEMP = builder.comment("Базовая температура (°C)").defineInRange("base_temp", 20.0, -50.0, 60.0);
        BASE_HUMIDITY = builder.comment("Базовая влажность (%)").defineInRange("base_humidity", 50.0, 0.0, 100.0);
        builder.pop();
        builder.push("time");
        REAL_TIME = builder.comment("Синхронизировать время суток с реальным временем по долготе (15° = 1 час)").define("real_time", true);
        TIME_ANCHOR = builder.comment("Чья долгота задаёт мировое время: 'spawn' — долгота точки спавна, или число (долгота -180..180)")
                .define("time_anchor", "spawn");
        builder.pop();
        builder.push("geo");
        ORIGIN_LAT = builder.comment("Широта реальной точки, которой соответствует X=0, Z=0 мира (-90..90). Север — это -Z.")
                .defineInRange("origin_latitude", 0.0, -90.0, 90.0);
        ORIGIN_LON = builder.comment("Долгота реальной точки, которой соответствует X=0, Z=0 мира (-180..180). Восток — это +X.")
                .defineInRange("origin_longitude", 0.0, -180.0, 180.0);
        builder.pop();
        builder.push("precipitation");
        SNOW_MELT = builder.comment("Снег и лёд тают по температуре с учётом источников тепла (радиатор, печка, факел)").define("snow_melt", true);
        WATER_FREEZE = builder.comment("Вода замерзает в холоде и в помещении тоже (не только под открытым небом)").define("water_freeze", true);
        builder.pop();
        SPEC = builder.build();
        // Регистрация выполняется в RealClimate, чтобы избежать двойной регистрации
    }

    public static double scale() {
        return ClimateService.scale();
    }

    private ClimateConfig() {}
}
