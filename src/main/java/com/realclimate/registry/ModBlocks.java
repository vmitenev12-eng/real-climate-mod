package com.realclimate.registry;

import com.realclimate.RealClimate;
import com.realclimate.block.FacingBlock;
import net.minecraft.world.item.BlockItem;
import com.realclimate.item.HammerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RealClimate.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RealClimate.MODID);
    /** Все блоки мода в порядке регистрации (для вкладки креатива). */
    public static final Map<String, RegistryObject<Block>> ALL = new LinkedHashMap<>();

    public static final RegistryObject<Item> HAMMER = ITEMS.register("hammer", () -> new HammerItem(new Item.Properties().stacksTo(1)));

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops();
    }

    private static RegistryObject<Block> reg(String id) {
        RegistryObject<Block> b = BLOCKS.register(id, () -> new FacingBlock(metal()));
        ITEMS.register(id, () -> new BlockItem(b.get(), new Item.Properties()));
        ALL.put(id, b);
        return b;
    }

    static {
        // Вентиляция (текстура 1000388937 -> vent_metal)
        reg("vent_straight"); reg("vent_straight_up");
        reg("vent_elbow_side"); reg("vent_elbow_up"); reg("vent_elbow_down");
        reg("vent_tee_side"); reg("vent_tee_up"); reg("vent_tee_down");
        reg("vent_cross_side"); reg("vent_cross_up");
        reg("vent_ceiling_grille");
        // Оборудование
        reg("ac_indoor"); reg("ac_outdoor"); reg("radiator");
    }

    private ModBlocks() {}
}
