package com.realclimate.network;

import com.realclimate.tileentity.ACOutdoorBlockEntity;
import com.realclimate.tileentity.ACIndoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;

import java.util.function.Supplier;

public class PacketLinkAC {
    private final BlockPos outdoor;
    private final int indoorHash;
    private final boolean unlinkAll;

    public PacketLinkAC(BlockPos outdoor, int indoorHash, boolean unlinkAll) {
        this.outdoor = outdoor;
        this.indoorHash = indoorHash;
        this.unlinkAll = unlinkAll;
    }

    public static void encode(PacketLinkAC msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.outdoor);
        buf.writeInt(msg.indoorHash);
        buf.writeBoolean(msg.unlinkAll);
    }

    public static PacketLinkAC decode(FriendlyByteBuf buf) {
        return new PacketLinkAC(buf.readBlockPos(), buf.readInt(), buf.readBoolean());
    }

    public static void handle(PacketLinkAC msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            net.minecraft.server.level.ServerLevel level = player.serverLevel();
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(msg.outdoor);
            if (be instanceof ACOutdoorBlockEntity outdoor) {
                if (msg.unlinkAll) {
                    outdoor.unlinkAll();
                } else {
                    for (net.minecraft.core.BlockPos ip : new java.util.ArrayList<>(outdoor.getLinkedIndoors())) {
                        net.minecraft.world.level.block.entity.BlockEntity ibe = level.getBlockEntity(ip);
                        if (ibe instanceof ACIndoorBlockEntity indoor) {
                            if (indoor.hashCode() == msg.indoorHash) {
                                outdoor.unlinkIndoor(ip);
                                indoor.setLinkedOutdoor(null);
                                break;
                            }
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
