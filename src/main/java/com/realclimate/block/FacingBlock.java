package com.realclimate.block;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.AABB;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class FacingBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    private static final Map<VoxelShape, EnumMap<Direction, VoxelShape>> ROTATED_SHAPES = new IdentityHashMap<>();
    protected final VoxelShape shape;

    public FacingBlock(Properties props) {
        this(props, Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0 / 16.0, 1.0),
            Shapes.box(0.0, 15.0 / 16.0, 0.0, 1.0, 1.0, 1.0),
            Shapes.box(0.0, 1.0 / 16.0, 0.0, 1.0 / 16.0, 15.0 / 16.0, 1.0),
            Shapes.box(15.0 / 16.0, 1.0 / 16.0, 0.0, 1.0, 15.0 / 16.0, 1.0)));
    }

    public FacingBlock(Properties props, VoxelShape shape) {
        super(props);
        this.shape = shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }

    @Override
    public BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return rotateShape(shape, s.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return rotateShape(shape, state.getValue(FACING));
    }

    public static VoxelShape rotateShape(VoxelShape shape, Direction facing) {
        synchronized (ROTATED_SHAPES) {
            EnumMap<Direction, VoxelShape> cache = ROTATED_SHAPES.get(shape);
            if (cache == null) {
                cache = new EnumMap<>(Direction.class);
                cache.put(Direction.NORTH, shape);
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    if (direction != Direction.NORTH) cache.put(direction, rotateShapeUncached(shape, direction));
                }
                ROTATED_SHAPES.put(shape, cache);
            }
            return cache.get(facing);
        }
    }

    private static VoxelShape rotateShapeUncached(VoxelShape shape, Direction facing) {
        VoxelShape rotated = Shapes.empty();
        for (AABB box : shape.toAabbs()) {
            double minX = box.minX;
            double maxX = box.maxX;
            double minZ = box.minZ;
            double maxZ = box.maxZ;
            double x1;
            double x2;
            double z1;
            double z2;
            switch (facing) {
                case EAST -> { x1 = 1.0 - maxZ; x2 = 1.0 - minZ; z1 = minX; z2 = maxX; }
                case SOUTH -> { x1 = 1.0 - maxX; x2 = 1.0 - minX; z1 = 1.0 - maxZ; z2 = 1.0 - minZ; }
                case WEST -> { x1 = minZ; x2 = maxZ; z1 = 1.0 - maxX; z2 = 1.0 - minX; }
                default -> { x1 = minX; x2 = maxX; z1 = minZ; z2 = maxZ; }
            }
            rotated = Shapes.or(rotated, Shapes.box(x1, box.minY, z1, x2, box.maxY, z2));
        }
        return rotated;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState st) { return null; }
}
