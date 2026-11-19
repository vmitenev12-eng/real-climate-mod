package com.realclimate.tileentity;

import com.realclimate.block.ACOutdoorBlock;
import com.realclimate.climate.VentilationService;
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
        return true;
    }
    public void unlinkIndoor(BlockPos pos) { if (linkedIndoors.remove(pos)) setChanged(); }
    public void unlinkAll() { if (!linkedIndoors.isEmpty()) { linkedIndoors.clear(); setChanged(); } }
    public Set<BlockPos> getLinkedPanels() { return linkedPanels; }
    public boolean linkPanel(BlockPos pos) { boolean changed = linkedPanels.add(pos); if (changed) setChanged(); return changed; }
    public void unlinkPanel(BlockPos pos) { if (linkedPanels.remove(pos)) setChanged(); }
    public void unlinkAllPanels() { if (!linkedPanels.isEmpty()) { linkedPanels.clear(); setChanged(); } }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { if (enabled != e) { enabled = e; setChanged(); } }
    public double getTargetTemp() { return targetTemp; }
    public void setTargetTemp(double t) {
        double clamped = Math.max(-20.0, Math.min(95.0, t));
        if (targetTemp != clamped) {
            targetTemp = clamped;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

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
        targetTemp = Math.max(-20.0, Math.min(95.0, tag.getDouble("TargetTemp")));
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
        linkedPanels.clear();
        if (level != null) VentilationService.removeSource(level, worldPosition);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ACOutdoorBlockEntity be) {
        if (level.getGameTime() % 20 != 0) return;
        if (!be.isEnabled()) {
            for (BlockPos indoorPos : be.linkedIndoors) {
                if (level.isLoaded(indoorPos) && level.getBlockEntity(indoorPos) instanceof ACIndoorBlockEntity indoor) {
                    indoor.setEnabled(false);
                }
            }
            VentilationService.removeSource(level, pos);
            return;
        }
        double target = be.getOperatingTargetTemp(level);
        if (!be.linkedIndoors.isEmpty()) {
            for (BlockPos ip : be.linkedIndoors) {
                if (level.isLoaded(ip)) {
                    BlockEntity ibe = level.getBlockEntity(ip);
                    if (ibe instanceof ACIndoorBlockEntity indoor && pos.equals(indoor.getLinkedOutdoor())) {
                        indoor.setTargetTemp(target);
                        indoor.setEnabled(true);
                    }
                }
            }
        }

        propagateThroughVentilation(level, pos, target, true);
    }

    public static void clientTick(Level level, BlockPos pos, ACOutdoorBlockEntity be) {
        if (level.getGameTime() % 20 != 0) return;
        if (!be.isEnabled()) {
            VentilationService.removeSource(level, pos);
            return;
        }
        propagateThroughVentilation(level, pos, be.getOperatingTargetTemp(level), false);
    }

    private double getOperatingTargetTemp(Level level) {
        for (BlockPos panelPos : linkedPanels) {
            if (!level.isLoaded(panelPos)) continue;
            if (level.getBlockEntity(panelPos) instanceof ControlPanelBlockEntity panel
                    && panel.isEnabled() && panel.getLinkedOutdoors().contains(worldPosition)) {
                return panel.getTargetTemp();
            }
        }
        return getTargetTemp();
    }

    private static void propagateThroughVentilation(Level level, BlockPos outdoorPos, double targetTemp,
                                                    boolean updateBlockEntities) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        java.util.Map<BlockPos, Double> airflow = new java.util.HashMap<>();
        queue.add(outdoorPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            if (!visited.add(current) || visited.size() > 512) continue;

            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos next = current.relative(direction);
                if (visited.contains(next) || !level.isLoaded(next)) continue;
                BlockState nextState = level.getBlockState(next);
                if (nextState.isAir()) {
                    BlockPos afterGap = next.relative(direction);
                    if (level.isLoaded(afterGap) && isDuctOrGrille(level.getBlockState(afterGap))) {
                        airflow.put(next.immutable(), 1.0);
                        queue.add(afterGap);
                    }
                    continue;
                }
                if (isDuctOrGrille(nextState)) {
                    String blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(nextState.getBlock()).getPath();
                    boolean terminal = nextState.getBlock() instanceof com.realclimate.block.GrilleBlock
                            || blockId.equals("vent_ceiling_grille");
                    int expectedConnections = expectedConnections(blockId);
                    int connected = 0;
                    for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                        BlockPos neighborPos = next.relative(side);
                        BlockState neighbor = level.getBlockState(neighborPos);
                        if (neighbor.getBlock() instanceof ACOutdoorBlock || isDuctOrGrille(neighbor)) connected++;
                    }
                        double loss = terminal
                            ? (level.getBlockEntity(next) instanceof com.realclimate.tileentity.GrilleBlockEntity ? 0.0 : 2.0)
                            : Math.max(0.0, (expectedConnections - connected) / (double) expectedConnections);
                    airflow.put(next.immutable(), loss);
                    if (updateBlockEntities && nextState.getBlock() instanceof com.realclimate.block.VentBlock
                            && level.getBlockEntity(next) instanceof com.realclimate.tileentity.VentBlockEntity vent) {
                        vent.setSealed(loss == 0.0);
                        vent.setTempBuffer(targetTemp);
                    }
                    if (updateBlockEntities
                            && level.getBlockEntity(next) instanceof com.realclimate.tileentity.GrilleBlockEntity grille) {
                        grille.setTargetTemp(targetTemp);
                    }
                    queue.add(next);
                }
            }
        }
        VentilationService.updatePath(level, outdoorPos, airflow, targetTemp);
    }

    private static boolean isDuctOrGrille(BlockState state) {
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null || !id.getNamespace().equals("real_climate")) return false;
        return id.getPath().startsWith("vent_") || id.getPath().equals("vent_block")
                || id.getPath().equals("grille_block");
    }

    private static int expectedConnections(String blockId) {
        if (blockId.contains("cross")) return 4;
        if (blockId.contains("tee")) return 3;
        return 2;
    }
}
