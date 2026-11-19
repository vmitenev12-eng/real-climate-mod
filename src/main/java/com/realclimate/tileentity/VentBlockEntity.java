package com.realclimate.tileentity;

import com.realclimate.block.VentBlock;
import com.realclimate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import com.realclimate.block.GrilleBlock;
import com.realclimate.block.ACOutdoorBlock;
import com.realclimate.climate.MiniBlockIntegration;

import java.util.*;

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
    public void setTempBuffer(double t) { this.tempBuffer = t; setChanged(); }
    public void addTemp(double t) { this.tempBuffer += t; setChanged(); }

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
        if (level.isClientSide || level.getGameTime() % 20 != 0) return;

        boolean sealed = true;
        double loss = 0.0;
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighbor = level.getBlockState(neighborPos);
            boolean validVentConnection = neighbor.getBlock() instanceof VentBlock
                    || neighbor.getBlock() instanceof GrilleBlock
                    || neighbor.getBlock() instanceof ACOutdoorBlock
                    || neighbor.isAir();

            if (!validVentConnection) {
                double gap = MiniBlockIntegration.openGapRatio(level, neighborPos, direction);
                if (gap > 0.0) {
                    sealed = false;
                    loss = Math.max(loss, gap);
                }
            }
            if (neighbor.isAir()) {
                sealed = false;
                loss = Math.max(loss, 1.0);
            }
        }

        be.setSealed(sealed);
        be.setTempBuffer(loss);
        if (state.hasProperty(VentBlock.SEALED)) {
            level.setBlock(pos, state.setValue(VentBlock.SEALED, sealed), 3);
        }
    }
}
