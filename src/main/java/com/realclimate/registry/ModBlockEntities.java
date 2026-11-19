package com.realclimate.registry;

import com.realclimate.RealClimate;
import com.realclimate.tileentity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.function.Supplier;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, RealClimate.MODID);

    public static final RegistryObject<BlockEntityType<ACOutdoorBlockEntity>> AC_OUTDOOR =
            register("ac_outdoor", ACOutdoorBlockEntity::new, ModBlocks.AC_OUTDOOR);
    public static final RegistryObject<BlockEntityType<ACIndoorBlockEntity>> AC_INDOOR =
            register("ac_indoor", ACIndoorBlockEntity::new, ModBlocks.AC_INDOOR);
    public static final RegistryObject<BlockEntityType<RadiatorBlockEntity>> RADIATOR =
            register("radiator", RadiatorBlockEntity::new, ModBlocks.RADIATOR);
    public static final RegistryObject<BlockEntityType<VentBlockEntity>> VENT =
            register("vent", VentBlockEntity::new, ModBlocks.VENT_BLOCK);
    public static final RegistryObject<BlockEntityType<GrilleBlockEntity>> GRILLE =
            register("grille", GrilleBlockEntity::new, ModBlocks.GRILLE_BLOCK);

    private static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> register(
            String id, BlockEntityType.BlockEntitySupplier<T> factory,
            java.util.function.Supplier<net.minecraft.world.level.block.Block> block) {
        return BLOCK_ENTITIES.register(id, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }

    private ModBlockEntities() {}
}
