package com.realclimate.tileentity;

import com.realclimate.climate.ClimateService;
import com.realclimate.climate.IndoorClimate;
import com.realclimate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Логика термодатчика: ведёт температуру воздуха в точке установки с инерцией
 * помещения, как и у игроков (IndoorClimate), и отдаёт её пульту управления.
 */
public class ThermometerBlockEntity extends BlockEntity {
    private static final String TEMP_KEY = "AirTemp";
    private double airTemp = Double.NaN;

    public ThermometerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THERMOMETER.get(), pos, state);
    }

    /** Фактическая температура воздуха вокруг датчика. */
    public double getMeasured() {
        if (Double.isNaN(airTemp)) {
            if (level == null) return 20.0;
            double raw = ClimateService.temperature(
                    worldPosition.getX(), worldPosition.getZ(), worldPosition.getY());
            return IndoorClimate.roomEquilibrium(level, worldPosition, raw);
        }
        return airTemp;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ThermometerBlockEntity be) {
        if (level.getGameTime() % 40 != 0) return;
        double raw = ClimateService.temperature(pos.getX(), pos.getZ(), pos.getY());
        if (!level.canSeeSky(pos) && pos.getY() < level.getSeaLevel() - 8) {
            raw = raw * 0.4 + 6.0;
        }
        double equilibrium = IndoorClimate.roomEquilibrium(level, pos, raw);
        if (Double.isNaN(be.airTemp)) be.airTemp = equilibrium;
        double shelter = IndoorClimate.shelter(level, pos);
        double rate = 0.45 * (1.0 - shelter) + 0.07 * shelter;
        double updated = be.airTemp + (equilibrium - be.airTemp) * rate;
        if (Math.abs(updated - be.airTemp) > 0.005) {
            be.airTemp = updated;
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!Double.isNaN(airTemp)) tag.putDouble(TEMP_KEY, airTemp);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        airTemp = tag.contains(TEMP_KEY) ? tag.getDouble(TEMP_KEY) : Double.NaN;
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
}
