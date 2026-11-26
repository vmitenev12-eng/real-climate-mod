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
import org.spongepowered.asm.mixin.Mixins;

@Mod(RealClimate.MODID)
public class RealClimate {
    public static final String MODID = "real_climate";
    /** Дублирует version из build.gradle — печатается в /climate body, чтобы по скриншоту
     * было видно, какая сборка реально стоит на сервере. При бампе версии менять вместе. */
    public static final String MOD_VERSION = "0.1.24";

    public RealClimate() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(bus);
        ModBlocks.ITEMS.register(bus);
        ModItems.ITEMS.register(bus);
        ModBlockEntities.BLOCK_ENTITIES.register(bus);
        ModCreativeTab.register(bus);

        ModMessages.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ClimateConfig.SPEC);

        // Миксины: тип осадков решает климат мода, а не шаблон биома.
        Mixins.addConfiguration("real_climate.mixins.json");

        // Клиентские события регистрируются через @Mod.EventBusSubscriber аннотации
        // KeyOverlayRenderer, BodyTemperature, ClimateEvents, ClimateService — самoregistrating
    }
}
