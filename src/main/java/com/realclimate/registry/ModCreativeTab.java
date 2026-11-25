package com.realclimate.registry;

import com.realclimate.RealClimate;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = RealClimate.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(ForgeRegistries.CREATIVE_MODE_TABS.key(), RealClimate.MODID);

    /** Отдельная вкладка креатива со всеми блоками и предметами мода. */
    public static final RegistryObject<CreativeModeTab> REAL_CLIMATE = TABS.register("real_climate_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModBlocks.AC_OUTDOOR.get()))
                    .title(Component.translatable("itemGroup.real_climate"))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.INSTALLATION_KEY.get());
                        output.accept(ModBlocks.HAMMER.get());
                        for (var item : ModBlocks.ITEMS.getEntries()) {
                            output.accept(item.get());
                        }
                    })
                    .build());

    private ModCreativeTab() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }

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
