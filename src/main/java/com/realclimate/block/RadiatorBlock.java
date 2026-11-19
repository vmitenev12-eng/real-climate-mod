package com.realclimate.block;

import com.realclimate.tileentity.RadiatorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.realclimate.registry.ModBlockEntities;

public class RadiatorBlock extends FacingBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    protected static final VoxelShape SHAPE = Shapes.or(
            Shapes.box(1.0 / 16.0, 1.0 / 16.0, 12.0 / 16.0, 3.0 / 16.0, 15.0 / 16.0, 15.0 / 16.0),
            Shapes.box(4.0 / 16.0, 1.0 / 16.0, 12.0 / 16.0, 6.0 / 16.0, 15.0 / 16.0, 15.0 / 16.0),
            Shapes.box(7.0 / 16.0, 1.0 / 16.0, 12.0 / 16.0, 9.0 / 16.0, 15.0 / 16.0, 15.0 / 16.0),
            Shapes.box(10.0 / 16.0, 1.0 / 16.0, 12.0 / 16.0, 12.0 / 16.0, 15.0 / 16.0, 15.0 / 16.0),
            Shapes.box(13.0 / 16.0, 1.0 / 16.0, 12.0 / 16.0, 15.0 / 16.0, 15.0 / 16.0, 15.0 / 16.0),
            Shapes.box(1.0 / 16.0, 12.0 / 16.0, 13.0 / 16.0, 15.0 / 16.0, 13.0 / 16.0, 14.0 / 16.0),
            Shapes.box(1.0 / 16.0, 3.0 / 16.0, 13.0 / 16.0, 15.0 / 16.0, 4.0 / 16.0, 14.0 / 16.0),
            Shapes.box(14.0 / 16.0, 11.0 / 16.0, 11.0 / 16.0, 1.0, 14.0 / 16.0, 13.0 / 16.0),
            Shapes.box(15.0 / 16.0, 11.0 / 16.0, 10.0 / 16.0, 1.0, 14.0 / 16.0, 11.0 / 16.0),
            Shapes.box(14.0 / 16.0, 11.0 / 16.0, 10.0 / 16.0, 15.0 / 16.0, 14.0 / 16.0, 11.0 / 16.0));

    public RadiatorBlock(Properties props) {
        super(props, SHAPE);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()).setValue(ACTIVE, false);
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }

    @Override
    public BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return rotateShape(SHAPE, s.getValue(FACING));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                InteractionHand hand, BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND
                && !player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RadiatorBlockEntity) return level.isClientSide
                    ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState st) { return new RadiatorBlockEntity(p, st); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return type == ModBlockEntities.RADIATOR.get()
                ? (level1, pos, state1, be) -> RadiatorBlockEntity.serverTick(level1, pos, state1, (RadiatorBlockEntity) be)
                : null;
    }
}
