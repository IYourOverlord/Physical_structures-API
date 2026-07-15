package org.exampl.physical_structures.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks structures assembled into Sable sub-levels so they can be
 * despawned/cleaned up later.
 *
 * <p>The handle is the Sable sub-level's own UUID ({@code ServerSubLevel#getUniqueId()}).</p>
 */
public final class SpawnedStructureRegistry {

    /**
     * Record of one assembled structure, keyed by its Sable sub-level UUID.
     * {@code origin} — позиция, переданная при спавне; нужна для
     * {@link org.exampl.physical_structures.api.event.PhysicalStructureDespawnedEvent}.
     */
    public record SpawnedStructureRecord(UUID subLevelUuid,
                                          ResourceLocation structureId,
                                          ResourceKey<Level> dimension,
                                          @Nullable BlockPos origin,
                                          UUID placementId) {

        /** Обратная совместимость: без origin и placementId. */
        public SpawnedStructureRecord(UUID subLevelUuid,
                                       ResourceLocation structureId,
                                       ResourceKey<Level> dimension) {
            this(subLevelUuid, structureId, dimension, null, UUID.randomUUID());
        }
    }

    private static final Map<UUID, SpawnedStructureRecord> RECORDS = new LinkedHashMap<>();

    private SpawnedStructureRegistry() {}

    public static void register(SpawnedStructureRecord record) {
        RECORDS.put(record.subLevelUuid(), record);
    }

    @Nullable
    public static SpawnedStructureRecord get(UUID handle) {
        return RECORDS.get(handle);
    }

    @Nullable
    public static SpawnedStructureRecord remove(UUID handle) {
        return RECORDS.remove(handle);
    }

    public static boolean contains(UUID handle) {
        return RECORDS.containsKey(handle);
    }

    /** Все текущие записи (read-only view). */
    public static java.util.Collection<SpawnedStructureRecord> all() {
        return java.util.Collections.unmodifiableCollection(RECORDS.values());
    }
}
