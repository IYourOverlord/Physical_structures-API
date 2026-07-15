package org.exampl.physical_structures.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import org.exampl.physical_structures.structure.StructurePlacer;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * PUBLIC API — единственная точка входа для размещения физической структуры.
 *
 * <pre>{@code
 * PlaceResult r = PhysicalStructurePlacer.place(level, origin,
 *         ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"));
 * }</pre>
 */
public final class PhysicalStructurePlacer {

    private PhysicalStructurePlacer() {}

    public enum PlaceResult { SUCCESS, UNKNOWN_ID, LOAD_FAILED }

    /**
     * Result of a placement that also exposes the Sable sub-level UUID (handle)
     * for later use with {@link PhysicalStructures#despawnStructure}.
     *
     * @param result the placement outcome.
     * @param handle UUID of the assembled Sable sub-level, or null if assembly
     *               was deferred ({@code assembleDelayTicks() > 0}), failed, or
     *               produced no sub-level.
     */
    public record PlaceResultHandle(PlaceResult result, @Nullable java.util.UUID handle) {}

    /** Places a composite structure set (multiple NBT parts) with explicit rotation. */
    public static PlaceResult placeSet(ServerLevel level, BlockPos origin,
                                        ResourceLocation setId, Rotation rotation) {
        Optional<PhysicalStructureSet> opt = PhysicalStructureRegistry.getSet(setId);
        if (opt.isEmpty()) return PlaceResult.UNKNOWN_ID;
        return StructurePlacer.placeSetFromDefinition(level, origin, opt.get(), rotation, null);
    }

    /** Places a composite structure set using its defaultRotation. */
    public static PlaceResult placeSet(ServerLevel level, BlockPos origin, ResourceLocation setId) {
        Optional<PhysicalStructureSet> opt = PhysicalStructureRegistry.getSet(setId);
        if (opt.isEmpty()) return PlaceResult.UNKNOWN_ID;
        PhysicalStructureSet set = opt.get();
        return StructurePlacer.placeSetFromDefinition(level, origin, set, set.defaultRotation(), null);
    }

    /** Place with explicit rotation. */
    public static PlaceResult place(ServerLevel level, BlockPos origin,
                                    ResourceLocation id, Rotation rotation) {
        Optional<PhysicalStructureDefinition> opt = PhysicalStructureRegistry.get(id);
        if (opt.isEmpty()) return PlaceResult.UNKNOWN_ID;
        return StructurePlacer.placeFromDefinition(level, origin, opt.get(), rotation, null);
    }

    /** Place using the definition's defaultRotation. */
    public static PlaceResult place(ServerLevel level, BlockPos origin, ResourceLocation id) {
        Optional<PhysicalStructureDefinition> opt = PhysicalStructureRegistry.get(id);
        if (opt.isEmpty()) return PlaceResult.UNKNOWN_ID;
        PhysicalStructureDefinition def = opt.get();
        return StructurePlacer.placeFromDefinition(level, origin, def, def.defaultRotation(), null);
    }

    /**
     * Place using the definition's defaultRotation, returning a handle to the
     * created Sable sub-level (usable with {@link PhysicalStructures#despawnStructure}).
     * The handle is {@code null} if assembly was deferred, failed, or produced no sub-level.
     */
    public static PlaceResultHandle placeWithHandle(ServerLevel level, BlockPos origin, ResourceLocation id) {
        Optional<PhysicalStructureDefinition> opt = PhysicalStructureRegistry.get(id);
        if (opt.isEmpty()) return new PlaceResultHandle(PlaceResult.UNKNOWN_ID, null);
        PhysicalStructureDefinition def = opt.get();
        return StructurePlacer.placeFromDefinitionWithHandle(level, origin, def, def.defaultRotation(), null);
    }

    /** Place with explicit rotation, returning a handle to the created Sable sub-level. */
    public static PlaceResultHandle placeWithHandle(ServerLevel level, BlockPos origin,
                                                      ResourceLocation id, Rotation rotation) {
        Optional<PhysicalStructureDefinition> opt = PhysicalStructureRegistry.get(id);
        if (opt.isEmpty()) return new PlaceResultHandle(PlaceResult.UNKNOWN_ID, null);
        return StructurePlacer.placeFromDefinitionWithHandle(level, origin, opt.get(), rotation, null);
    }

    /** Places a composite structure set using its defaultRotation, returning a handle to the sub-level. */
    public static PlaceResultHandle placeSetWithHandle(ServerLevel level, BlockPos origin, ResourceLocation setId) {
        Optional<PhysicalStructureSet> opt = PhysicalStructureRegistry.getSet(setId);
        if (opt.isEmpty()) return new PlaceResultHandle(PlaceResult.UNKNOWN_ID, null);
        PhysicalStructureSet set = opt.get();
        return StructurePlacer.placeSetFromDefinitionWithHandle(level, origin, set, set.defaultRotation(), null);
    }

    /** Places a composite structure set with explicit rotation, returning a handle to the sub-level. */
    public static PlaceResultHandle placeSetWithHandle(ServerLevel level, BlockPos origin,
                                                         ResourceLocation setId, Rotation rotation) {
        Optional<PhysicalStructureSet> opt = PhysicalStructureRegistry.getSet(setId);
        if (opt.isEmpty()) return new PlaceResultHandle(PlaceResult.UNKNOWN_ID, null);
        return StructurePlacer.placeSetFromDefinitionWithHandle(level, origin, opt.get(), rotation, null);
    }

    /**
     * Internal overload used by {@link org.exampl.physical_structures.block.StructureSpawnerBlock}
     * to propagate the spawner's position into {@link org.exampl.physical_structures.api.event.PhysicalStructurePlacedEvent}.
     *
     * @param spawnerPos position of the triggering StructureSpawnerBlock, or null.
     */
    public static PlaceResult place(ServerLevel level, BlockPos origin,
                                    ResourceLocation id, @Nullable BlockPos spawnerPos) {
        Optional<PhysicalStructureDefinition> opt = PhysicalStructureRegistry.get(id);
        if (opt.isEmpty()) return PlaceResult.UNKNOWN_ID;
        PhysicalStructureDefinition def = opt.get();
        return StructurePlacer.placeFromDefinition(level, origin, def, def.defaultRotation(), spawnerPos);
    }
}
