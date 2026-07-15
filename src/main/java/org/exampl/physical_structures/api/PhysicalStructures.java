package org.exampl.physical_structures.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.common.NeoForge;
import org.exampl.physical_structures.api.PhysicalStructurePlacer.PlaceResult;
import org.exampl.physical_structures.api.event.PhysicalStructureDespawnedEvent;
import org.exampl.physical_structures.block.StructureSpawnerBlock;

import java.util.Set;
import java.util.UUID;

/**
 * PUBLIC API — главная точка входа для интеграции с physical_structures.
 *
 * <h3>Простейший спавн</h3>
 * <pre>{@code
 * PhysicalStructures.spawnStructure(serverLevel, pos,
 *         ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"));
 * }</pre>
 *
 * <h3>С PlacementOptions (поворот, хук, heightmap, ворлдген-режим)</h3>
 * <pre>{@code
 * PlacementResult r = PhysicalStructures.spawn(level, origin, id,
 *         PlacementOptions.builder()
 *                 .rotation(Rotation.CLOCKWISE_90)
 *                 .snapToHeightmap(Heightmap.Types.WORLD_SURFACE_WG)
 *                 .postPlaceHook((pos, be) -> randomizeLoot(be))
 *                 .build());
 *
 * if (r.status() == PlacementResult.Status.SUCCESS_ASSEMBLED) {
 *     myTracker.put(r.placementId(), r.handle());
 * }
 * }</pre>
 *
 * <h3>Ворлдген (Feature/ChunkGenerator)</h3>
 * <pre>{@code
 * PhysicalStructures.spawn(level, origin, id,
 *         PlacementOptions.forWorldgen(Rotation.NONE));
 * // Блоки размещены сразу; Sable-сборка отложена на server-tick,
 * // когда чанк гарантированно загружен.
 * }</pre>
 *
 * <h3>События</h3>
 * <pre>{@code
 * // До размещения (cancelable):
 * NeoForge.EVENT_BUS.addListener(PhysicalStructurePlacingEvent.class, e -> {
 *     if (ClaimsAPI.isProtected(e.level(), e.origin())) e.setCanceled(true);
 * });
 * // После:
 * NeoForge.EVENT_BUS.addListener(PhysicalStructurePlacedEvent.class, e -> {
 *     LOGGER.info("Placed {} at {}", e.structureId(), e.origin());
 * });
 * // При удалении:
 * NeoForge.EVENT_BUS.addListener(PhysicalStructureDespawnedEvent.class, e -> {
 *     MapMarkers.remove(e.handle());
 * });
 * }</pre>
 */
public final class PhysicalStructures {

    private PhysicalStructures() {}

    // ================================================================ spawn — новый API

    /**
     * Размещает структуру с полным контролем через {@link PlacementOptions}.
     * Поддерживает: поворот, heightmap-привязку, postPlaceHook, ворлдген-режим,
     * cancellable pre-place event. Возвращает детальный {@link PlacementResult}.
     */
    public static PlacementResult spawn(ServerLevel level, BlockPos origin,
                                         ResourceLocation structureId, PlacementOptions opts) {
        PhysicalStructureDefinition def = PhysicalStructureRegistry.get(structureId).orElse(null);
        if (def == null) return PlacementResult.unknownId();
        return org.exampl.physical_structures.structure.StructurePlacer
                .placeWithOptions(level, origin, def, opts);
    }

    /** Spawn с настройками по умолчанию — shortcut для {@link #spawn(ServerLevel, BlockPos, ResourceLocation, PlacementOptions)}. */
    public static PlacementResult spawn(ServerLevel level, BlockPos origin, ResourceLocation structureId) {
        return spawn(level, origin, structureId, PlacementOptions.defaults());
    }

    // ================================================================ canPlace (dry-run)

    /**
     * Проверяет, возможно ли разместить структуру в данной позиции,
     * <b>не изменяя мир</b>. Проверяет: наличие NBT, загруженность чанков,
     * мировые границы по Y.
     *
     * <pre>{@code
     * if (PhysicalStructures.canPlace(level, pos, id, Rotation.NONE)) {
     *     PhysicalStructures.spawn(level, pos, id);
     * }
     * }</pre>
     */
    public static boolean canPlace(ServerLevel level, BlockPos origin,
                                    ResourceLocation structureId, Rotation rotation) {
        PhysicalStructureDefinition def = PhysicalStructureRegistry.get(structureId).orElse(null);
        if (def == null) return false;
        return org.exampl.physical_structures.structure.StructurePlacer
                .canPlace(level, origin, def, rotation);
    }

    // ================================================================ метаданные

    /**
     * Возвращает метаданные структуры без её размещения.
     *
     * <pre>{@code
     * PhysicalStructures.getMetadata(id).ifPresent(meta -> {
     *     LOGGER.info("{}: {}x{}x{}", id, meta.sizeX(), meta.sizeY(), meta.sizeZ());
     * });
     * }</pre>
     *
     * @return {@link java.util.Optional} с метаданными, или empty если id не зарегистрирован
     */
    public static java.util.Optional<StructureMetadata> getMetadata(ServerLevel level,
                                                                      ResourceLocation structureId) {
        PhysicalStructureDefinition def = PhysicalStructureRegistry.get(structureId).orElse(null);
        if (def == null) return java.util.Optional.empty();
        return StructureMetadata.compute(level, def);
    }

    // ================================================================ legacy spawnStructure (обратная совместимость)

