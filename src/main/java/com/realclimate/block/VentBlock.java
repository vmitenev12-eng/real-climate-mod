package com.realclimate.block;

import com.realclimate.tileentity.VentBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.realclimate.registry.ModBlockEntities;

public class VentBlock extends FacingBlock {
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");
    protected static final VoxelShape SHAPE = Shapes.box(0.25, 0.0, 0.25, 0.75, 1.0, 0.75);

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

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return type == ModBlockEntities.VENT.get()
                ? (level1, pos, state1, be) -> VentBlockEntity.serverTick(level1, pos, state1, (VentBlockEntity) be)
                : null;
    }

    public static boolean isVent(BlockGetter g, BlockPos p) {
        return g.getBlockState(p).getBlock() instanceof VentBlock || g.getBlockState(p).getBlock() instanceof GrilleBlock;
    }

    public static boolean isAirOrVent(Level l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return s.isAir() || s.is(BlockTags.LEAVES) || s.getBlock() instanceof VentBlock || s.getBlock() instanceof GrilleBlock;
    }
}
