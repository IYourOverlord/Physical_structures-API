package org.exampl.physical_structures.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Rotation;
import org.exampl.physical_structures.PhysicalStructures;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Reads data/<ns>/physical_structures/<name>.json on load and /reload. */
public class PhysicalStructureJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();
    private final Set<ResourceLocation> loadedIds = new HashSet<>();
    private final Set<ResourceLocation> loadedSetIds = new HashSet<>();

    public PhysicalStructureJsonLoader() { super(GSON, "physical_structures"); }

    @Override
    protected void apply(Map<ResourceLocation, com.google.gson.JsonElement> objects,
                         ResourceManager manager, ProfilerFiller profiler) {
        PhysicalStructureRegistry.clearIds(loadedIds);
        PhysicalStructureRegistry.clearSetIds(loadedSetIds);
        loadedIds.clear();
        loadedSetIds.clear();

        int count = 0;
        int setCount = 0;
        for (var entry : objects.entrySet()) {
            try {
                JsonObject obj = entry.getValue().getAsJsonObject();
                ResourceLocation id = ResourceLocation.parse(obj.get("id").getAsString());

                if (obj.has("parts")) {
                    Rotation rot = Rotation.NONE;
                    if (obj.has("rotation")) rot = parseRotation(obj.get("rotation").getAsString());
                    int delay = 0;
                    if (obj.has("assemble_delay_ticks")) delay = obj.get("assemble_delay_ticks").getAsInt();

                    java.util.List<StructurePart> parts = new java.util.ArrayList<>();
                    for (var partElem : obj.getAsJsonArray("parts")) {
                        JsonObject partObj = partElem.getAsJsonObject();
                        ResourceLocation partNbt = ResourceLocation.parse(partObj.get("nbt_location").getAsString());
                        var offsetArr = partObj.getAsJsonArray("offset");
                        net.minecraft.core.Vec3i offset = new net.minecraft.core.Vec3i(
                                offsetArr.get(0).getAsInt(),
                                offsetArr.get(1).getAsInt(),
                                offsetArr.get(2).getAsInt());
                        PhysicalStructureDefinition partDef = new PhysicalStructureDefinition(id, partNbt);
                        parts.add(new StructurePart(partDef, offset));
                    }

                    PhysicalStructureSet set = new PhysicalStructureSet(id, parts, rot, delay);
                    PhysicalStructureRegistry.registerOrReplaceSet(set);
                    loadedSetIds.add(id);
                    setCount++;
                    continue;
                }

                ResourceLocation nbt = ResourceLocation.parse(obj.get("nbt_location").getAsString());
                Rotation rot = Rotation.NONE;
                if (obj.has("rotation")) rot = parseRotation(obj.get("rotation").getAsString());
                int delay = 0;
                if (obj.has("assemble_delay_ticks")) delay = obj.get("assemble_delay_ticks").getAsInt();
                PhysicalStructureDefinition def = new PhysicalStructureDefinition(id, nbt, rot, delay);
                PhysicalStructureRegistry.registerOrReplace(def);
                loadedIds.add(id);
                count++;
            } catch (Exception e) {
                PhysicalStructures.LOGGER.error("[PhysicalStructures] Bad JSON '{}': {}",
                        entry.getKey(), e.getMessage());
            }
        }
        PhysicalStructures.LOGGER.info("[PhysicalStructures] Loaded {} JSON definition(s), {} set(s).", count, setCount);
    }

    private static Rotation parseRotation(String s) {
        return switch (s.toLowerCase()) {
            case "clockwise_90"        -> Rotation.CLOCKWISE_90;
            case "counterclockwise_90" -> Rotation.COUNTERCLOCKWISE_90;
            case "clockwise_180"       -> Rotation.CLOCKWISE_180;
            default                    -> Rotation.NONE;
        };
    }
}
