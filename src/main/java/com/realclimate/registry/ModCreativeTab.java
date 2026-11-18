package com.realclimate.registry;

import com.realclimate.RealClimate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RealClimate.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.real_climate"))
                    .icon(() -> ModBlocks.ALL.get("radiator").get().asItem().getDefaultInstance())
                    .displayItems((params, out) -> ModBlocks.ALL.values().forEach(b -> out.accept(b.get())))
                    .build());

    private ModCreativeTab() {}
}
