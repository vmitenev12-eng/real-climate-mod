package com.realclimate.registry;

import com.realclimate.RealClimate;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RealClimate.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModCreativeTab {
    private ModCreativeTab() {}

    @SubscribeEvent
    public static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SEARCH) {
            event.accept(ModItems.INSTALLATION_KEY.get());
            event.accept(ModBlocks.HAMMER.get());
            for (var item : ModBlocks.ITEMS.getEntries()) {
                event.accept(item.get());
            }
        }
    }
}
