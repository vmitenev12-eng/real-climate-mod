package com.realclimate.tileentity;

import com.realclimate.block.RadiatorBlock;
import com.realclimate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import com.realclimate.climate.ClimateService;

public class RadiatorBlockEntity extends BlockEntity {
    private boolean enabled = true;
    private double targetTemp = 22.0;
    private double heatBuffer = 0.0;

    public RadiatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADIATOR.get(), pos, state);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; setChanged(); }
    public double getTargetTemp() { return targetTemp; }
    public void setTargetTemp(double t) { this.targetTemp = Math.max(-20.0, Math.min(95.0, t)); setChanged(); }
    public double getHeatBuffer() { return heatBuffer; }
    public void addHeat(double h) { this.heatBuffer += h; }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Enabled", enabled);
        tag.putDouble("TargetTemp", targetTemp);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        enabled = tag.getBoolean("Enabled");
        targetTemp = Math.max(-20.0, Math.min(95.0, tag.getDouble("TargetTemp")));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadiatorBlockEntity be) {
        if (!be.isEnabled()) {
            if (state.getValue(RadiatorBlock.ACTIVE)) {
                level.setBlock(pos, state.setValue(RadiatorBlock.ACTIVE, false), 3);
            }
            return;
        }
        if (level.getGameTime() % 20 != 0) return;

        double outside = ClimateService.getTemperature(level, pos);
        double neededHeat = Math.max(0.0, be.getTargetTemp() - outside);
        be.heatBuffer = neededHeat;
        boolean active = neededHeat > 0.5;
        if (state.getValue(RadiatorBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(RadiatorBlock.ACTIVE, active), 3);
        }
    }
}
