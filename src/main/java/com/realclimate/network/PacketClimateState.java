package com.realclimate.network;

import com.realclimate.climate.BodyTemperature;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент: температура тела, воздух вокруг игрока и утепление одежды.
 * HUD показывает ровно те значения, которые считает сервер, — без расхождений.
 */
public class PacketClimateState {
    private final double body;
    private final double ambient;
    private final double insulation;

    public PacketClimateState(double body, double ambient, double insulation) {
        this.body = body;
        this.ambient = ambient;
        this.insulation = insulation;
    }

    public static void encode(PacketClimateState message, FriendlyByteBuf buf) {
        buf.writeDouble(message.body);
        buf.writeDouble(message.ambient);
        buf.writeDouble(message.insulation);
    }

    public static PacketClimateState decode(FriendlyByteBuf buf) {
        return new PacketClimateState(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(PacketClimateState message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> BodyTemperature.applyClientSync(
                message.body, message.ambient, message.insulation));
        context.setPacketHandled(true);
    }
}
