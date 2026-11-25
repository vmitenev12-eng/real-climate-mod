package com.realclimate.registry;

import com.realclimate.RealClimate;
import com.realclimate.block.*;
import com.realclimate.item.HammerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RealClimate.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RealClimate.MODID);
    public static final Map<String, RegistryObject<Block>> ALL = new LinkedHashMap<>();

    public static final RegistryObject<Item> HAMMER = ITEMS.register("hammer", () -> new HammerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Block> AC_OUTDOOR;
    public static final RegistryObject<Block> AC_INDOOR;
    public static final RegistryObject<Block> RADIATOR;
    public static final RegistryObject<Block> VENT_BLOCK;
    public static final RegistryObject<Block> GRILLE_BLOCK;
    public static final RegistryObject<Block> CONTROL_PANEL;
    public static final RegistryObject<Block> THERMOMETER;

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops();
    }

    private static <T extends Block> RegistryObject<T> reg(String id, Supplier<T> block, boolean registerItem) {
        RegistryObject<T> b = BLOCKS.register(id, block);
        if (registerItem) {
            ITEMS.register(id, () -> new BlockItem(b.get(), new Item.Properties()));
        }
        ALL.put(id, (RegistryObject<Block>) (RegistryObject<?>) b);
        return b;
    }

    static {
        // Вентиляция (FacingBlock без TE)
        reg("vent_straight", () -> new FacingBlock(metal()), true);
        reg("vent_straight_up", () -> new FacingBlock(metal()), true);
        reg("vent_elbow_side", () -> new FacingBlock(metal()), true);
        reg("vent_elbow_up", () -> new FacingBlock(metal()), true);
        reg("vent_elbow_down", () -> new FacingBlock(metal()), true);
        reg("vent_tee_side", () -> new FacingBlock(metal()), true);
        reg("vent_tee_up", () -> new FacingBlock(metal()), true);
        reg("vent_tee_down", () -> new FacingBlock(metal()), true);
        reg("vent_cross_side", () -> new FacingBlock(metal()), true);
        reg("vent_cross_up", () -> new FacingBlock(metal()), true);
        reg("vent_ceiling_grille", () -> new FacingBlock(metal()), true);

        // Оборудование (с TE)
        AC_OUTDOOR = reg("ac_outdoor", () -> new ACOutdoorBlock(metal()), true);
        AC_INDOOR = reg("ac_indoor", () -> new ACIndoorBlock(metal()), true);
        RADIATOR = reg("radiator", () -> new RadiatorBlock(metal()), true);
        VENT_BLOCK = reg("vent_block", () -> new VentBlock(metal()), true);
        GRILLE_BLOCK = reg("grille_block", () -> new GrilleBlock(metal()), true);
        CONTROL_PANEL = reg("control_panel", () -> new ControlPanelBlock(metal()), true);
        THERMOMETER = reg("thermometer", () -> new ThermometerBlock(metal()), true);
    }

    private ModBlocks() {}
}
