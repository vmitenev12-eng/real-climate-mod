package com.realclimate.climate;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class VentilationService {
    private static final Map<ResourceKey<Level>, Map<Long, Map<Long, AirFlow>>> SOURCES = new HashMap<>();

    private VentilationService() {}

    public static synchronized void updatePath(Level level, BlockPos source, Map<BlockPos, Double> leakage, double temperature) {
        Map<Long, Map<Long, AirFlow>> dimensionFlows = SOURCES.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        Map<Long, AirFlow> route = new HashMap<>();
        leakage.forEach((pos, loss) -> route.put(pos.asLong(), new AirFlow(temperature, Math.max(0.0, Math.min(2.0, loss)))));
        dimensionFlows.put(source.asLong(), route);
    }

    public static synchronized void removeSource(Level level, BlockPos source) {
        Map<Long, Map<Long, AirFlow>> dimensionFlows = SOURCES.get(level.dimension());
        if (dimensionFlows == null) return;
        dimensionFlows.remove(source.asLong());
        if (dimensionFlows.isEmpty()) SOURCES.remove(level.dimension());
    }

    public static synchronized double temperatureOffset(Level level, BlockPos pos, double ambient) {
        Map<Long, Map<Long, AirFlow>> dimensionFlows = SOURCES.get(level.dimension());
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
        return Math.max(-35.0, Math.min(35.0, offset));
    }

    public static void clear() {
        synchronized (VentilationService.class) {
            SOURCES.clear();
        }
    }

    private record AirFlow(double temperature, double leakage) {}
}
