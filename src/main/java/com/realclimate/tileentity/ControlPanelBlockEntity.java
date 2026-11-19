package com.realclimate.tileentity;

import com.realclimate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

import java.util.HashSet;
import java.util.Set;

public class ControlPanelBlockEntity extends BlockEntity {
    private final Set<BlockPos> linkedOutdoors = new HashSet<>();
    private boolean enabled = true;
    private double targetTemp = 22.0;

    public ControlPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTROL_PANEL.get(), pos, state);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; setChanged(); }
    public double getTargetTemp() { return targetTemp; }
    public void setTargetTemp(double targetTemp) { this.targetTemp = Math.max(-20.0, Math.min(95.0, targetTemp)); setChanged(); }
    public Set<BlockPos> getLinkedOutdoors() { return linkedOutdoors; }

    public boolean linkOutdoor(BlockPos pos) {
        boolean changed = linkedOutdoors.add(pos);
        if (changed) setChanged();
        return changed;
    }

    public void unlinkOutdoor(BlockPos pos) {
        if (linkedOutdoors.remove(pos)) setChanged();
    }

    public void unlinkAll() {
        if (!linkedOutdoors.isEmpty()) {
            linkedOutdoors.clear();
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Enabled", enabled);
        tag.putDouble("TargetTemp", targetTemp);
        CompoundTag linked = new CompoundTag();
        int index = 0;
        for (BlockPos pos : linkedOutdoors) {
            linked.putString("O" + index++, pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }
        tag.put("Linked", linked);
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

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        enabled = tag.getBoolean("Enabled");
        targetTemp = Math.max(-20.0, Math.min(95.0, tag.getDouble("TargetTemp")));
        linkedOutdoors.clear();
        CompoundTag linked = tag.getCompound("Linked");
        for (String key : linked.getAllKeys()) {
            String[] parts = linked.getString(key).split(",");
            if (parts.length == 3) {
                linkedOutdoors.add(new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            }
        }
    }
}
