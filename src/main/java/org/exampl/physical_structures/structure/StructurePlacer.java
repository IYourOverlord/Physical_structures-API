package org.exampl.physical_structures.structure;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.NeoForge;
import org.exampl.physical_structures.PhysicalStructures;
import org.exampl.physical_structures.api.PhysicalStructureDefinition;
import org.exampl.physical_structures.api.PhysicalStructurePlacer.PlaceResult;
import org.exampl.physical_structures.api.PhysicalStructurePlacer.PlaceResultHandle;
import org.exampl.physical_structures.api.PlacementOptions;
import org.exampl.physical_structures.api.PlacementResult;
import org.exampl.physical_structures.api.event.PhysicalStructurePlacedEvent;
import org.exampl.physical_structures.api.event.PhysicalStructurePlacingEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Внутренний движок: загружает NBT, расставляет блоки, собирает sub-level Sable,
 * файрит события. Внешний код обращается только через
 * {@link org.exampl.physical_structures.api.PhysicalStructurePlacer} или
 * {@link org.exampl.physical_structures.api.PhysicalStructures}.
 */
public final class StructurePlacer {

    private StructurePlacer() {}

    // ================================================================ новый центральный путь

    /**
     * Центральный метод — все остальные перегрузки сводятся к нему.
     * Обрабатывает полный цикл: pre-place event → heightmap snap →
     * placeInWorld → postPlaceHook → assembly (immediate / deferred / blocksOnly).
     */
    public static PlacementResult placeWithOptions(ServerLevel level,
                                                    BlockPos origin,
                                                    PhysicalStructureDefinition def,
                                                    PlacementOptions opts) {
        UUID placementId = UUID.randomUUID();
        Rotation rotation = opts.rotation() != Rotation.NONE
                ? opts.rotation() : def.defaultRotation();

        // 1 — heightmap snap
        if (opts.snapHeightmap() != null) {
            int surfaceY = level.getHeight(opts.snapHeightmap(), origin.getX(), origin.getZ());
            origin = new BlockPos(origin.getX(), surfaceY, origin.getZ());
        }

        // 2 — pre-place cancelable event
        var placingEvent = new PhysicalStructurePlacingEvent(
                level, origin, rotation, def, opts, placementId);
        NeoForge.EVENT_BUS.post(placingEvent);
        if (placingEvent.isCanceled()) {
            return PlacementResult.cancelled(placementId);
        }
        // слушатель мог изменить origin/rotation
        origin   = placingEvent.origin();
        rotation = placingEvent.rotation();

        // 3 — загрузить шаблон
        StructureTemplate template = loadTemplate(level, def);
        if (template == null) return PlacementResult.loadFailed("NBT load failed for " + def.id());

        Vec3i size = template.getSize();
        PhysicalStructures.LOGGER.info(
                "[PhysicalStructures] Placing '{}' ({}x{}x{}) at {}",
                def.id(), size.getX(), size.getY(), size.getZ(), origin);

        // 4 — расставить блоки
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(rotation)
                .setIgnoreEntities(false)
                .setFinalizeEntities(true);

        template.placeInWorld(level, origin, origin, settings, level.getRandom(), 3);

        // 5 — postPlaceHook (для рандомизации лута, NBT и т.д.)
        if (opts.postPlaceHook() != null) {
            BlockPos max = origin.offset(
                    Math.max(size.getX() - 1, 0),
                    Math.max(size.getY() - 1, 0),
                    Math.max(size.getZ() - 1, 0));
            BlockPos.betweenClosedStream(BoundingBox.fromCorners(origin, max)).forEach(pos -> {
                var be = level.getBlockEntity(pos);
                if (be != null) opts.postPlaceHook().accept(pos.immutable(), be);
            });
        }

        // 6 — собрать список не-воздушных блоков
        BlockPos max = origin.offset(
                Math.max(size.getX() - 1, 0),
                Math.max(size.getY() - 1, 0),
                Math.max(size.getZ() - 1, 0));
        BoundingBox placed = BoundingBox.fromCorners(origin, max);
        List<BlockPos> blocks = BlockPos.betweenClosedStream(placed)
                .filter(pos -> !level.getBlockState(pos).isAir())
                .map(BlockPos::immutable)
                .toList();

        if (blocks.isEmpty()) {
            PhysicalStructures.LOGGER.warn(
                    "[PhysicalStructures] No blocks after placement of '{}' — skipping assembly.", def.id());
            return PlacementResult.loadFailed("No non-air blocks found after placeInWorld");
        }

        // 7 — только блоки, без сборки
        if (opts.blocksOnlyNoAssemble()) {
            PhysicalStructures.LOGGER.info(
                    "[PhysicalStructures] Placed '{}' ({} blocks) in blocks-only mode.", def.id(), blocks.size());
            return PlacementResult.blocksOnly(placementId);
        }

        // 8 — определить задержку сборки
        int delay = opts.assembleDelayTicksOverride() >= 0
                ? opts.assembleDelayTicksOverride()
                : def.assembleDelayTicks();

        // deferAssemblyToServerThread: минимум 1 тик задержки чтобы выйти из ворлдген-потока
        if (opts.deferAssemblyToServerThread() && delay == 0) delay = 1;

        if (delay > 0) {
            PendingAssemblyQueue.add(new PendingAssembly(
                    origin, placed, delay, level.dimension(), def, blocks,
                    opts.spawnerPos(), placementId));
            PhysicalStructures.LOGGER.info(
                    "[PhysicalStructures] Placed '{}' ({} blocks) at {}, assembly in {} tick(s).",
                    def.id(), blocks.size(), origin, delay);
            return PlacementResult.pending(placementId);
        }

        // 9 — немедленная сборка
        UUID handle = performAssembly(level, origin, def, blocks, placed,
                opts.spawnerPos(), placementId);
        if (handle == null) {
            return PlacementResult.assemblyFailed(placementId, "Sable returned null sub-level");
        }
        return PlacementResult.assembled(handle, placementId);
    }

