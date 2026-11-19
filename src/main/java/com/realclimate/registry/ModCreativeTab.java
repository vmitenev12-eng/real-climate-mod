package com.realclimate.registry;

import com.realclimate.RealClimate;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RealClimate.MODID);

    public static final RegistryObject<CreativeModeTab> REAL_CLIMATE_TAB = TABS.register("real_climate",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.INSTALLATION_KEY.get())).build());

    private ModCreativeTab() {}
}
