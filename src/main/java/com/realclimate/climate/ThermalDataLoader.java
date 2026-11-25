package com.realclimate.climate;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/**
 * Загружает переопределения теплопроводности из data/*/real_climate/thermal/*.json.
 * Формат файла: {"modid:block_path": 0.77, ...} — полные ID блоков любых модов
 * (vanilla, hospitals, ptm2, LittleTiles...). Файлы из всех модов и датапаков
 * объединяются, климатический мод остаётся без жёстких зависимостей.
 */
public final class ThermalDataLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();

    public ThermalDataLoader() {
        super(GSON, "real_climate/thermal");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Double> result = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> file : data.entrySet()) {
            if (!file.getValue().isJsonObject()) continue;
            JsonObject object = file.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                ResourceLocation blockId = ResourceLocation.tryParse(entry.getKey());
                if (blockId == null || !entry.getValue().isJsonPrimitive()) continue;
                double value = entry.getValue().getAsDouble();
                if (value > 0.0 && Double.isFinite(value)) result.put(blockId, value);
            }
        }
        ThermalProperties.setOverrides(result);
    }
}
