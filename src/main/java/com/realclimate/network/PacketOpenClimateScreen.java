package com.realclimate.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketOpenClimateScreen {
    private final BlockPos pos;
    private final boolean enabled;
    private final double targetTemperature;

    public PacketOpenClimateScreen(BlockPos pos, boolean enabled, double targetTemperature) {
        this.pos = pos;
        this.enabled = enabled;
        this.targetTemperature = targetTemperature;
    }

    public static void encode(PacketOpenClimateScreen message, FriendlyByteBuf buf) {
        buf.writeBlockPos(message.pos);
        buf.writeBoolean(message.enabled);
        buf.writeDouble(message.targetTemperature);
    }

    public static PacketOpenClimateScreen decode(FriendlyByteBuf buf) {
        return new PacketOpenClimateScreen(buf.readBlockPos(), buf.readBoolean(), buf.readDouble());
    }

    public static void handle(PacketOpenClimateScreen message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> openScreen(message));
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void openScreen(PacketOpenClimateScreen message) {
        Minecraft.getInstance().setScreen(new com.realclimate.client.ClimateSettingsScreen(
                message.pos, message.enabled, message.targetTemperature));
    }
}
