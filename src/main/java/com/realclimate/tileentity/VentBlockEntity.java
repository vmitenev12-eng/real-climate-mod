package com.realclimate.tileentity;

import com.realclimate.block.VentBlock;
import com.realclimate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class VentBlockEntity extends BlockEntity {
    private boolean sealed = false;
    private int roomId = -1;
    private double tempBuffer = 0.0;

    public VentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VENT.get(), pos, state);
    }

    public boolean isSealed() { return sealed; }
    public void setSealed(boolean s) { this.sealed = s; setChanged(); }
    public int getRoomId() { return roomId; }
    public void setRoomId(int id) { this.roomId = id; setChanged(); }
    public double getTempBuffer() { return tempBuffer; }
    public void addTemp(double t) { this.tempBuffer += t; }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Sealed", sealed);
        tag.putInt("RoomId", roomId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        sealed = tag.getBoolean("Sealed");
        roomId = tag.getInt("RoomId");
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, VentBlockEntity be) {
        if (!level.isClientSide && level.getGameTime() % 20 == 0) {
            FluidState fs = level.getFluidState(pos);
            boolean waterlogged = fs.getType() == Fluids.WATER;
            level.setBlock(pos, state.setValue(VentBlock.SEALED, waterlogged), 3);
        }
    }
}
