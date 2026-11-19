package com.realclimate.block;

import com.realclimate.tileentity.VentBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.tags.BlockTags;

public class VentBlock extends FacingBlock implements EntityBlock {
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");
    protected static final VoxelShape SHAPE = Shapes.block();

    public VentBlock(Properties props) {
        super(props, SHAPE);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(SEALED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, SEALED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()).setValue(SEALED, false);
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }

    @Override
    public BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState st) { return new VentBlockEntity(p, st); }

    public static boolean isVent(BlockGetter g, BlockPos p) {
        return g.getBlockState(p).getBlock() instanceof VentBlock || g.getBlockState(p).getBlock() instanceof GrilleBlock;
    }

    public static boolean isAirOrVent(Level l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return s.isAir() || s.is(BlockTags.LEAVES) || s.getBlock() instanceof VentBlock || s.getBlock() instanceof GrilleBlock;
    }
}
