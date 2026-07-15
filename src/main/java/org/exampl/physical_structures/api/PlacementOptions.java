package org.exampl.physical_structures.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/**
 * PUBLIC API — параметры размещения структуры.
 *
 * <p>Заменяет разрастающееся семейство перегрузок в {@link PhysicalStructurePlacer} единым
 * объектом-настройкой. Используйте статический factory или builder:</p>
 *
 * <pre>{@code
 * // Минимум — только поворот:
 * PlacementOptions opts = PlacementOptions.withRotation(Rotation.CLOCKWISE_90);
 *
 * // Полная настройка:
 * PlacementOptions opts = PlacementOptions.builder()
 *         .rotation(Rotation.CLOCKWISE_90)
 *         .assembleDelayTicksOverride(20)
 *         .snapToHeightmap(Heightmap.Types.WORLD_SURFACE_WG)
 *         .postPlaceHook((pos, be) -> randomizeLoot(be))
 *         .spawnerPos(spawnerBlockPos)
 *         .blocksOnlyNoAssemble(true)   // для ворлдгена: блоки без Sable-сборки
 *         .build();
 *
 * PlacementResult result = PhysicalStructurePlacer.place(level, origin, id, opts);
 * }</pre>
 */
public final class PlacementOptions {

    // ----------------------------------------------------------------- fields

    private final Rotation rotation;
    private final int assembleDelayTicksOverride; // -1 = не переопределять
    @Nullable private final BlockPos spawnerPos;
    @Nullable private final Heightmap.Types snapHeightmap;
    @Nullable private final BiConsumer<BlockPos, BlockEntity> postPlaceHook;
    private final boolean blocksOnlyNoAssemble;
    private final boolean deferAssemblyToServerThread;

    // ----------------------------------------------------------------- private ctor

    private PlacementOptions(Builder b) {
        this.rotation                     = b.rotation;
        this.assembleDelayTicksOverride   = b.assembleDelayTicksOverride;
        this.spawnerPos                   = b.spawnerPos;
        this.snapHeightmap                = b.snapHeightmap;
        this.postPlaceHook                = b.postPlaceHook;
        this.blocksOnlyNoAssemble         = b.blocksOnlyNoAssemble;
        this.deferAssemblyToServerThread  = b.deferAssemblyToServerThread;
    }

    // ----------------------------------------------------------------- factories

    /** Параметры по умолчанию (поворот NONE, без хуков, немедленная сборка). */
    public static PlacementOptions defaults() {
        return builder().build();
    }

    /** Быстрый factory: только поворот, всё остальное по умолчанию. */
    public static PlacementOptions withRotation(Rotation rotation) {
        return builder().rotation(rotation).build();
    }

    /**
     * Пресет для вызова из ворлдгена (Feature/ChunkGenerator):
     * <ul>
     *   <li>Только расставляет блоки, без Sable-сборки ({@code blocksOnlyNoAssemble = true}).</li>
     *   <li>Привязывается к поверхности мира ({@link Heightmap.Types#WORLD_SURFACE_WG}).</li>
     *   <li>Сборку в Sable-sublevel откладывает на server-thread тик, когда чанк
     *       гарантированно загружен ({@code deferAssemblyToServerThread = true}).</li>
     * </ul>
     */
    public static PlacementOptions forWorldgen(Rotation rotation) {
        return builder()
                .rotation(rotation)
                .snapToHeightmap(Heightmap.Types.WORLD_SURFACE_WG)
                .blocksOnlyNoAssemble(false)          // блоки ставим, а сборку...
                .deferAssemblyToServerThread(true)    // ...откладываем на server tick
                .build();
    }

    public static Builder builder() { return new Builder(); }

    // ----------------------------------------------------------------- accessors

    public Rotation rotation()                                       { return rotation; }

    /** -1 означает «использовать значение из {@link PhysicalStructureDefinition}». */
    public int assembleDelayTicksOverride()                          { return assembleDelayTicksOverride; }

    @Nullable public BlockPos spawnerPos()                           { return spawnerPos; }

    /**
     * Если задан, origin по оси Y автоматически поднимается до поверхности
     * указанной heightmap перед размещением.
     */
    @Nullable public Heightmap.Types snapHeightmap()                 { return snapHeightmap; }

    /**
     * Хук, вызываемый для каждой {@link BlockEntity} сразу после
     * {@code template.placeInWorld(...)}, до сборки в Sable sub-level.
     * Удобен для рандомизации лута в сундуках, установки NBT-данных и т.д.
     */
    @Nullable public BiConsumer<BlockPos, BlockEntity> postPlaceHook() { return postPlaceHook; }

    /**
     * Если {@code true} — ставит только блоки, без Sable-сборки.
     * Полезно для размещения статичных декораций (не нужна физика).
     */
    public boolean blocksOnlyNoAssemble()                            { return blocksOnlyNoAssemble; }

    /**
     * Если {@code true} — сборка в Sable sub-level будет отложена через
     * {@link org.exampl.physical_structures.structure.PendingAssemblyQueue}
     * на ближайший server-thread тик, даже если {@code assembleDelayTicks == 0}.
     * Используется для ворлдгена, где прямой вызов Sable небезопасен вне server-thread.
     */
    public boolean deferAssemblyToServerThread()                     { return deferAssemblyToServerThread; }

    // ----------------------------------------------------------------- builder

    public static final class Builder {
        private Rotation rotation                    = Rotation.NONE;
        private int assembleDelayTicksOverride       = -1;
        @Nullable private BlockPos spawnerPos        = null;
        @Nullable private Heightmap.Types snapHeightmap = null;
        @Nullable private BiConsumer<BlockPos, BlockEntity> postPlaceHook = null;
        private boolean blocksOnlyNoAssemble         = false;
        private boolean deferAssemblyToServerThread  = false;

        private Builder() {}

        public Builder rotation(Rotation r)                               { this.rotation = r; return this; }
        public Builder assembleDelayTicksOverride(int ticks)              { this.assembleDelayTicksOverride = ticks; return this; }
        public Builder spawnerPos(@Nullable BlockPos pos)                 { this.spawnerPos = pos; return this; }
        public Builder snapToHeightmap(@Nullable Heightmap.Types hm)      { this.snapHeightmap = hm; return this; }
        public Builder postPlaceHook(@Nullable BiConsumer<BlockPos, BlockEntity> hook) { this.postPlaceHook = hook; return this; }
        public Builder blocksOnlyNoAssemble(boolean v)                    { this.blocksOnlyNoAssemble = v; return this; }
        public Builder deferAssemblyToServerThread(boolean v)             { this.deferAssemblyToServerThread = v; return this; }

        public PlacementOptions build()                                   { return new PlacementOptions(this); }
    }
}
