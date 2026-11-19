package com.realclimate.block;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
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
    public static final IntegerProperty FACING = IntegerProperty.create("facing", 0, 3);
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
        return defaultBlockState().setValue(FACING, directionToIndex(c.getHorizontalDirection().getOpposite()));
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        Direction direction = r.rotate(indexToDirection(s.getValue(FACING)));
        return s.setValue(FACING, directionToIndex(direction));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        Direction direction = m.mirror(indexToDirection(s.getValue(FACING)));
        return s.setValue(FACING, directionToIndex(direction));
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return rotateShape(shape, indexToDirection(s.getValue(FACING)));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return rotateShape(shape, indexToDirection(state.getValue(FACING)));
    }

    public static int directionToIndex(Direction direction) {
        return switch (direction) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
    }

    public static Direction indexToDirection(int index) {
        return switch (index) { case 1 -> Direction.EAST; case 2 -> Direction.SOUTH; case 3 -> Direction.WEST; default -> Direction.NORTH; };
    }

    public static VoxelShape rotateShape(VoxelShape shape, Direction facing) {
        synchronized (ROTATED_SHAPES) {
            EnumMap<Direction, VoxelShape> cache = ROTATED_SHAPES.get(shape);
            if (cache == null) {
                cache = new EnumMap<>(Direction.class);
                cache.put(Direction.NORTH, shape);
                ROTATED_SHAPES.put(shape, cache);
            }
            return cache.computeIfAbsent(facing, direction -> rotateShapeUncached(shape, direction));
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
