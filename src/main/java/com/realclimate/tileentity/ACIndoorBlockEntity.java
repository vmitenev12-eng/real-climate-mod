package com.realclimate.tileentity;

import com.realclimate.block.ACIndoorBlock;
import com.realclimate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import com.realclimate.climate.ClimateService;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

public class ACIndoorBlockEntity extends BlockEntity {
    private BlockPos linkedOutdoor;
    private boolean enabled = true;
    private double targetTemp = 22.0;

    public ACIndoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AC_INDOOR.get(), pos, state);
    }

    public BlockPos getLinkedOutdoor() { return linkedOutdoor; }
    public void setLinkedOutdoor(BlockPos p) {
        if (java.util.Objects.equals(linkedOutdoor, p)) return;
        linkedOutdoor = p;
        setChanged();
    }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { if (enabled != e) { enabled = e; setChanged(); } }
    public double getTargetTemp() { return targetTemp; }
    public void setTargetTemp(double targetTemp) {
        double clamped = Math.max(-20.0, Math.min(95.0, targetTemp));
        if (this.targetTemp != clamped) { this.targetTemp = clamped; setChanged(); }
    }

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
        linkedOutdoor = null;
        if (tag.contains("LinkedX")) {
            linkedOutdoor = new BlockPos(tag.getInt("LinkedX"), tag.getInt("LinkedY"), tag.getInt("LinkedZ"));
        }
        enabled = tag.getBoolean("Enabled");
        targetTemp = Math.max(-20.0, Math.min(95.0, tag.getDouble("TargetTemp")));
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ACIndoorBlockEntity be) {
        if (!be.isEnabled() || be.linkedOutdoor == null) {
            setActive(level, pos, state, false);
            return;
        }
        if (level.getGameTime() % 20 != 0) return;
        BlockPos outdoorPos = be.linkedOutdoor;
        if (!level.isLoaded(outdoorPos)
            || !(level.getBlockEntity(outdoorPos) instanceof ACOutdoorBlockEntity outdoor)
                || !outdoor.isEnabled()) {
            setActive(level, pos, state, false);
            return;
        }
        double ambient = ClimateService.getTemperature(level, pos);
        setActive(level, pos, state, Math.abs(be.targetTemp - ambient) > 0.1);
    }

    private static void setActive(Level level, BlockPos pos, BlockState state, boolean active) {
        if (state.getValue(ACIndoorBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ACIndoorBlock.ACTIVE, active), 3);
        }
    }
}
