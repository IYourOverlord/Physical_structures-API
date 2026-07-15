package org.exampl.physical_structures.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.exampl.physical_structures.api.PhysicalStructureDefinition;
import org.exampl.physical_structures.api.PlacementOptions;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * PUBLIC API — событие, файримое <em>до</em> размещения структуры.
 *
 * <p>Позволяет сторонним модам:</p>
 * <ul>
 *   <li><b>Отменить</b> размещение (например, в protected-зоне мода-claims): вызовите
 *       {@link #setCanceled(boolean) setCanceled(true)}.</li>
 *   <li><b>Изменить</b> origin или поворот в последний момент: вызовите
 *       {@link #setOrigin} / {@link #setRotation}.</li>
 *   <li><b>Записать</b> {@link #placementId()} — тот же UUID придёт в
 *       {@link PhysicalStructurePlacedEvent#placementId()}, что позволяет
 *       связать "до" и "после" без гонки по позиции/времени.</li>
 * </ul>
 *
 * <pre>{@code
 * @SubscribeEvent
 * public static void beforePlace(PhysicalStructurePlacingEvent event) {
 *     if (ClaimsAPI.isProtected(event.level(), event.origin())) {
 *         event.setCanceled(true);
 *         return;
 *     }
 *     event.setRotation(Rotation.NONE);
 * }
 * }</pre>
 *
 * <p>Подписка — на game event bus:</p>
 * <pre>{@code NeoForge.EVENT_BUS.register(MyListener.class); }</pre>
 *
 * <p><b>NeoForge 21.1 / EventBus 7.x:</b> cancelable события реализуют
 * {@link ICancellableEvent} вместо устаревшей аннотации {@code @Cancelable}.</p>
 */
public class PhysicalStructurePlacingEvent extends Event implements ICancellableEvent {

    private final ServerLevel level;
    private BlockPos origin;
    private Rotation rotation;
    private final PhysicalStructureDefinition definition;
    private final PlacementOptions options;
    private final UUID placementId;

    public PhysicalStructurePlacingEvent(ServerLevel level,
                                          BlockPos origin,
                                          Rotation rotation,
                                          PhysicalStructureDefinition definition,
                                          PlacementOptions options,
                                          UUID placementId) {
        this.level       = level;
        this.origin      = origin.immutable();
        this.rotation    = rotation;
        this.definition  = definition;
        this.options     = options;
        this.placementId = placementId;
    }

    // ----------------------------------------------------------------- readable

    public ServerLevel level()                      { return level; }
    public BlockPos origin()                        { return origin; }
    public Rotation rotation()                      { return rotation; }
    public PhysicalStructureDefinition definition() { return definition; }
    public PlacementOptions options()               { return options; }

    /**
     * Стабильный UUID, который придёт в
     * {@link PhysicalStructurePlacedEvent#placementId()} после успешной сборки.
     */
    public UUID placementId()                       { return placementId; }

    // ----------------------------------------------------------------- mutable

    /** Переопределить позицию размещения. */
    public void setOrigin(BlockPos pos)             { this.origin = pos.immutable(); }

    /** Переопределить поворот структуры. */
    public void setRotation(Rotation rotation)      { this.rotation = rotation; }
}