    // ================================================================ canPlace (dry-run)

    /**
     * Проверяет, влезет ли структура в мир без реального её размещения.
     * Не изменяет мир — только загружает шаблон и проверяет bounding box.
     *
     * <p>Возвращает {@code false} если:</p>
     * <ul>
     *   <li>NBT не найден;</li>
     *   <li>хотя бы один не-воздушный блок структуры выходит за пределы
     *       загруженных чанков в данный момент;</li>
     *   <li>структура выходит за пределы мирового измерения (Y).</li>
     * </ul>
     */
    public static boolean canPlace(ServerLevel level, BlockPos origin,
                                    PhysicalStructureDefinition def, Rotation rotation) {
        StructureTemplate template = loadTemplate(level, def);
        if (template == null) return false;

        Vec3i size = template.getSize();
        BlockPos max = origin.offset(
                Math.max(size.getX() - 1, 0),
                Math.max(size.getY() - 1, 0),
                Math.max(size.getZ() - 1, 0));

        // проверка мировых границ по Y
        if (origin.getY() < level.getMinBuildHeight() || max.getY() > level.getMaxBuildHeight()) {
            return false;
        }

        // проверка загруженности всех чанков в bounding box
        for (int cx = origin.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = origin.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) return false;
            }
        }
        return true;
    }

    // ================================================================ legacy (обратная совместимость)

    public static PlaceResult placeFromDefinition(ServerLevel level, BlockPos origin,
                                                   PhysicalStructureDefinition def, Rotation rotation,
                                                   @Nullable BlockPos spawnerPos) {
        return placeFromDefinitionWithHandle(level, origin, def, rotation, spawnerPos).result();
    }

    public static PlaceResultHandle placeFromDefinitionWithHandle(ServerLevel level, BlockPos origin,
                                                                    PhysicalStructureDefinition def,
                                                                    Rotation rotation,
                                                                    @Nullable BlockPos spawnerPos) {
        PlacementOptions opts = PlacementOptions.builder()
                .rotation(rotation)
                .spawnerPos(spawnerPos)
                .build();
        PlacementResult r = placeWithOptions(level, origin, def, opts);
        return new PlaceResultHandle(r.toLegacy(), r.handle());
    }

    public static PlaceResult placeFromDefinition(ServerLevel level, BlockPos origin,
                                                   PhysicalStructureDefinition def, Rotation rotation) {
        return placeFromDefinition(level, origin, def, rotation, null);
    }

    // ================================================================ assembly

    /**
     * Assembles the given blocks into a Sable sub-level and fires
     * {@link PhysicalStructurePlacedEvent}.
     * Called both for immediate assembly and from {@link PendingAssemblyQueue}.
     */
    @Nullable
    public static UUID performAssembly(ServerLevel level, BlockPos origin,
                                        PhysicalStructureDefinition def,
                                        List<BlockPos> blocks, BoundingBox placed,
                                        @Nullable BlockPos spawnerPos,
                                        UUID placementId) {
        BoundingBox3i bounds = new BoundingBox3i(placed);
        bounds.set(bounds.minX() - 1, bounds.minY() - 1, bounds.minZ() - 1,
                   bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1);

        dev.ryanhcode.sable.sublevel.ServerSubLevel subLevel =
                SubLevelAssemblyHelper.assembleBlocks(level, origin, blocks, (BoundingBox3ic) bounds);

        PhysicalStructures.LOGGER.info(
                "[PhysicalStructures] Assembled {} block(s) from '{}' at {}.",
                blocks.size(), def.id(), origin);

        UUID handle = (subLevel != null) ? subLevel.getUniqueId() : null;

        if (handle != null) {
            SpawnedStructureRegistry.register(new SpawnedStructureRegistry.SpawnedStructureRecord(
                    handle, def.id(), level.dimension(), origin.immutable(), placementId));
        }

        NeoForge.EVENT_BUS.post(new PhysicalStructurePlacedEvent(
                level, origin, def, blocks.size(), spawnerPos, handle, placementId));

        return handle;
    }

    /** Обратная совместимость — без placementId (генерируем новый). */
    @Nullable
    public static UUID performAssembly(ServerLevel level, BlockPos origin,
                                        PhysicalStructureDefinition def,
                                        List<BlockPos> blocks, BoundingBox placed,
                                        @Nullable BlockPos spawnerPos) {
        return performAssembly(level, origin, def, blocks, placed, spawnerPos, UUID.randomUUID());
    }

    // ================================================================ set (составные структуры)

    public static PlaceResult placeSetFromDefinition(ServerLevel level, BlockPos origin,
                                                       org.exampl.physical_structures.api.PhysicalStructureSet set,
                                                       Rotation rotation, @Nullable BlockPos spawnerPos) {
        return placeSetFromDefinitionWithHandle(level, origin, set, rotation, spawnerPos).result();
    }

    public static PlaceResultHandle placeSetFromDefinitionWithHandle(ServerLevel level, BlockPos origin,
                                                       org.exampl.physical_structures.api.PhysicalStructureSet set,
                                                       Rotation rotation, @Nullable BlockPos spawnerPos) {
        List<BlockPos> allBlocks = new java.util.ArrayList<>();
        BoundingBox totalBounds = null;

        PhysicalStructureDefinition setAsDef = new PhysicalStructureDefinition(
                set.id(), (net.minecraft.resources.ResourceLocation) null,
                rotation, set.assembleDelayTicks());

        for (var part : set.parts()) {
            StructureTemplate template = loadTemplate(level, part.def());
            if (template == null) return new PlaceResultHandle(PlaceResult.LOAD_FAILED, null);

            BlockPos rotatedOffset = rotateOffset(part.offset(), rotation);
            BlockPos partOrigin = origin.offset(rotatedOffset);
            Vec3i size = template.getSize();

            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setMirror(Mirror.NONE).setRotation(rotation)
                    .setIgnoreEntities(false).setFinalizeEntities(true);
            template.placeInWorld(level, partOrigin, partOrigin, settings, level.getRandom(), 3);

            BlockPos pMax = partOrigin.offset(
                    Math.max(size.getX() - 1, 0),
                    Math.max(size.getY() - 1, 0),
                    Math.max(size.getZ() - 1, 0));
            BoundingBox partBounds = BoundingBox.fromCorners(partOrigin, pMax);
            List<BlockPos> partBlocks = BlockPos.betweenClosedStream(partBounds)
                    .filter(pos -> !level.getBlockState(pos).isAir())
                    .map(BlockPos::immutable).toList();
            allBlocks.addAll(partBlocks);

            totalBounds = (totalBounds == null) ? partBounds
                    : BoundingBox.encapsulatingBoxes(List.of(totalBounds, partBounds)).orElse(totalBounds);
        }

        if (allBlocks.isEmpty()) {
            return new PlaceResultHandle(PlaceResult.LOAD_FAILED, null);
        }

        UUID pid = UUID.randomUUID();
        if (set.assembleDelayTicks() > 0) {
            PendingAssemblyQueue.add(new PendingAssembly(
                    origin, totalBounds, set.assembleDelayTicks(),
                    level.dimension(), setAsDef, allBlocks, spawnerPos, pid));
            return new PlaceResultHandle(PlaceResult.SUCCESS, null);
        }

        UUID handle = performAssembly(level, origin, setAsDef, allBlocks, totalBounds, spawnerPos, pid);
        return new PlaceResultHandle(PlaceResult.SUCCESS, handle);
    }

    // ================================================================ helpers

    private static BlockPos rotateOffset(Vec3i offset, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90        -> new BlockPos(-offset.getZ(), offset.getY(),  offset.getX());
            case CLOCKWISE_180       -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos( offset.getZ(), offset.getY(), -offset.getX());
            default                  -> new BlockPos( offset.getX(), offset.getY(),  offset.getZ());
        };
    }

    public static StructureTemplate loadTemplate(ServerLevel level, PhysicalStructureDefinition def) {
        StructureTemplate template = new StructureTemplate();
        try {
            CompoundTag nbt;
            if (def.absoluteNbtPath() != null) {
                try (var stream = java.nio.file.Files.newInputStream(def.absoluteNbtPath())) {
                    nbt = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
                }
            } else {
                var resource = level.getServer().getResourceManager()
                        .getResourceOrThrow(def.nbtLocation());
                try (var stream = resource.open()) {
                    nbt = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
                }
            }
            template.load(level.holderLookup(Registries.BLOCK), nbt);
            return template;
        } catch (Exception e) {
            PhysicalStructures.LOGGER.error("[PhysicalStructures] Failed to load NBT '{}': {}",
                    def.absoluteNbtPath() != null ? def.absoluteNbtPath() : def.nbtLocation(),
                    e.getMessage());
            return null;
        }
    }
}
