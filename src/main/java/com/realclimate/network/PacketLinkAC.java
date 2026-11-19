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
    private final BlockPos indoor;
    private final boolean add;
    private final boolean unlinkAll;

    public PacketLinkAC(BlockPos outdoor, BlockPos indoor, boolean add, boolean unlinkAll) {
        this.outdoor = outdoor;
        this.indoor = indoor;
        this.add = add;
        this.unlinkAll = unlinkAll;
    }

    public static PacketLinkAC unlinkOne(BlockPos outdoor, BlockPos indoor) {
        return new PacketLinkAC(outdoor, indoor, false, false);
    }

    public static PacketLinkAC unlinkAll(BlockPos outdoor) {
        return new PacketLinkAC(outdoor, BlockPos.ZERO, false, true);
    }

    public static void encode(PacketLinkAC msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.outdoor);
        buf.writeBlockPos(msg.indoor);
        buf.writeBoolean(msg.add);
        buf.writeBoolean(msg.unlinkAll);
    }

    public static PacketLinkAC decode(FriendlyByteBuf buf) {
        return new PacketLinkAC(buf.readBlockPos(), buf.readBlockPos(), buf.readBoolean(), buf.readBoolean());
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
                } else if (msg.add) {
                    if (outdoor.linkIndoor(msg.indoor)) {
                        net.minecraft.world.level.block.entity.BlockEntity ibe = level.getBlockEntity(msg.indoor);
                        if (ibe instanceof ACIndoorBlockEntity indoor) indoor.setLinkedOutdoor(msg.outdoor);
                    }
                } else {
                    outdoor.unlinkIndoor(msg.indoor);
                    net.minecraft.world.level.block.entity.BlockEntity ibe = level.getBlockEntity(msg.indoor);
                    if (ibe instanceof ACIndoorBlockEntity indoor) indoor.setLinkedOutdoor(null);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
