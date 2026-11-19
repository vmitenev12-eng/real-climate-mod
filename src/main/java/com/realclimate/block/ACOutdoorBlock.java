package com.realclimate.block;

import com.realclimate.tileentity.ACOutdoorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.realclimate.registry.ModBlockEntities;

public class ACOutdoorBlock extends FacingBlock {
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    protected static final VoxelShape SHAPE = createModelShape();
    private static final VoxelShape OUTLINE_SHAPE = Shapes.box(0.0, 2.0 / 16.0, 3.0 / 16.0, 1.0, 1.0, 1.0);

    private static VoxelShape createModelShape() {
        double[][] parts = {
                    {16, 2, 7, 16, 16, 16}, {0, 2, 7, 0, 16, 16}, {0, 2, 16, 16, 16, 16},
                    {0, 2, 11, 16, 16, 11}, {0, 2, 7, 16, 2, 16}, {0, 16, 7, 16, 16, 16},
                    {11, 15, 3, 11, 16, 3}, {12, 14, 7, 16, 16, 7}, {12, 11, 7, 16, 13, 7},
                    {12, 8, 7, 16, 10, 7}, {12, 5, 7, 16, 7, 7}, {12, 2, 7, 16, 4, 7},
                    {0, 15, 7, 12, 16, 7}, {0, 3, 7, 1, 15, 7}, {12, 4, 7, 13, 14, 7},
                    {15, 4, 7, 16, 14, 7}, {0, 2, 7, 12, 3, 7}, {1, 3, 6, 2, 15, 7},
                    {3, 3, 6, 4, 15, 7}, {5, 3, 6, 6, 15, 7}, {7, 3, 6, 8, 15, 7},
                    {9, 3, 6, 10, 15, 7}, {11, 3, 6, 12, 15, 7}, {1, 14, 6, 12, 15, 7},
                    {1, 3, 6, 12, 4, 7}, {4.5, 7.5, 8, 6.5, 9.5, 9}, {4.5, 7.5, 9, 6.5, 9.5, 15},
                    {5, 4.5, 8.3, 6, 13.5, 8.7}, {1.5, 8, 8.3, 10, 9, 8.7}
        };
        VoxelShape shape = Shapes.empty();
        for (double[] part : parts) {
            double minX = part[0], minY = part[1], minZ = part[2];
            double maxX = part[3], maxY = part[4], maxZ = part[5];
            if (minX == maxX) { minX = Math.max(0, minX - 0.5); maxX = Math.min(16, maxX + 0.5); }
            if (minY == maxY) { minY = Math.max(0, minY - 0.5); maxY = Math.min(16, maxY + 0.5); }
            if (minZ == maxZ) { minZ = Math.max(0, minZ - 0.5); maxZ = Math.min(16, maxZ + 0.5); }
            shape = Shapes.or(shape, Shapes.box(minX / 16.0, minY / 16.0, minZ / 16.0,
                    maxX / 16.0, maxY / 16.0, maxZ / 16.0));
        }
        return shape;
    }

    public ACOutdoorBlock(Properties props) {
        super(props, SHAPE);
        registerDefaultState(stateDefinition.any().setValue(FACING, 0).setValue(CONNECTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, CONNECTED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, directionToIndex(c.getHorizontalDirection().getOpposite())).setValue(CONNECTED, false);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return rotateShape(OUTLINE_SHAPE, indexToDirection(s.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState st) { return new ACOutdoorBlockEntity(p, st); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.AC_OUTDOOR.get()) return null;
        return level.isClientSide
                ? (level1, pos, state1, be) -> ACOutdoorBlockEntity.clientTick(level1, pos, (ACOutdoorBlockEntity) be)
                : (level1, pos, state1, be) -> ACOutdoorBlockEntity.serverTick(level1, pos, state1, (ACOutdoorBlockEntity) be);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                InteractionHand hand, BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND
                && !player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ACOutdoorBlockEntity) return level.isClientSide
                    ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moving) {
        if (!s.is(ns.getBlock())) {
            BlockEntity be = l.getBlockEntity(p);
            if (be instanceof ACOutdoorBlockEntity ae) ae.onRemove();
        }
        super.onRemove(s, l, p, ns, moving);
    }
}
