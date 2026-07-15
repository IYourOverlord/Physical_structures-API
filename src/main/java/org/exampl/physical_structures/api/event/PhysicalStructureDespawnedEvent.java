package org.exampl.physical_structures.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * PUBLIC API — файрится на NeoForge game bus при удалении ранее собранной структуры.
 *
 * <p>Симметричное событие к {@link PhysicalStructurePlacedEvent}. Позволяет
 * сторонним модам реагировать на удаление: убрать маркеры на карте, сыграть
 * звук взрыва, дропнуть предметы и т.д.</p>
 *
 * <pre>{@code
 * @SubscribeEvent
 * public static void onDespawn(PhysicalStructureDespawnedEvent e) {
 *     MapMarkers.remove(e.handle());
 *     e.level().playSound(null, e.origin(), SoundEvents.GENERIC_EXPLODE, ...);
 * }
 * }</pre>
 */
public class PhysicalStructureDespawnedEvent extends Event {

    private final ServerLevel level;
    private final UUID handle;
    private final ResourceLocation structureId;
    @Nullable private final BlockPos origin;

    public PhysicalStructureDespawnedEvent(ServerLevel level,
                                            UUID handle,
                                            ResourceLocation structureId,
                                            @Nullable BlockPos origin) {
        this.level       = level;
        this.handle      = handle;
        this.structureId = structureId;
        this.origin      = origin;
    }

    public ServerLevel level()           { return level; }

    /** UUID Sable sub-level, который был передан в {@code despawnStructure(...)}. */
    public UUID handle()                 { return handle; }

    public ResourceLocation structureId(){ return structureId; }

    /**
     * Позиция, где была собрана структура — берётся из {@link
     * org.exampl.physical_structures.structure.SpawnedStructureRegistry}.
     * Может быть {@code null}, если запись не сохранила origin.
     */
    @Nullable public BlockPos origin()   { return origin; }
}
