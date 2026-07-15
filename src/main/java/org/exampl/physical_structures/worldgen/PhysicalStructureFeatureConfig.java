package org.exampl.physical_structures.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * PUBLIC API — конфигурация {@link PhysicalStructureFeature} для JSON датапака.
 *
 * <pre>{@code
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
 *
 * <p>Все поля кроме {@code structure_id} опциональны.</p>
 */
public record PhysicalStructureFeatureConfig(
        ResourceLocation structureId,
        Rotation rotation,
        boolean snapToSurface,
        int assembleDelayTicks
) implements FeatureConfiguration {

    public static final MapCodec<PhysicalStructureFeatureConfig> CODEC =
            RecordCodecBuilder.mapCodec(inst -> inst.group(
                    ResourceLocation.CODEC.fieldOf("structure_id")
                            .forGetter(PhysicalStructureFeatureConfig::structureId),
                    Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE)
                            .forGetter(PhysicalStructureFeatureConfig::rotation),
                    Codec.BOOL.optionalFieldOf("snap_to_surface", false)
                            .forGetter(PhysicalStructureFeatureConfig::snapToSurface),
                    Codec.INT.optionalFieldOf("assemble_delay_ticks", 1)
                            .forGetter(PhysicalStructureFeatureConfig::assembleDelayTicks)
            ).apply(inst, PhysicalStructureFeatureConfig::new));
}
