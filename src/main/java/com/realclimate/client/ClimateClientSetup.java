package com.realclimate.client;

import com.realclimate.RealClimate;
import com.realclimate.registry.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RealClimate.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClimateClientSetup {
    private ClimateClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CONTROL_PANEL.get(), ControlPanelRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.THERMOMETER.get(), ThermometerRenderer::new);
    }
}
