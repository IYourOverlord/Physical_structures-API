package org.exampl.physical_structures.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.exampl.physical_structures.api.PhysicalStructureDefinition;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Структура, поставленная в мир, ожидающая отложенной сборки в Sable sub-level.
 * Хранит {@code placementId} — стабильный UUID, который придёт в
 * {@link org.exampl.physical_structures.api.event.PhysicalStructurePlacedEvent}
 * после сборки, чтобы коррелировать вызов spawn с результатом.
 */
public class PendingAssembly {

    private final BlockPos anchor;
    private final BoundingBox boundingBox;
    private int ticksRemaining;
    private final ResourceKey<Level> dimension;
    private final PhysicalStructureDefinition def;
    private final List<BlockPos> blocks;
    @Nullable private final BlockPos spawnerPos;
    private final UUID placementId;

    public PendingAssembly(BlockPos anchor, BoundingBox boundingBox, int delayTicks,
                            ResourceKey<Level> dimension, PhysicalStructureDefinition def,
                            List<BlockPos> blocks, @Nullable BlockPos spawnerPos,
                            UUID placementId) {
        this.anchor       = anchor;
        this.boundingBox  = boundingBox;
        this.ticksRemaining = delayTicks;
        this.dimension    = dimension;
        this.def          = def;
        this.blocks       = blocks;
        this.spawnerPos   = spawnerPos;
        this.placementId  = placementId;
    }

    /** Обратная совместимость — без placementId. */
    public PendingAssembly(BlockPos anchor, BoundingBox boundingBox, int delayTicks,
                            ResourceKey<Level> dimension, PhysicalStructureDefinition def,
                            List<BlockPos> blocks, @Nullable BlockPos spawnerPos) {
        this(anchor, boundingBox, delayTicks, dimension, def, blocks, spawnerPos, UUID.randomUUID());
    }

    /** @return true когда таймер истёк и нужно запустить сборку. */
    public boolean tick() { return --ticksRemaining <= 0; }

    public BlockPos anchor()                   { return anchor; }
    public BoundingBox boundingBox()           { return boundingBox; }
    public ResourceKey<Level> dimension()      { return dimension; }
    public PhysicalStructureDefinition definition() { return def; }
    public List<BlockPos> blocks()             { return blocks; }
    @Nullable public BlockPos spawnerPos()     { return spawnerPos; }
    public UUID placementId()                  { return placementId; }
}
