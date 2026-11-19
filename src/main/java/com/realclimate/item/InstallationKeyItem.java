package com.realclimate.item;

import com.realclimate.tileentity.ACIndoorBlockEntity;
import com.realclimate.tileentity.ACOutdoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class InstallationKeyItem extends Item {
    private static final String OUTDOOR_X = "realclimate_outdoor_x";
    private static final String OUTDOOR_Y = "realclimate_outdoor_y";
    private static final String OUTDOOR_Z = "realclimate_outdoor_z";

    public InstallationKeyItem(Properties props) { super(props); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);

        if (player != null && player.isShiftKeyDown()) {
            if (be instanceof ACOutdoorBlockEntity outdoor) {
                outdoor.unlinkAll();
                player.displayClientMessage(Component.literal("Связи наружного блока сброшены"), true);
            } else if (be instanceof ACIndoorBlockEntity indoor && indoor.getLinkedOutdoor() != null) {
                BlockPos outdoorPos = indoor.getLinkedOutdoor();
                BlockEntity outdoorBe = level.getBlockEntity(outdoorPos);
                if (outdoorBe instanceof ACOutdoorBlockEntity outdoor) outdoor.unlinkIndoor(pos);
                indoor.setLinkedOutdoor(null);
                player.displayClientMessage(Component.literal("Связь внутреннего блока разорвана"), true);
            }
            clearSelection(stack);
            return InteractionResult.CONSUME;
        }

        if (be instanceof ACOutdoorBlockEntity outdoor) {
            setSelection(stack, pos);
            if (player != null) player.displayClientMessage(Component.literal("Наружный блок выбран. Выберите внутренний"), true);
            return InteractionResult.CONSUME;
        }

        if (be instanceof ACIndoorBlockEntity indoor) {
            BlockPos outdoorPos = getSelection(stack);
            if (outdoorPos == null) {
                if (player != null) player.displayClientMessage(Component.literal("Сначала ПКМ по наружному блоку кондиционера"), true);
                return InteractionResult.CONSUME;
            }
            BlockEntity outdoorBe = level.getBlockEntity(outdoorPos);
            if (!(outdoorBe instanceof ACOutdoorBlockEntity outdoor)) {
                clearSelection(stack);
                if (player != null) player.displayClientMessage(Component.literal("Наружный блок не найден"), true);
                return InteractionResult.CONSUME;
            }
            if (outdoor.linkIndoor(pos)) {
                indoor.setLinkedOutdoor(outdoorPos);
                outdoor.setChanged();
                indoor.setChanged();
                if (player != null) player.displayClientMessage(Component.literal("Кондиционеры связаны"), true);
            } else {
                if (player != null) player.displayClientMessage(Component.literal("Максимум: 3 внутренних блока"), true);
            }
            clearSelection(stack);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    private static void setSelection(ItemStack stack, BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(OUTDOOR_X, pos.getX());
        tag.putInt(OUTDOOR_Y, pos.getY());
        tag.putInt(OUTDOOR_Z, pos.getZ());
    }

    private static BlockPos getSelection(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(OUTDOOR_X)) return null;
        return new BlockPos(tag.getInt(OUTDOOR_X), tag.getInt(OUTDOOR_Y), tag.getInt(OUTDOOR_Z));
    }

    private static void clearSelection(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(OUTDOOR_X);
        tag.remove(OUTDOOR_Y);
        tag.remove(OUTDOOR_Z);
    }
}
