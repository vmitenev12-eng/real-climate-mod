package com.realclimate.tileentity;

import com.realclimate.block.GrilleBlock;
import com.realclimate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

public class GrilleBlockEntity extends BlockEntity {
    private boolean open = true;

    public GrilleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRILLE.get(), pos, state);
    }

    public boolean isOpen() { return open; }
    public void setOpen(boolean o) { this.open = o; setChanged(); }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Open", open);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        open = tag.getBoolean("Open");
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GrilleBlockEntity be) {
        // grille passes air when open
    }
}
