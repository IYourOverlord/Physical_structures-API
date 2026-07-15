package org.exampl.physical_structures.api;

import net.minecraft.resources.ResourceLocation;
import org.exampl.physical_structures.PhysicalStructures;

import java.util.*;
import java.util.Set;

public final class PhysicalStructureRegistry {

    private static final Map<ResourceLocation, PhysicalStructureDefinition> ENTRIES =
            new LinkedHashMap<>();

    /** ids registered dynamically at runtime via {@link #registerRuntime}, protected from JSON reloads. */
    private static final Set<ResourceLocation> RUNTIME_IDS = new HashSet<>();

    private PhysicalStructureRegistry() {}

    /**
     * Registers (or replaces) a structure definition at runtime, without a JSON file
     * and without a game restart. The id is protected from being removed by
     * the JSON reload listener.
     */
    public static void registerRuntime(PhysicalStructureDefinition def) {
        boolean existed = ENTRIES.containsKey(def.id());
        ENTRIES.put(def.id(), def);
        RUNTIME_IDS.add(def.id());
        PhysicalStructures.LOGGER.info("[PhysicalStructures] {} (runtime): {}",
                existed ? "Replaced" : "Registered", def.id());
    }

    /** Removes a runtime-registered structure. @return true if it was present. */
    public static boolean unregisterRuntime(ResourceLocation id) {
        RUNTIME_IDS.remove(id);
        boolean removed = ENTRIES.remove(id) != null;
        if (removed) {
            PhysicalStructures.LOGGER.info("[PhysicalStructures] Unregistered (runtime): {}", id);
        }
        return removed;
    }

    public static void register(PhysicalStructureDefinition def) {
        if (ENTRIES.containsKey(def.id()))
            throw new IllegalArgumentException("[PhysicalStructures] Duplicate id: " + def.id());
        ENTRIES.put(def.id(), def);
        PhysicalStructures.LOGGER.info("[PhysicalStructures] Registered: {}", def.id());
    }

    public static void registerOrReplace(PhysicalStructureDefinition def) {
        if (RUNTIME_IDS.contains(def.id())) {
            PhysicalStructures.LOGGER.warn(
                    "[PhysicalStructures] JSON definition '{}' ignored - id is registered at runtime.",
                    def.id());
            return;
        }
        boolean existed = ENTRIES.containsKey(def.id());
        ENTRIES.put(def.id(), def);
        PhysicalStructures.LOGGER.info("[PhysicalStructures] {} (JSON): {}",
                existed ? "Reloaded" : "Registered", def.id());
    }

    public static void clearIds(Set<ResourceLocation> ids) {
        ids.forEach(id -> {
            if (!RUNTIME_IDS.contains(id)) ENTRIES.remove(id);
        });
    }

    public static Optional<PhysicalStructureDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(ENTRIES.get(id));
    }

    public static boolean contains(ResourceLocation id) { return ENTRIES.containsKey(id); }

    /**
     * Returns all registered structure ids.
     *
     * <pre>{@code
     * PhysicalStructureRegistry.getAll()
     *     .forEach(id -> LOGGER.info("Available structure: {}", id));
     * }</pre>
     */
    public static Set<ResourceLocation> getAll() {
        return Collections.unmodifiableSet(ENTRIES.keySet());
    }

    /** @deprecated Use {@link #getAll()} for ids or iterate definitions via {@code allDefinitions()}. */
    @Deprecated
    public static Collection<PhysicalStructureDefinition> all() {
        return Collections.unmodifiableCollection(ENTRIES.values());
    }

    /** Returns all registered definitions (id + nbt path + rotation). */
    public static Collection<PhysicalStructureDefinition> allDefinitions() {
        return Collections.unmodifiableCollection(ENTRIES.values());
    }

    // ----------------------------------------------------------------- sets (composite structures)

    private static final Map<ResourceLocation, PhysicalStructureSet> SETS = new LinkedHashMap<>();
    private static final Set<ResourceLocation> RUNTIME_SET_IDS = new HashSet<>();

    /** Registers (or replaces) a composite structure set, statically (mod init). */
    public static void registerSet(PhysicalStructureSet set) {
        if (SETS.containsKey(set.id()))
            throw new IllegalArgumentException("[PhysicalStructures] Duplicate set id: " + set.id());
        SETS.put(set.id(), set);
        PhysicalStructures.LOGGER.info("[PhysicalStructures] Registered set: {}", set.id());
    }

    /** Registers (or replaces) a composite structure set from JSON, protected from runtime overrides. */
    public static void registerOrReplaceSet(PhysicalStructureSet set) {
        if (RUNTIME_SET_IDS.contains(set.id())) {
            PhysicalStructures.LOGGER.warn(
                    "[PhysicalStructures] JSON set '{}' ignored - id is registered at runtime.", set.id());
            return;
        }
        boolean existed = SETS.containsKey(set.id());
        SETS.put(set.id(), set);
        PhysicalStructures.LOGGER.info("[PhysicalStructures] {} set (JSON): {}",
                existed ? "Reloaded" : "Registered", set.id());
    }

    /** Registers (or replaces) a composite structure set at runtime, protected from JSON reloads. */
    public static void registerRuntimeSet(PhysicalStructureSet set) {
        boolean existed = SETS.containsKey(set.id());
        SETS.put(set.id(), set);
        RUNTIME_SET_IDS.add(set.id());
        PhysicalStructures.LOGGER.info("[PhysicalStructures] {} set (runtime): {}",
                existed ? "Replaced" : "Registered", set.id());
    }

    /** Removes a runtime-registered set. @return true if it was present. */
    public static boolean unregisterRuntimeSet(ResourceLocation id) {
        RUNTIME_SET_IDS.remove(id);
        boolean removed = SETS.remove(id) != null;
        if (removed) PhysicalStructures.LOGGER.info("[PhysicalStructures] Unregistered set (runtime): {}", id);
        return removed;
    }

    public static void clearSetIds(Set<ResourceLocation> ids) {
        ids.forEach(id -> {
            if (!RUNTIME_SET_IDS.contains(id)) SETS.remove(id);
        });
    }

    public static Optional<PhysicalStructureSet> getSet(ResourceLocation id) {
        return Optional.ofNullable(SETS.get(id));
    }

    public static boolean containsSet(ResourceLocation id) { return SETS.containsKey(id); }

    public static Set<ResourceLocation> getAllSets() {
        return Collections.unmodifiableSet(SETS.keySet());
    }
}
