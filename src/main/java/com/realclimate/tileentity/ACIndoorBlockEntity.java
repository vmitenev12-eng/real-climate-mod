package com.realclimate.tileentity;

import com.realclimate.block.ACIndoorBlock;
import com.realclimate.climate.ClimateService;
import com.realclimate.climate.IndoorClimate;
import com.realclimate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

/**
 * Внутренний блок сплит-системы. Уставка приходит от наружного блока
 * (push при его тике) и дополнительно подтягивается самим блоком (pull) —
 * цепочка пульт→наружный→внутренний самовосстанавливается, даже если одно
 * звено отставало при загрузке чанков.
 *
 * Блок работает как термостат: раз в секунду замеряет равновесие комнаты
 * (IndoorClimate) и докручивает температуру струи (emission), пока комната
 * не выйдет на уставку. Поэтому заданная на пульте температура достигается,
 * а не «тянется на треть пути», как при фиксированной мощности. Стены, щели
 * и соседний радиатор компенсируются регулятором автоматически. Коридор
 * струи широкий (±60° от рабочего диапазона, пределы −80..+160 °C), чтобы
 * морозные уставки (−20 °C) были достижимы и при открытой двери.
 * ACTIVE = блок реально дует (есть запрос по температуре).
 */
public class ACIndoorBlockEntity extends BlockEntity {
    private BlockPos linkedOutdoor;
    private boolean enabled = true;
    private double targetTemp = 22.0;
    private double emission = Double.NaN;   // температура струи, выводится регулятором

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
    /** Текущая температура струи (NaN — блок ещё не работал). */
    public double getEmission() { return emission; }

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
        if (!Double.isNaN(emission)) tag.putDouble("Emission", emission);
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
        emission = tag.contains("Emission") ? tag.getDouble("Emission") : Double.NaN;
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
        // Подтягиваем уставку из цепочки сами (наружный блок тоже пушит её
        // сюда — дублирование делает связку устойчивой к порядку тиков).
        be.setTargetTemp(outdoor.getOperatingTargetTemp(level));
        double target = be.targetTemp;

        // Замеряем комнату из соседней воздушной клетки: при скане из своего
        // собственного блока вклад самого блока в равновесие не считается,
        // и регулятор не увидел бы обратной связи.
        BlockPos measurePos = pos;
        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);
            if (level.isLoaded(n) && level.getBlockState(n).isAir()) { measurePos = n; break; }
        }
        double raw = ClimateService.getTemperature(level, pos);
        double equilibrium = IndoorClimate.roomEquilibrium(level, measurePos, raw);
        double error = target - equilibrium;

        // ACTIVE = «дует»: включаемся с запасом 0.35°, выключаемся у цели (0.1°)
        // — гистерезис, чтобы блок не мигал каждую секунду.
        boolean active = state.getValue(ACIndoorBlock.ACTIVE);
        if (active) {
            if (Math.abs(error) < 0.1) active = false;
        } else if (Math.abs(error) > 0.35) {
            active = true;
        }

        if (active) {
            // Регулятор струи: равновесие уже включает вклад этого блока, так
            // что обратная связь сама находит мощность именно для этой комнаты.
            if (Double.isNaN(be.emission)) be.emission = target;
            if (Math.abs(error) > 0.05) {
                be.emission += error * 0.9;
                double lo = Math.min(target, raw) - 60.0;
                double hi = Math.max(target, raw) + 60.0;
                be.emission = Math.max(-80.0, Math.min(160.0, Math.max(lo, Math.min(hi, be.emission))));
                be.setChanged();
            }
        } else if (!Double.isNaN(be.emission)) {
            // На паузе струя плавно возвращается к уставке, без накопления.
            be.emission += (target - be.emission) * 0.2;
            be.setChanged();
        }
        setActive(level, pos, state, active);
    }

    private static void setActive(Level level, BlockPos pos, BlockState state, boolean active) {
        if (state.getValue(ACIndoorBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ACIndoorBlock.ACTIVE, active), 3);
        }
    }
}