    /** @deprecated Используйте {@link #spawn(ServerLevel, BlockPos, ResourceLocation, PlacementOptions)} */
    @Deprecated
    public static boolean spawnStructure(ServerLevel level, BlockPos origin, ResourceLocation structureId) {
        return spawn(level, origin, structureId).isSuccess();
    }

    /** @deprecated Используйте {@link #spawn} с {@link PlacementOptions#withRotation(Rotation)} */
    @Deprecated
    public static boolean spawnStructure(ServerLevel level, BlockPos origin,
                                          ResourceLocation structureId, Rotation rotation) {
        return spawn(level, origin, structureId, PlacementOptions.withRotation(rotation)).isSuccess();
    }

    /** @deprecated Используйте {@link #spawn} и {@link PlacementResult#toLegacy()} */
    @Deprecated
    public static PlaceResult spawnStructureResult(ServerLevel level, BlockPos origin,
                                                    ResourceLocation structureId) {
        return spawn(level, origin, structureId).toLegacy();
    }

    public static PhysicalStructurePlacer.PlaceResultHandle spawnStructureWithHandle(
            ServerLevel level, BlockPos origin, ResourceLocation structureId) {
        PlacementResult r = spawn(level, origin, structureId);
        return new PhysicalStructurePlacer.PlaceResultHandle(r.toLegacy(), r.handle());
    }

    public static PhysicalStructurePlacer.PlaceResultHandle spawnStructureWithHandle(
            ServerLevel level, BlockPos origin, ResourceLocation structureId, Rotation rotation) {
        PlacementResult r = spawn(level, origin, structureId, PlacementOptions.withRotation(rotation));
        return new PhysicalStructurePlacer.PlaceResultHandle(r.toLegacy(), r.handle());
    }

    public static boolean spawnStructureSet(ServerLevel level, BlockPos origin, ResourceLocation setId) {
        return PhysicalStructurePlacer.placeSet(level, origin, setId) == PlaceResult.SUCCESS;
    }

    public static boolean spawnStructureSet(ServerLevel level, BlockPos origin,
                                             ResourceLocation setId, Rotation rotation) {
        return PhysicalStructurePlacer.placeSet(level, origin, setId, rotation) == PlaceResult.SUCCESS;
    }

    public static PhysicalStructurePlacer.PlaceResultHandle spawnStructureSetWithHandle(
            ServerLevel level, BlockPos origin, ResourceLocation setId) {
        return PhysicalStructurePlacer.placeSetWithHandle(level, origin, setId);
    }

    public static PhysicalStructurePlacer.PlaceResultHandle spawnStructureSetWithHandle(
            ServerLevel level, BlockPos origin, ResourceLocation setId, Rotation rotation) {
        return PhysicalStructurePlacer.placeSetWithHandle(level, origin, setId, rotation);
    }

    // ================================================================ despawn

    /**
     * Удаляет ранее собранную структуру по UUID Sable sub-level и файрит
     * {@link PhysicalStructureDespawnedEvent}.
     *
     * @param handle UUID, полученный из {@link PlacementResult#handle()} или
     *               {@link org.exampl.physical_structures.api.event.PhysicalStructurePlacedEvent#handle()}
     * @return true если sub-level найден и помечен для удаления
     */
    public static boolean despawnStructure(ServerLevel level, UUID handle) {
        var record = org.exampl.physical_structures.structure.SpawnedStructureRegistry.get(handle);
        if (record == null) return false;

        var container = dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer.getContainer(level);
        if (container == null) return false;

        var subLevel = container.getSubLevel(handle);
        if (subLevel == null) {
            org.exampl.physical_structures.structure.SpawnedStructureRegistry.remove(handle);
            return false;
        }

        subLevel.markRemoved();
        org.exampl.physical_structures.structure.SpawnedStructureRegistry.remove(handle);

        NeoForge.EVENT_BUS.post(new PhysicalStructureDespawnedEvent(
                level, handle, record.structureId(), record.origin()));

        org.exampl.physical_structures.PhysicalStructures.LOGGER.info(
                "[PhysicalStructures] Despawned '{}' (sub-level {}).", record.structureId(), handle);
        return true;
    }

    // ================================================================ реестр

    public static Set<ResourceLocation> availableStructures() {
        return PhysicalStructureRegistry.getAll();
    }

    public static boolean isRegistered(ResourceLocation structureId) {
        return PhysicalStructureRegistry.contains(structureId);
    }

    public static void registerStructure(PhysicalStructureDefinition def) {
        PhysicalStructureRegistry.registerRuntime(def);
    }

    public static void registerStructureFromFile(ResourceLocation id, java.nio.file.Path nbtFile,
                                                   Rotation defaultRotation, int assembleDelayTicks) {
        if (!java.nio.file.Files.exists(nbtFile)) {
            org.exampl.physical_structures.PhysicalStructures.LOGGER.warn(
                    "[PhysicalStructures] NBT file not found for '{}': {}", id, nbtFile);
        }
        PhysicalStructureRegistry.registerRuntime(
                PhysicalStructureDefinition.fromFile(id, nbtFile, defaultRotation, assembleDelayTicks));
    }

    public static void registerStructureFromFile(ResourceLocation id, java.nio.file.Path nbtFile) {
        registerStructureFromFile(id, nbtFile, Rotation.NONE, 0);
    }

    public static boolean unregisterStructure(ResourceLocation structureId) {
        return PhysicalStructureRegistry.unregisterRuntime(structureId);
    }
}
