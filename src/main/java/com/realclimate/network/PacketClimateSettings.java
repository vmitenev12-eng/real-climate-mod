package com.realclimate.network;

import com.realclimate.tileentity.ACOutdoorBlockEntity;
import com.realclimate.tileentity.RadiatorBlockEntity;
import com.realclimate.tileentity.ControlPanelBlockEntity;
import com.realclimate.tileentity.ACIndoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketClimateSettings {
    private final BlockPos pos;
    private final boolean enabled;
    private final double targetTemperature;

    public PacketClimateSettings(BlockPos pos, boolean enabled, double targetTemperature) {
        this.pos = pos;
        this.enabled = enabled;
        this.targetTemperature = targetTemperature;
    }

    public static void encode(PacketClimateSettings message, FriendlyByteBuf buf) {
        buf.writeBlockPos(message.pos);
        buf.writeBoolean(message.enabled);
        buf.writeDouble(message.targetTemperature);
    }

    public static PacketClimateSettings decode(FriendlyByteBuf buf) {
        return new PacketClimateSettings(buf.readBlockPos(), buf.readBoolean(), buf.readDouble());
    }

    public static void handle(PacketClimateSettings message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!Double.isFinite(message.targetTemperature)) return;
            if (player.distanceToSqr(message.pos.getX() + 0.5, message.pos.getY() + 0.5, message.pos.getZ() + 0.5) > 36.0) return;

            BlockEntity blockEntity = player.serverLevel().getBlockEntity(message.pos);
            if (blockEntity == null) return;
            if (blockEntity instanceof ACOutdoorBlockEntity outdoor) {
                outdoor.setEnabled(message.enabled);
                outdoor.setTargetTemp(clampHvac(message.targetTemperature));
            } else if (blockEntity instanceof RadiatorBlockEntity radiator) {
                radiator.setEnabled(message.enabled);
                radiator.setTargetTemp(Math.max(20.0, Math.min(90.0, message.targetTemperature)));
            } else if (blockEntity instanceof ControlPanelBlockEntity panel) {
                panel.setEnabled(message.enabled);
                panel.setTargetTemp(clampHvac(message.targetTemperature));
                for (BlockPos outdoorPos : panel.getLinkedOutdoors()) {
                    BlockEntity outdoorEntity = player.serverLevel().getBlockEntity(outdoorPos);
                    if (outdoorEntity instanceof ACOutdoorBlockEntity outdoor
                            && outdoor.getLinkedPanels().contains(message.pos)) {
                        outdoor.setEnabled(message.enabled);
                        outdoor.setTargetTemp(panel.getTargetTemp());
                        for (BlockPos indoorPos : outdoor.getLinkedIndoors()) {
                            if (player.serverLevel().isLoaded(indoorPos)
                                    && player.serverLevel().getBlockEntity(indoorPos) instanceof ACIndoorBlockEntity indoor
                                    && indoor.getLinkedOutdoor() != null
                                    && indoor.getLinkedOutdoor().equals(outdoorPos)) {
                                indoor.setEnabled(message.enabled);
                                indoor.setTargetTemp(panel.getTargetTemp());
                                player.serverLevel().sendBlockUpdated(indoorPos, indoor.getBlockState(), indoor.getBlockState(), 3);
                            }
                        }
                        player.serverLevel().sendBlockUpdated(outdoorPos, outdoor.getBlockState(), outdoor.getBlockState(), 3);
                    }
                }
            } else if (blockEntity instanceof ACIndoorBlockEntity indoor) {
                indoor.setEnabled(message.enabled);
                indoor.setTargetTemp(clampHvac(message.targetTemperature));
            } else {
                return;
            }
            blockEntity.setChanged();
            player.serverLevel().sendBlockUpdated(message.pos, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                double savedTemperature = blockEntity instanceof ACOutdoorBlockEntity outdoor ? outdoor.getTargetTemp()
                    : blockEntity instanceof RadiatorBlockEntity radiator ? radiator.getTargetTemp()
                    : blockEntity instanceof ControlPanelBlockEntity panel ? panel.getTargetTemp()
                    : ((ACIndoorBlockEntity) blockEntity).getTargetTemp();
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    String.format(java.util.Locale.ROOT, "Уставка сохранена: %.1f °C", savedTemperature)), true);
        });
        context.setPacketHandled(true);
    }

    private static double clampHvac(double temperature) {
        return Math.max(-20.0, Math.min(95.0, temperature));
    }
}
