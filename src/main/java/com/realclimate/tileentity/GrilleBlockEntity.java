package com.realclimate.tileentity;

import com.realclimate.climate.ClimateService;
import com.realclimate.climate.IndoorClimate;
import com.realclimate.climate.VentilationService;
import com.realclimate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

/**
 * Вентиляционная решётка. Пока по магистрали идёт поток (есть активный
 * наружный блок), решётка работает как термостат: раз в секунду замеряет
 * равновесие комнаты и докручивает температуру потока (emission), пока
 * комната не выйдет на уставку своей системы — холод/тепло реально
 * «излучается» из решётки, а не тянется к цели на треть пути. Коридор
 * потока широкий (±60° от рабочего диапазона, пределы −80..+160 °C),
 * чтобы морозные уставки были достижимы даже с утечками.
 */
public class GrilleBlockEntity extends BlockEntity {
    private boolean open = true;
    private double targetTemp = 22.0;
    private double emission = Double.NaN;   // температура потока, выводится регулятором

    public GrilleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRILLE.get(), pos, state);
    }

    public boolean isOpen() { return open; }
    public void setOpen(boolean o) { this.open = o; setChanged(); }
    public double getTargetTemp() { return targetTemp; }
    public void setTargetTemp(double targetTemp) { this.targetTemp = Math.max(-20.0, Math.min(95.0, targetTemp)); setChanged(); }
    /** Текущая температура потока из решётки (NaN — потока сейчас нет). */
    public double getEmission() { return emission; }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Open", open);
        tag.putDouble("TargetTemp", targetTemp);
        if (!Double.isNaN(emission)) tag.putDouble("Emission", emission);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        open = tag.getBoolean("Open");
        targetTemp = Math.max(-20.0, Math.min(95.0, tag.getDouble("TargetTemp")));
        emission = tag.contains("Emission") ? tag.getDouble("Emission") : Double.NaN;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GrilleBlockEntity be) {
        if (level.getGameTime() % 20 != 0) return;
        if (!be.open || !VentilationService.hasActiveAirflow(level, pos)) {
            if (!Double.isNaN(be.emission)) { be.emission = Double.NaN; be.setChanged(); }
            return;
        }
        double target = be.targetTemp;
        // Замер из соседней воздушной клетки — как и у внутреннего блока,
        // чтобы вклад самой решётки попадал в скан комнаты.
        BlockPos measurePos = pos;
        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);
            if (level.isLoaded(n) && level.getBlockState(n).isAir()) { measurePos = n; break; }
        }
        double raw = ClimateService.getTemperature(level, pos);
        double equilibrium = IndoorClimate.roomEquilibrium(level, measurePos, raw);
        double error = target - equilibrium;
        if (Double.isNaN(be.emission)) {
            be.emission = VentilationService.airflowTemperature(level, pos, target);
        }
        if (Math.abs(error) > 0.05) {
            be.emission += error * 0.9;
            double lo = Math.min(target, raw) - 60.0;
            double hi = Math.max(target, raw) + 60.0;
            be.emission = Math.max(-80.0, Math.min(160.0, Math.max(lo, Math.min(hi, be.emission))));
            be.setChanged();
        }
    }
}
