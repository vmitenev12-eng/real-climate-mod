package com.realclimate.climate;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClimateConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue METERS_PER_BLOCK;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        METERS_PER_BLOCK = b.comment("Сколько реальных метров в одном блоке. 1000 = полюс на расстоянии 10 000 блоков от экватора (X:0,Z:0).")
                .defineInRange("metersPerBlock", 1000.0, 1.0, 100000.0);
        SPEC = b.build();
    }

    public static double scale() {
        try { return METERS_PER_BLOCK.get(); } catch (Throwable t) { return 1000.0; }
    }

    private ClimateConfig() {}
}
