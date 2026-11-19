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
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;

public class ACOutdoorBlockEntity extends BlockEntity {
    private final Set<BlockPos> linkedIndoors = new HashSet<>();
    private final Set<BlockPos> linkedPanels = new HashSet<>();
    private boolean enabled = true;
    private double targetTemp = 22.0;

    public ACOutdoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AC_OUTDOOR.get(), pos, state);
    }

    public void setLinkedIndoors(Set<BlockPos> indoors) { this.linkedIndoors.clear(); this.linkedIndoors.addAll(indoors); }
    public Set<BlockPos> getLinkedIndoors() { return linkedIndoors; }
    public boolean linkIndoor(BlockPos pos) {
        if (linkedIndoors.size() >= 3 && !linkedIndoors.contains(pos)) return false;
        boolean changed = linkedIndoors.add(pos);
        if (changed) setChanged();
        return changed;
    }
    public void unlinkIndoor(BlockPos pos) { if (linkedIndoors.remove(pos)) setChanged(); }
    public void unlinkAll() { if (!linkedIndoors.isEmpty()) { linkedIndoors.clear(); setChanged(); } }
    public Set<BlockPos> getLinkedPanels() { return linkedPanels; }
    public boolean linkPanel(BlockPos pos) { boolean changed = linkedPanels.add(pos); if (changed) setChanged(); return changed; }
    public void unlinkPanel(BlockPos pos) { if (linkedPanels.remove(pos)) setChanged(); }
    public void unlinkAllPanels() { if (!linkedPanels.isEmpty()) { linkedPanels.clear(); setChanged(); } }

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
        CompoundTag linkedPanelsTag = new CompoundTag();
        int panelIndex = 0;
        for (BlockPos panelPos : linkedPanels) {
            linkedPanelsTag.putString("P" + panelIndex++, panelPos.getX() + "," + panelPos.getY() + "," + panelPos.getZ());
        }
        tag.put("LinkedPanels", linkedPanelsTag);
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
        targetTemp = tag.getDouble("TargetTemp");
        linkedIndoors.clear();
        CompoundTag linked = tag.getCompound("Linked");
        linkedPanels.clear();
        CompoundTag linkedPanelsTag = tag.getCompound("LinkedPanels");
        for (String key : linkedPanelsTag.getAllKeys()) {
            String[] xyz = linkedPanelsTag.getString(key).split(",");
            if (xyz.length == 3) linkedPanels.add(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
        }
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
        double target = be.getTargetTemp();
        for (BlockPos panelPos : be.linkedPanels) {
            BlockEntity panelEntity = level.getBlockEntity(panelPos);
            if (panelEntity instanceof ControlPanelBlockEntity panel && panel.isEnabled()) {
                target = panel.getTargetTemp();
                be.targetTemp = target;
                break;
            }
        }
        double outside = ClimateService.getTemperature(level, pos);
        double cooling = (outside - target) * 0.5;
        if (Math.abs(cooling) > 0.5 && !be.linkedIndoors.isEmpty()) {
            double splitCooling = cooling / be.linkedIndoors.size();
            for (BlockPos ip : be.linkedIndoors) {
                if (level.isLoaded(ip)) {
                    BlockEntity ibe = level.getBlockEntity(ip);
                    if (ibe instanceof ACIndoorBlockEntity indoor) {
                        indoor.addCool(splitCooling * 0.3);
                        indoor.setTargetTemp(target);
                        indoor.setEnabled(true);
                    }
                }
            }
        }

        propagateThroughVentilation(level, pos, target);
    }

    private static void propagateThroughVentilation(Level level, BlockPos outdoorPos, double targetTemp) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(outdoorPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            if (!visited.add(current) || visited.size() > 512) continue;

            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos next = current.relative(direction);
                if (visited.contains(next) || !level.isLoaded(next)) continue;
                BlockState nextState = level.getBlockState(next);
                if (nextState.getBlock() instanceof com.realclimate.block.VentBlock
                        || nextState.getBlock() instanceof com.realclimate.block.GrilleBlock) {
                    if (nextState.getBlock() instanceof com.realclimate.block.VentBlock
                            && level.getBlockEntity(next) instanceof com.realclimate.tileentity.VentBlockEntity vent) {
                        vent.setSealed(true);
                        vent.setTempBuffer(targetTemp);
                    }
                    if (level.getBlockEntity(next) instanceof com.realclimate.tileentity.GrilleBlockEntity grille) {
                        grille.setTargetTemp(targetTemp);
                    }
                    queue.add(next);
                }
            }
        }
    }
}
