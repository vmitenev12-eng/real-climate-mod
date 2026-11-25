package com.realclimate.climate;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class VentilationService {
    private static final Map<WorldKey, Map<Long, Map<Long, AirFlow>>> SOURCES = new HashMap<>();

    private VentilationService() {}

    public static synchronized void updatePath(Level level, BlockPos source, Map<BlockPos, Double> leakage, double temperature) {
        Map<Long, Map<Long, AirFlow>> dimensionFlows = SOURCES.computeIfAbsent(worldKey(level), key -> new HashMap<>());
        Map<Long, AirFlow> route = new HashMap<>();
        leakage.forEach((pos, loss) -> route.put(pos.asLong(), new AirFlow(temperature, Math.max(0.0, Math.min(2.0, loss)))));
        dimensionFlows.put(source.asLong(), route);
    }

    public static synchronized void removeSource(Level level, BlockPos source) {
        Map<Long, Map<Long, AirFlow>> dimensionFlows = SOURCES.get(worldKey(level));
        if (dimensionFlows == null) return;
        dimensionFlows.remove(source.asLong());
        if (dimensionFlows.isEmpty()) SOURCES.remove(worldKey(level));
    }

    public static synchronized boolean hasActiveAirflow(Level level, BlockPos grillePos) {
        Map<Long, Map<Long, AirFlow>> dimensionFlows = SOURCES.get(worldKey(level));
        if (dimensionFlows == null) return false;
        long packedPos = grillePos.asLong();
        for (Map<Long, AirFlow> route : dimensionFlows.values()) {
            if (route.containsKey(packedPos)) return true;
        }
        return false;
    }

    public static synchronized double airflowTemperature(Level level, BlockPos pos, double fallback) {
        Map<Long, Map<Long, AirFlow>> dimensionFlows = SOURCES.get(worldKey(level));
        if (dimensionFlows == null) return fallback;

        double total = 0.0;
        int sources = 0;
        long packedPos = pos.asLong();
        for (Map<Long, AirFlow> route : dimensionFlows.values()) {
            AirFlow flow = route.get(packedPos);
            if (flow != null) {
                total += flow.temperature;
                sources++;
            }
        }
        return sources == 0 ? fallback : total / sources;
    }

    /**
     * Сколько решёток обслуживается теми же вентиляционными магистралями, что и
     * решётка в точке pos. Мощность системы делится между ними поровну: несколько
     * решёток в одной системе не должны складывать температуру друг с другом.
     */
    public static synchronized int grilleSystemSize(Level level, BlockPos pos) {
        Map<Long, Map<Long, AirFlow>> dimensionFlows = SOURCES.get(worldKey(level));
        if (dimensionFlows == null) return 1;
        long packedPos = pos.asLong();
        int count = 0;
        for (Map<Long, AirFlow> route : dimensionFlows.values()) {
            if (!route.containsKey(packedPos)) continue;
            for (long cell : route.keySet()) {
                BlockPos cellPos = BlockPos.of(cell);
                if (!level.isLoaded(cellPos)) continue;
                net.minecraft.resources.ResourceLocation id =
                        net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(cellPos).getBlock());
                if (id != null && id.getNamespace().equals("real_climate")
                        && (id.getPath().equals("grille_block") || id.getPath().equals("vent_ceiling_grille"))) {
                    count++;
                }
            }
        }
        return Math.max(1, count);
    }

    public static synchronized double temperatureOffset(Level level, BlockPos pos, double ambient) {
        Map<Long, Map<Long, AirFlow>> dimensionFlows = SOURCES.get(worldKey(level));
        if (dimensionFlows == null) return 0.0;

        double offset = 0.0;
        Set<Long> checked = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos sample = pos.offset(dx, dy, dz);
                    if (!checked.add(sample.asLong())) continue;
                    for (Map<Long, AirFlow> route : dimensionFlows.values()) {
                        AirFlow flow = route.get(sample.asLong());
                        if (flow == null || flow.leakage <= 0.0) continue;
                        double distanceFactor = 1.0 - (Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) * 0.12;
                        double openness = flow.leakage >= 1.5 ? 0.8 : 0.2 * flow.leakage;
                        offset += (flow.temperature - ambient) * openness * Math.max(0.0, distanceFactor);
                    }
                }
            }
        }
        return Math.max(-100.0, Math.min(100.0, offset));
    }

    public static void clear() {
        synchronized (VentilationService.class) {
            SOURCES.clear();
        }
    }

    public static synchronized void clear(Level level) {
        SOURCES.remove(worldKey(level));
    }

    private static WorldKey worldKey(Level level) {
        return new WorldKey(level.dimension(), level.isClientSide);
    }

    private record WorldKey(ResourceKey<Level> dimension, boolean clientSide) {}
    private record AirFlow(double temperature, double leakage) {}
}
