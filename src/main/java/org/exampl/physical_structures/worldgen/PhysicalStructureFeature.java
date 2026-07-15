package org.exampl.physical_structures.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.exampl.physical_structures.PhysicalStructures;
import org.exampl.physical_structures.api.PlacementOptions;
import org.exampl.physical_structures.api.PlacementResult;

/**
 * PUBLIC API — ванильный NeoForge {@link Feature}, размещающий физическую
 * структуру во время генерации мира.
 *
 * <h3>Как использовать из другого мода</h3>
 * <ol>
 *   <li>Зарегистрируйте этот Feature в {@code DeferredRegister<Feature<?>>}.</li>
 *   <li>Создайте JSON {@code ConfiguredFeature} в датапаке:</li>
 * </ol>
 * <pre>{@code
 * // data/mymod/worldgen/configured_feature/my_ship.json
 * {
 *   "type": "physical_structures:physical_structure",
 *   "config": {
 *     "structure_id": "physical_structures:gun6",
 *     "rotation": "NONE",
 *     "snap_to_surface": true,
 *     "assemble_delay_ticks": 5
 *   }
 * }
 * }</pre>
 * <ol start="3">
 *   <li>Создайте {@code PlacedFeature} и добавьте в биом через {@code BiomeModifier}.</li>
 * </ol>
 *
 * <h3>Безопасность ворлдгена</h3>
 * <p>Этот метод вызывается из ворлдген-потока. Мод автоматически использует
 * {@link PlacementOptions#deferAssemblyToServerThread()} = true, что откладывает
 * Sable-сборку на 1+ server-tick через {@link
 * org.exampl.physical_structures.structure.PendingAssemblyQueue}.
 * Блоки ставятся в рамках генерации чанка (безопасно), физическая сборка —
 * на main thread, когда чанк уже загружен.</p>
 */
public class PhysicalStructureFeature extends Feature<PhysicalStructureFeatureConfig> {

    public PhysicalStructureFeature(Codec<PhysicalStructureFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<PhysicalStructureFeatureConfig> ctx) {
        var config = ctx.config();
        // ctx.level() — это WorldGenLevel-обёртка; .getLevel() даёт настоящий ServerLevel
        var level  = ctx.level().getLevel();
        BlockPos origin = ctx.origin();

        PlacementOptions opts = PlacementOptions.builder()
                .rotation(config.rotation())
                .deferAssemblyToServerThread(true)       // обязательно для ворлдгена
                .snapToHeightmap(config.snapToSurface()
                        ? Heightmap.Types.WORLD_SURFACE_WG : null)
                .assembleDelayTicksOverride(config.assembleDelayTicks())
                .build();

        PlacementResult result = org.exampl.physical_structures.api.PhysicalStructures
                .spawn(level, origin, config.structureId(), opts);

        if (!result.isSuccess()) {
            PhysicalStructures.LOGGER.debug(
                    "[PhysicalStructureFeature] Failed '{}' at {}: {}",
                    config.structureId(), origin, result.status());
            return false;
        }

        PhysicalStructures.LOGGER.debug(
                "[PhysicalStructureFeature] Queued '{}' at {} (id={}).",
                config.structureId(), origin, result.placementId());
        return true;
    }
}
