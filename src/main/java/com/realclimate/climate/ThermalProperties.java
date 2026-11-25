package com.realclimate.climate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Теплосвойства блоков.
 *
 * Теплопроводность λ берётся из JSON-переопределений
 * (data/<namespace>/real_climate/thermal/имя.json, ключи — полные ID блоков любых модов),
 * затем из эвристик по тегам и ID, иначе — типовое значение для кладки.
 *
 * Толщина материала считается по форме коллизии блока: цельный блок кирпича
 * ведёт себя как метр кладки, а стенка из миниблоков (LittleTiles и подобных)
 * толщиной 1/32 метра вносит сопротивление (1/32)/λ. Весь сэндвич ограждения
 * (кирпич + утеплитель + сайдинг из миниблоков) считается лучом: сопротивления
 * слоёв складываются, как в реальной стене.
 * Ни исходники Minecraft, ни код других модов не требуются — только состояние мира.
 */
public final class ThermalProperties {
    /** Неизвестный кладочный материал, Вт/(м·К). */
    public static final double DEFAULT_CONDUCTIVITY = 1.0;

    /** На сколько блоков вглубь ограждения смотрит луч сэндвича. */
    private static final int MAX_SANDWICH_DEPTH = 8;

    private static final Map<ResourceLocation, Double> OVERRIDES = new ConcurrentHashMap<>();

    private ThermalProperties() {}

    public static void setOverrides(Map<ResourceLocation, Double> map) {
        OVERRIDES.clear();
        OVERRIDES.putAll(map);
    }

    /** Теплопроводность λ блока, Вт/(м·К). */
    public static double conductivity(BlockState state) {
        Block block = state.getBlock();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null) return DEFAULT_CONDUCTIVITY;
        Double override = OVERRIDES.get(id);
        if (override != null) return override;
        return heuristic(id, state);
    }

    /**
     * Теплопроводность грани блока в заданном направлении (от помещения наружу),
     * Вт/К на грань: площадь проёма / полное сопротивление сэндвича R = Σ толщина_i / λ_i.
     *
     * Луч идёт от самого внутреннего слоя (со стороны помещения — воздух) через
     * все следующие сплошные слои, пока не встретит воздух. Внешние слои сэндвича
     * возвращают 0: их вклад уже учтён лучом от внутреннего слоя, и otherwise
     * каждый слой посчитался бы дважды. Для пустой коллизии (открытые двери,
     * решётки) — 0.
     */
    public static double faceConductance(Level level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return 0.0;
        List<AABB> boxes = shape.toAabbs();
        if (boxes.isEmpty()) return 0.0;

        // Сэндвич считаем один раз — от внутреннего слоя.
        BlockPos roomSide = pos.relative(direction.getOpposite());
        if (!level.getBlockState(roomSide).getCollisionShape(level, roomSide).isEmpty()) return 0.0;

        Direction.Axis axis = direction.getAxis();

        double area = 0.0;
        for (AABB box : boxes) {
            double w = axis == Direction.Axis.X ? box.maxZ - box.minZ : box.maxX - box.minX;
            double h = axis == Direction.Axis.Y ? box.maxZ - box.minZ : box.maxY - box.minY;
            area += w * h;
        }
        area = Math.max(0.0, Math.min(1.0, area));
        if (area <= 0.0) return 0.0;

        double resistance = 0.0;
        BlockPos cursor = pos;
        for (int layer = 0; layer < MAX_SANDWICH_DEPTH; layer++) {
            BlockState layerState = layer == 0 ? state : level.getBlockState(cursor);
            VoxelShape layerShape = layerState.getCollisionShape(level, cursor);
            if (layerShape.isEmpty()) break; // вышли наружу — за воздушной прослойкой луч не идёт
            double thickness = 0.0;
            for (AABB box : layerShape.toAabbs()) {
                double extent = switch (axis) {
                    case X -> box.maxX - box.minX;
                    case Y -> box.maxY - box.minY;
                    default -> box.maxZ - box.minZ;
                };
                thickness = Math.max(thickness, extent);
            }
            if (thickness <= 0.001) break;
            resistance += thickness / conductivity(layerState);
            cursor = cursor.relative(direction);
        }
        if (resistance <= 0.0) return 0.0;
        return area / resistance;
    }

    /** Эвристики по тегам и ID блока: типовые λ стройматериалов. */
    private static double heuristic(ResourceLocation id, BlockState state) {
        if (state.is(BlockTags.WOOL)) return 0.04;                 // шерсть / утеплитель
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS)) return 0.13; // дерево
        if (state.is(BlockTags.LEAVES)) return 0.06;               // листва
        if (state.is(BlockTags.ICE)) return 2.2;                   // лёд
        if (state.is(BlockTags.SAND)) return 0.35;                 // сухой песок

        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("snow")) return 0.10;                    // снег
        if (path.contains("glass")) return 0.8;                    // стекло (толщину считает форма)
        if (path.contains("brick")) return 0.77;                   // кирпичная кладка
        if (path.contains("terracotta") || path.contains("hardened_clay")) return 0.75;
        if (path.contains("iron") || path.contains("steel") || path.contains("copper")
                || path.contains("metal") || path.contains("gold") || path.contains("chain")) return 45.0;
        if (path.contains("stone") || path.contains("deepslate") || path.contains("cobble")
                || path.contains("andesite") || path.contains("diorite") || path.contains("granite")
                || path.contains("calcite") || path.contains("tuff")) return 1.7;
        if (path.contains("concrete")) return 1.5;
        if (path.contains("dirt") || path.contains("clay") || path.contains("gravel")
                || path.contains("mud") || path.contains("grass_block")) return 0.5;
        if (path.contains("wood") || path.contains("plank") || path.contains("log")) return 0.13;
        // Миниблоки LittleTiles: по умолчанию считаем типовой кладкой,
        // точные значения задаются JSON-переопределениями.
        if (id.getNamespace().equals("littletiles") || id.getNamespace().equals("little_tiles")) return 0.77;
        return DEFAULT_CONDUCTIVITY;
    }
}
