package com.realclimate.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class HammerItem extends Item {
    public HammerItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockState state = level.getBlockState(context.getClickedPos());
        if (!state.getBlock().getClass().getName().startsWith("com.realclimate.")) {
            return InteractionResult.PASS;
        }

        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty) {
                BlockState rotated = state.setValue((DirectionProperty) property,
                        state.getValue((DirectionProperty) property).getClockWise());
                level.setBlock(context.getClickedPos(), rotated, 3);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }
}
