package com.realclimate.network;

import com.realclimate.RealClimate;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModMessages {
    private static SimpleChannel INSTANCE;
    private static int id = 0;

    public static void register() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                RealClimate.MODID,
                () -> "1.0",
                s -> true,
                s -> true);
        INSTANCE.messageBuilder(PacketLinkAC.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketLinkAC::encode)
                .decoder(PacketLinkAC::decode)
                .consumer(PacketLinkAC::handle)
                .add();
    }

    public static SimpleChannel get() { return INSTANCE; }

    public static <MSG> void sendToServer(MSG msg) {
        if (INSTANCE != null) INSTANCE.sendToServer(msg);
    }
}