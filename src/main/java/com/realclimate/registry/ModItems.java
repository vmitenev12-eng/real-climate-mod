package com.realclimate.registry;

import com.realclimate.RealClimate;
import com.realclimate.item.InstallationKeyItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RealClimate.MODID);

    public static final RegistryObject<Item> INSTALLATION_KEY = ITEMS.register("installation_key",
            () -> new InstallationKeyItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
