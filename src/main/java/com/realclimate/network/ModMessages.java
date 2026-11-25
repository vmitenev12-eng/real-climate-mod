package com.realclimate.network;

import com.realclimate.RealClimate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModMessages {
    private static SimpleChannel INSTANCE;
    private static int id = 0;

    public static void register() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(RealClimate.MODID, "main"),
                () -> "1.0",
                s -> true,
                s -> true);
        INSTANCE.messageBuilder(PacketLinkAC.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketLinkAC::encode)
                .decoder(PacketLinkAC::decode)
                .consumerMainThread(PacketLinkAC::handle)
                .add();
        INSTANCE.messageBuilder(PacketClimateSettings.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketClimateSettings::encode)
                .decoder(PacketClimateSettings::decode)
                .consumerMainThread(PacketClimateSettings::handle)
                .add();
        INSTANCE.messageBuilder(PacketClimateState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PacketClimateState::encode)
                .decoder(PacketClimateState::decode)
                .consumerMainThread(PacketClimateState::handle)
                .add();
    }

    public static SimpleChannel get() { return INSTANCE; }

    public static <MSG> void sendToServer(MSG msg) {
        if (INSTANCE != null) INSTANCE.sendToServer(msg);
    }

    public static <MSG> void sendToPlayer(ServerPlayer player, MSG msg) {
        if (INSTANCE != null) INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
