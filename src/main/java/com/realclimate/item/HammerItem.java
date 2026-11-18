package com.realclimate.item;

import com.realclimate.RealClimate;
import com.realclimate.block.FacingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** Молоточек: ПКМ по блоку мода поворачивает его по часовой, с Shift — против часовой. */
public class HammerItem extends Item {
    public HammerItem(Properties props) { super(props); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        var key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key == null || !RealClimate.MODID.equals(key.getNamespace()) || !state.hasProperty(FacingBlock.FACING)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Direction cur = state.getValue(FacingBlock.FACING);
            boolean back = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown();
            Direction next = back ? cur.getCounterClockWise() : cur.getClockWise();
            level.setBlock(pos, state.setValue(FacingBlock.FACING, next), 3);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
