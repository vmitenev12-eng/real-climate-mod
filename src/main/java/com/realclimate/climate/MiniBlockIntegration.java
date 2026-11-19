package com.realclimate.climate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fml.ModList;

import java.util.Optional;

public final class MiniBlockIntegration {
    private static volatile Boolean littleTilesLoaded;

    private MiniBlockIntegration() {}

    public static boolean isLittleTilesLoaded() {
        if (littleTilesLoaded == null) {
            littleTilesLoaded = ModList.get().isLoaded("littletiles")
                    || ModList.get().isLoaded("little_tiles");
        }
        return littleTilesLoaded;
    }

    public static boolean isLikelyMiniBlock(BlockState state) {
        String blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                .getKey(state.getBlock()).toString();
        if (blockId.startsWith("littletiles:") || blockId.startsWith("little_tiles:")) return true;
        String className = state.getBlock().getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return className.contains("littletile") || className.contains("minitile")
                || className.contains("door") || className.contains("window");
    }

    public static double openGapRatio(Level level, BlockPos pos, Direction side) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return 1.0;
        Boolean open = openDoorOrWindow(state);
        if (open != null) return open ? 0.85 : 0.0;
        if (!isLikelyMiniBlock(state)) return level.getFluidState(pos).isEmpty() ? 0.0 : 0.4;
        VoxelShape collision = state.getCollisionShape(level, pos);
        if (collision.isEmpty()) return 1.0;

        final double[] volume = {0.0};
        collision.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                volume[0] += (x2 - x1) * (y2 - y1) * (z2 - z1));

        double occupied = Math.max(0.0, Math.min(1.0, volume[0]));
        return 1.0 - occupied;
    }

    public static boolean allowsAirPassage(Level level, BlockPos pos, Direction movement) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        Boolean open = openDoorOrWindow(state);
        if (open != null) return open;
        if (!isLikelyMiniBlock(state)) return state.getCollisionShape(level, pos).isEmpty();

        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return true;

        final int size = 8;
        boolean[][][] blocked = new boolean[size][size][size];
        java.util.List<AABB> boxes = new java.util.ArrayList<>();
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> boxes.add(new AABB(x1, y1, z1, x2, y2, z2)));
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    double px = (x + 0.5) / size;
                    double py = (y + 0.5) / size;
                    double pz = (z + 0.5) / size;
                    for (AABB box : boxes) {
                        if (px >= box.minX && px <= box.maxX && py >= box.minY && py <= box.maxY
                                && pz >= box.minZ && pz <= box.maxZ) {
                            blocked[x][y][z] = true;
                            break;
                        }
                    }
                }
            }
        }

        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        boolean[][][] visited = new boolean[size][size][size];
        int entryAxis = movement.getAxis() == Direction.Axis.X ? 0
                : movement.getAxis() == Direction.Axis.Y ? 1 : 2;
        int startCoordinate = movement.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : size - 1;
        int endCoordinate = size - 1 - startCoordinate;
        for (int a = 0; a < size; a++) {
            for (int b = 0; b < size; b++) {
                int[] cell = faceCell(entryAxis, startCoordinate, a, b);
                if (!blocked[cell[0]][cell[1]][cell[2]]) {
                    visited[cell[0]][cell[1]][cell[2]] = true;
                    queue.add(cell);
                }
            }
        }

        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            if (cell[entryAxis] == endCoordinate) return true;
            for (Direction direction : Direction.values()) {
                int x = cell[0] + direction.getStepX();
                int y = cell[1] + direction.getStepY();
                int z = cell[2] + direction.getStepZ();
                if (x < 0 || y < 0 || z < 0 || x >= size || y >= size || z >= size
                        || blocked[x][y][z] || visited[x][y][z]) continue;
                visited[x][y][z] = true;
                queue.addLast(new int[]{x, y, z});
            }
        }
        return false;
    }

    private static int[] faceCell(int axis, int coordinate, int a, int b) {
        return switch (axis) {
            case 0 -> new int[]{coordinate, a, b};
            case 1 -> new int[]{a, coordinate, b};
            default -> new int[]{a, b, coordinate};
        };
    }

    private static Boolean openDoorOrWindow(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            String name = property.getName().toLowerCase(java.util.Locale.ROOT);
            if (!(name.equals("open") || name.equals("opened") || name.equals("closed"))) continue;
            Optional<?> value = state.getOptionalValue(property);
            if (value.isPresent()) {
                String text = value.get().toString();
                if (name.equals("closed")) return !(text.equals("true") || text.equals("1"));
                return text.equals("true") || text.equals("1") || text.equals("open");
            }
        }
        return null;
    }
}
