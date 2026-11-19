package com.realclimate.block;

import com.realclimate.tileentity.GrilleBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.realclimate.registry.ModBlockEntities;

public class GrilleBlock extends FacingBlock {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    protected static final VoxelShape SHAPE = createGrilleShape();
    private static final VoxelShape OUTLINE_SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0 / 16.0);

    private static VoxelShape createGrilleShape() {
        VoxelShape shape = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0 / 16.0),
            Shapes.box(0.0, 0.0, 15.0 / 16.0, 1.0, 1.0, 1.0),
            Shapes.box(0.0, 0.0, 1.0 / 16.0, 1.0 / 16.0, 1.0, 15.0 / 16.0),
            Shapes.box(15.0 / 16.0, 0.0, 1.0 / 16.0, 1.0, 1.0, 15.0 / 16.0));
        for (double offset : new double[]{0.0, 2.15, 4.3, 6.45, 8.6, 10.7, 12.85, 15.0}) {
            double min = offset / 16.0;
            double max = Math.min(1.0, (offset + 1.0) / 16.0);
            shape = Shapes.or(shape, Shapes.box(min, 0.0, 0.0, max, 0.5 / 16.0, 1.0));
            shape = Shapes.or(shape, Shapes.box(0.0, 0.0, min, 1.0, 0.5 / 16.0, max));
        }
        return shape;
    }

    public GrilleBlock(Properties props) {
        super(props, SHAPE);
        registerDefaultState(stateDefinition.any().setValue(FACING, 0).setValue(OPEN, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, directionToIndex(c.getHorizontalDirection().getOpposite())).setValue(OPEN, true);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return rotateShape(OUTLINE_SHAPE, indexToDirection(s.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState st) { return new GrilleBlockEntity(p, st); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return type == ModBlockEntities.GRILLE.get()
                ? (level1, pos, state1, be) -> GrilleBlockEntity.serverTick(level1, pos, state1, (GrilleBlockEntity) be)
                : null;
    }
}