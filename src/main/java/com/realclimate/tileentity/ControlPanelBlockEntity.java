package com.realclimate.tileentity;

import com.realclimate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

import java.util.HashSet;
import java.util.Set;

public class ControlPanelBlockEntity extends BlockEntity {
    private static final String MEASURED_KEY = "LastMeasured";
    private final Set<BlockPos> linkedOutdoors = new HashSet<>();
    private final Set<BlockPos> linkedThermometers = new HashSet<>();
    private boolean enabled = true;
    private double targetTemp = 22.0;
    private double integral = 0.0;
    /** Последнее усреднённое показание привязанных термодатчиков (для экрана пульта). */
    private double lastMeasured = Double.NaN;

    public ControlPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTROL_PANEL.get(), pos, state);
    }

    /** Сохранить и отправить обновление клиенту — без этого экран пульта не меняется. */
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { if (this.enabled != enabled) { this.enabled = enabled; sync(); } }
    public double getTargetTemp() { return targetTemp; }
    public void setTargetTemp(double targetTemp) {
        double clamped = Math.max(-20.0, Math.min(95.0, targetTemp));
        if (this.targetTemp != clamped) { this.targetTemp = clamped; sync(); }
    }
    public Set<BlockPos> getLinkedOutdoors() { return linkedOutdoors; }
    public Set<BlockPos> getLinkedThermometers() { return linkedThermometers; }
    public double getLastMeasured() { return lastMeasured; }

    /** Авторежим: пульт сам регулирует кондиционеры по показаниям датчиков. */
    public boolean isAutoMode() { return enabled && !linkedThermometers.isEmpty() && !linkedOutdoors.isEmpty(); }

    public boolean linkOutdoor(BlockPos pos) {
        boolean changed = linkedOutdoors.add(pos);
        if (changed) sync();
        return changed;
    }

    public void unlinkOutdoor(BlockPos pos) {
        if (linkedOutdoors.remove(pos)) sync();
    }

    public void unlinkAll() {
        if (!linkedOutdoors.isEmpty() || !linkedThermometers.isEmpty()) {
            linkedOutdoors.clear();
            linkedThermometers.clear();
            integral = 0.0;
            sync();
        }
    }

    public boolean linkThermometer(BlockPos pos) {
        boolean changed = linkedThermometers.add(pos);
        if (changed) { integral = 0.0; sync(); }
        return changed;
    }

    public void unlinkThermometer(BlockPos pos) {
        if (linkedThermometers.remove(pos)) { integral = 0.0; sync(); }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ControlPanelBlockEntity be) {
        if (level.getGameTime() % 20 != 0) return;
        be.integral *= 0.98;   // затухание интегральной составляющей

        // Замер обновляем всегда, пока привязан хоть один датчик:
        // экран пульта обязан жить и в ручном режиме, а не только в авторежиме.
        double measured = Double.NaN;
        if (!be.linkedThermometers.isEmpty()) {
            double sum = 0.0;
            int count = 0;
            for (BlockPos sensorPos : be.linkedThermometers) {
                if (!level.isLoaded(sensorPos)) continue;
                if (level.getBlockEntity(sensorPos) instanceof ThermometerBlockEntity thermometer) {
                    sum += thermometer.getMeasured();
                    count++;
                }
            }
            if (count > 0) measured = sum / count;
        }
        if (!Double.isNaN(measured)
                && (Double.isNaN(be.lastMeasured) || Math.abs(be.lastMeasured - measured) > 0.05)) {
            be.lastMeasured = measured;
            be.sync();
        }

        if (!be.isAutoMode() || Double.isNaN(measured)) return;

        // ПИ-регулятор: error > 0 — в помещении холоднее уставки, греем сильнее.
        double error = be.targetTemp - measured;
        be.integral = Math.max(-15.0, Math.min(15.0, be.integral + error * 0.15));
        double output = Math.max(-20.0, Math.min(95.0, be.targetTemp + error * 1.5 + be.integral));

        for (BlockPos outdoorPos : be.linkedOutdoors) {
            if (!level.isLoaded(outdoorPos)) continue;
            if (level.getBlockEntity(outdoorPos) instanceof ACOutdoorBlockEntity outdoor && outdoor.isEnabled()) {
                outdoor.setTargetTemp(output);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Enabled", enabled);
        tag.putDouble("TargetTemp", targetTemp);
        tag.putDouble("Integral", integral);
        if (!Double.isNaN(lastMeasured)) tag.putDouble(MEASURED_KEY, lastMeasured);
        CompoundTag linked = new CompoundTag();
        int index = 0;
        for (BlockPos pos : linkedOutdoors) {
            linked.putString("O" + index++, pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }
        tag.put("Linked", linked);
        CompoundTag sensors = new CompoundTag();
        int sensorIndex = 0;
        for (BlockPos pos : linkedThermometers) {
            sensors.putString("T" + sensorIndex++, pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }
        tag.put("LinkedTh", sensors);
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
        integral = tag.getDouble("Integral");
        lastMeasured = tag.contains(MEASURED_KEY) ? tag.getDouble(MEASURED_KEY) : Double.NaN;
        linkedOutdoors.clear();
        CompoundTag linked = tag.getCompound("Linked");
        for (String key : linked.getAllKeys()) {
            String[] parts = linked.getString(key).split(",");
            if (parts.length == 3) {
                linkedOutdoors.add(new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            }
        }
        linkedThermometers.clear();
        CompoundTag sensors = tag.getCompound("LinkedTh");
        for (String key : sensors.getAllKeys()) {
            String[] parts = sensors.getString(key).split(",");
            if (parts.length == 3) {
                linkedThermometers.add(new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            }
        }
    }
}
