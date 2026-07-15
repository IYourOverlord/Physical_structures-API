package org.exampl.physical_structures.api;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * PUBLIC API — детальный результат попытки разместить структуру.
 *
 * <p>Приходит на замену грубому трёхзначному {@link PhysicalStructurePlacer.PlaceResult}
 * (который оставлен для обратной совместимости, но теперь делегирует сюда).</p>
 *
 * <pre>{@code
 * PlacementResult r = PhysicalStructures.spawn(level, origin, id, opts);
 * switch (r.status()) {
 *     case SUCCESS_ASSEMBLED  -> uuid = r.handle();     // уже в мире, есть UUID
 *     case SUCCESS_PENDING    -> {}                     // блоки есть, Sable ещё не собрал
 *     case SUCCESS_BLOCKS_ONLY -> {}                   // без физики (ворлдген-пресет)
 *     case UNKNOWN_ID         -> LOGGER.warn("...");
 *     case LOAD_FAILED        -> LOGGER.error("...");
 *     case ASSEMBLY_FAILED    -> LOGGER.error("Sable не смог собрать: {}", r.errorMessage());
 *     case CANCELLED          -> {}                    // PhysicalStructurePlacingEvent отменил
 * }
 * }</pre>
 */
public final class PlacementResult {

    // ----------------------------------------------------------------- status enum

    public enum Status {
        /** Структура размещена и немедленно собрана в Sable sub-level. {@link #handle()} доступен. */
        SUCCESS_ASSEMBLED,
        /** Блоки размещены, сборка в Sable отложена ({@code assembleDelayTicks > 0} или
         *  {@link PlacementOptions#deferAssemblyToServerThread()}). Handle будет доступен
         *  через {@link org.exampl.physical_structures.api.event.PhysicalStructurePlacedEvent}
         *  с совпадающим {@code placementId()}. */
        SUCCESS_PENDING,
        /** Блоки размещены, Sable-сборка не запускалась ({@link PlacementOptions#blocksOnlyNoAssemble()}). */
        SUCCESS_BLOCKS_ONLY,
        /** id не найден в {@link PhysicalStructureRegistry}. */
        UNKNOWN_ID,
        /** NBT файл не найден или повреждён. */
        LOAD_FAILED,
        /** {@code template.placeInWorld} отработал, но Sable не вернул sub-level. */
        ASSEMBLY_FAILED,
        /** {@link org.exampl.physical_structures.api.event.PhysicalStructurePlacingEvent}
         *  был отменён слушателем. */
        CANCELLED;

        public boolean isSuccess() {
            return this == SUCCESS_ASSEMBLED || this == SUCCESS_PENDING || this == SUCCESS_BLOCKS_ONLY;
        }
    }

    // ----------------------------------------------------------------- fields

    private final Status status;
    @Nullable private final UUID handle;
    @Nullable private final String errorMessage;
    private final UUID placementId;   // стабильный id корреляции, даже для PENDING

    // ----------------------------------------------------------------- construction

    private PlacementResult(Status status, @Nullable UUID handle,
                             @Nullable String errorMessage, UUID placementId) {
        this.status       = status;
        this.handle       = handle;
        this.errorMessage = errorMessage;
        this.placementId  = placementId;
    }

    // ----------------------------------------------------------------- factories

    public static PlacementResult assembled(UUID handle, UUID placementId) {
        return new PlacementResult(Status.SUCCESS_ASSEMBLED, handle, null, placementId);
    }

    public static PlacementResult pending(UUID placementId) {
        return new PlacementResult(Status.SUCCESS_PENDING, null, null, placementId);
    }

    public static PlacementResult blocksOnly(UUID placementId) {
        return new PlacementResult(Status.SUCCESS_BLOCKS_ONLY, null, null, placementId);
    }

    public static PlacementResult unknownId() {
        return new PlacementResult(Status.UNKNOWN_ID, null, "Structure id not registered", newId());
    }

    public static PlacementResult loadFailed(String reason) {
        return new PlacementResult(Status.LOAD_FAILED, null, reason, newId());
    }

    public static PlacementResult assemblyFailed(UUID placementId, String reason) {
        return new PlacementResult(Status.ASSEMBLY_FAILED, null, reason, placementId);
    }

    public static PlacementResult cancelled(UUID placementId) {
        return new PlacementResult(Status.CANCELLED, null, null, placementId);
    }

    // ----------------------------------------------------------------- accessors

    public Status status()                   { return status; }
    public boolean isSuccess()               { return status.isSuccess(); }

    /**
     * UUID Sable sub-level для {@link PhysicalStructures#despawnStructure}.
     * Доступен только при {@link Status#SUCCESS_ASSEMBLED}.
     */
    @Nullable public UUID handle()           { return handle; }

    /**
     * Стабильный UUID этой попытки размещения, генерируется до постановки в очередь.
     * Передаётся в {@link org.exampl.physical_structures.api.event.PhysicalStructurePlacedEvent#placementId()}
     * — позволяет однозначно сопоставить конкретный вызов {@code spawn(...)} с
     * соответствующим событием, даже если сборка была отложена.
     */
    public UUID placementId()                { return placementId; }

    /** Описание ошибки при {@link Status#LOAD_FAILED} / {@link Status#ASSEMBLY_FAILED}. */
    @Nullable public String errorMessage()   { return errorMessage; }

    /** Совместимость со старым API: конвертирует в legacy {@link PhysicalStructurePlacer.PlaceResult}. */
    public PhysicalStructurePlacer.PlaceResult toLegacy() {
        return switch (status) {
            case SUCCESS_ASSEMBLED, SUCCESS_PENDING, SUCCESS_BLOCKS_ONLY
                    -> PhysicalStructurePlacer.PlaceResult.SUCCESS;
            case UNKNOWN_ID  -> PhysicalStructurePlacer.PlaceResult.UNKNOWN_ID;
            default          -> PhysicalStructurePlacer.PlaceResult.LOAD_FAILED;
        };
    }

    @Override public String toString() {
        return "PlacementResult{" + status + (handle != null ? ", handle=" + handle : "")
                + (errorMessage != null ? ", error=" + errorMessage : "") + "}";
    }

    private static UUID newId() { return UUID.randomUUID(); }
}
