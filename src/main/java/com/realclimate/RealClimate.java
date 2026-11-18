package com.realclimate;

import com.realclimate.registry.ModBlocks;
import com.realclimate.registry.ModCreativeTab;
import com.realclimate.climate.ClimateConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;

@Mod(RealClimate.MODID)
public class RealClimate {
    public static final String MODID = "real_climate";

    public RealClimate() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModBlocks.ITEMS.register(bus);
        ModCreativeTab.TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ClimateConfig.SPEC);
    }
}
