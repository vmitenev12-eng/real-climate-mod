package com.realclimate.tileentity;

import com.realclimate.block.ACIndoorBlock;
import com.realclimate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

public class ACIndoorBlockEntity extends BlockEntity {
    private BlockPos linkedOutdoor;
    private boolean enabled = true;
    private double coolBuffer = 0.0;
    private double targetTemp = 22.0;

    public ACIndoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AC_INDOOR.get(), pos, state);
    }

    public BlockPos getLinkedOutdoor() { return linkedOutdoor; }
    public void setLinkedOutdoor(BlockPos p) { this.linkedOutdoor = p; setChanged(); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; setChanged(); }
    public double getCoolBuffer() { return coolBuffer; }
    public double getTargetTemp() { return targetTemp; }
    public void setTargetTemp(double targetTemp) { this.targetTemp = Math.max(-20.0, Math.min(95.0, targetTemp)); setChanged(); }
    public void addCool(double c) { this.coolBuffer += c; }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (linkedOutdoor != null) {
            tag.putInt("LinkedX", linkedOutdoor.getX());
            tag.putInt("LinkedY", linkedOutdoor.getY());
            tag.putInt("LinkedZ", linkedOutdoor.getZ());
        }
        tag.putBoolean("Enabled", enabled);
        tag.putDouble("TargetTemp", targetTemp);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("LinkedX")) {
            linkedOutdoor = new BlockPos(tag.getInt("LinkedX"), tag.getInt("LinkedY"), tag.getInt("LinkedZ"));
        }
        enabled = tag.getBoolean("Enabled");
        targetTemp = Math.max(-20.0, Math.min(95.0, tag.getDouble("TargetTemp")));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ACIndoorBlockEntity be) {
        if (!be.isEnabled() || be.linkedOutdoor == null) return;
        double cool = be.coolBuffer;
        be.coolBuffer = 0;
        if (Math.abs(cool) > 0.01) {
            level.setBlock(pos, state.setValue(ACIndoorBlock.ACTIVE, Math.abs(cool) > 0.1), 3);
        } else {
            level.setBlock(pos, state.setValue(ACIndoorBlock.ACTIVE, false), 3);
        }
    }
}
