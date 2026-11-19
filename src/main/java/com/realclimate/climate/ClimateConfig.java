package com.realclimate.climate;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class ClimateConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CLIMATE;
    public static final ForgeConfigSpec.DoubleValue BASE_TEMP;
    public static final ForgeConfigSpec.DoubleValue BASE_HUMIDITY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("climate");
        ENABLE_CLIMATE = builder.comment("Включить климатическую систему").define("enabled", true);
        BASE_TEMP = builder.comment("Базовая температура (°C)").defineInRange("base_temp", 20.0, -50.0, 60.0);
        BASE_HUMIDITY = builder.comment("Базовая влажность (%)").defineInRange("base_humidity", 50.0, 0.0, 100.0);
        builder.pop();
        SPEC = builder.build();
        // Регистрация выполняется в RealClimate, чтобы избежать двойной регистрации
    }

    private ClimateConfig() {}
}
