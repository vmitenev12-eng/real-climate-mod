package com.realclimate;

import com.realclimate.block.*;
import com.realclimate.client.*;
import com.realclimate.climate.*;
import com.realclimate.item.*;
import com.realclimate.network.*;
import com.realclimate.registry.*;
import com.realclimate.tileentity.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.common.MinecraftForge;

@Mod(RealClimate.MODID)
public class RealClimate {
    public static final String MODID = "real_climate";

    public RealClimate() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(bus);
        ModBlocks.ITEMS.register(bus);
        ModItems.ITEMS.register(bus);
        ModBlockEntities.BLOCK_ENTITIES.register(bus);
        ModCreativeTab.TABS.register(bus);

        ModMessages.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ClimateConfig.SPEC);

        // Клиентские события (HUD, линии ключа)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(KeyOverlayRenderer.class);
        }
    }
}
