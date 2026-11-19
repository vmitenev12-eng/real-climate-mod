package com.realclimate.climate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.Shapes;
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
        if (isOpenDoorOrWindow(state)) return 0.85;
        if (!isLikelyMiniBlock(state)) return level.getFluidState(pos).isEmpty() ? 0.0 : 0.4;

        VoxelShape collision = state.getCollisionShape(level, pos);
        if (collision.isEmpty()) return 1.0;
        if (collision == Shapes.block()) return 0.0;

        final double[] volume = {0.0};
        collision.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                volume[0] += (x2 - x1) * (y2 - y1) * (z2 - z1));

        double occupied = Math.max(0.0, Math.min(1.0, volume[0]));
        return 1.0 - occupied;
    }

    private static boolean isOpenDoorOrWindow(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            String name = property.getName().toLowerCase(java.util.Locale.ROOT);
            if (!(name.equals("open") || name.equals("opened") || name.equals("closed"))) continue;
            Optional<?> value = state.getOptionalValue(property);
            if (value.isPresent()) {
                String text = value.get().toString();
                if (name.equals("closed")) return text.equals("false") || text.equals("0");
                return text.equals("true") || text.equals("1") || text.equals("open");
            }
        }
        return false;
    }
}
