package org.exampl.physical_structures.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import org.exampl.physical_structures.api.PhysicalStructureDefinition;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * PUBLIC API — файрится на NeoForge game bus после успешного размещения и сборки структуры.
 *
 * <p>Теперь содержит {@link #placementId()} — тот же UUID, что и в
 * {@link PhysicalStructurePlacingEvent#placementId()}, что позволяет
 * однозначно связать "до" и "после" при отложенной сборке.</p>
 *
 * <pre>{@code
 * @SubscribeEvent
 * public static void onPlaced(PhysicalStructurePlacedEvent e) {
 *     if (e.structureId().equals(MY_ID)) {
 *         level.playSound(null, e.origin(), SoundEvents.ANVIL_LAND, ...);
 *     }
 * }
 * }</pre>
 */
public class PhysicalStructurePlacedEvent extends Event {

    private final ServerLevel level;
    private final BlockPos origin;
    private final PhysicalStructureDefinition definition;
    private final int blockCount;
    @Nullable private final BlockPos spawnerPos;
    @Nullable private final UUID handle;
    private final UUID placementId;

    public PhysicalStructurePlacedEvent(ServerLevel level,
                                         BlockPos origin,
                                         PhysicalStructureDefinition definition,
                                         int blockCount,
                                         @Nullable BlockPos spawnerPos,
                                         @Nullable UUID handle,
                                         UUID placementId) {
        this.level       = level;
        this.origin      = origin.immutable();
        this.definition  = definition;
        this.blockCount  = blockCount;
        this.spawnerPos  = spawnerPos;
        this.handle      = handle;
        this.placementId = placementId;
    }

    /** Обратная совместимость — без handle и placementId. */
    public PhysicalStructurePlacedEvent(ServerLevel level, BlockPos origin,
                                         PhysicalStructureDefinition definition,
                                         int blockCount, @Nullable BlockPos spawnerPos) {
        this(level, origin, definition, blockCount, spawnerPos, null, UUID.randomUUID());
    }

    public ServerLevel level()                   { return level; }
    public BlockPos origin()                     { return origin; }
    public PhysicalStructureDefinition definition() { return definition; }
    public ResourceLocation structureId()        { return definition.id(); }
    public int blockCount()                      { return blockCount; }
    @Nullable public BlockPos spawnerPos()       { return spawnerPos; }

    /**
     * UUID Sable sub-level — можно передать в
     * {@link org.exampl.physical_structures.api.PhysicalStructures#despawnStructure}.
     * {@code null} при {@code blocksOnly}-режиме.
     */
    @Nullable public UUID handle()               { return handle; }

    /**
     * Стабильный UUID размещения: совпадает с
     * {@link PhysicalStructurePlacingEvent#placementId()}, позволяя
     * сопоставить конкретный вызов spawn с событием даже при отложенной сборке.
     */
    public UUID placementId()                    { return placementId; }
}
