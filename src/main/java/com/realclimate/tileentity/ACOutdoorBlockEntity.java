package com.realclimate.tileentity;

import com.realclimate.block.ACOutdoorBlock;
import com.realclimate.climate.ClimateService;
import com.realclimate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;

public class ACOutdoorBlockEntity extends BlockEntity {
    private final Set<BlockPos> linkedIndoors = new HashSet<>();
    private boolean enabled = true;
    private double targetTemp = 22.0;

    public ACOutdoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AC_OUTDOOR.get(), pos, state);
    }

    public void setLinkedIndoors(Set<BlockPos> indoors) { this.linkedIndoors.clear(); this.linkedIndoors.addAll(indoors); }
    public Set<BlockPos> getLinkedIndoors() { return linkedIndoors; }
    public boolean linkIndoor(BlockPos pos) { return linkedIndoors.add(pos); }
    public void unlinkIndoor(BlockPos pos) { linkedIndoors.remove(pos); }
    public void unlinkAll() { linkedIndoors.clear(); }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; setChanged(); }
    public double getTargetTemp() { return targetTemp; }
    public void setTargetTemp(double t) { this.targetTemp = t; setChanged(); }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Enabled", enabled);
        tag.putDouble("TargetTemp", targetTemp);
        CompoundTag linked = new CompoundTag();
        int i = 0;
        for (BlockPos p : linkedIndoors) {
            linked.putString("I" + i++, p.getX() + "," + p.getY() + "," + p.getZ());
        }
        tag.put("Linked", linked);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        enabled = tag.getBoolean("Enabled");
        targetTemp = tag.getDouble("TargetTemp");
        linkedIndoors.clear();
        CompoundTag linked = tag.getCompound("Linked");
        for (String key : linked.getAllKeys()) {
            String[] xyz = linked.getString(key).split(",");
            linkedIndoors.add(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
        }
    }

    public void onRemove() {
        linkedIndoors.clear();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ACOutdoorBlockEntity be) {
        if (!be.isEnabled()) return;
        double outside = ClimateService.temperature(pos.getX(), pos.getZ(), 1.0);
        double cooling = (outside - be.getTargetTemp()) * 0.5;
        if (Math.abs(cooling) > 0.5) {
            for (BlockPos ip : be.linkedIndoors) {
                if (level.isLoaded(ip)) {
                    BlockEntity ibe = level.getBlockEntity(ip);
                    if (ibe instanceof ACIndoorBlockEntity indoor) {
                        indoor.addCool(cooling * 0.3);
                    }
                }
            }
        }
    }
}
