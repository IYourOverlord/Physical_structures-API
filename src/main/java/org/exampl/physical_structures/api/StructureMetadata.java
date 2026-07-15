package org.exampl.physical_structures.api;

import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * PUBLIC API — метаданные структуры, доступные <em>без</em> её размещения.
 *
 * <pre>{@code
 * PhysicalStructures.getMetadata(level, id).ifPresent(m -> {
 *     LOGGER.info("{} размер: {}×{}×{}, поворот по умолч.: {}",
 *             id, m.sizeX(), m.sizeY(), m.sizeZ(), m.defaultRotation());
 * });
 * }</pre>
 *
 * @param id               ResourceLocation структуры
 * @param sizeX            размер по X до применения поворота
 * @param sizeY            высота
 * @param sizeZ            размер по Z до применения поворота
 * @param defaultRotation  поворот из определения {@link PhysicalStructureDefinition}
 * @param assembleDelayTicks задержка сборки в тиках
 * @param isComposite      true если это {@link PhysicalStructureSet} (несколько частей)
 */
public record StructureMetadata(
        net.minecraft.resources.ResourceLocation id,
        int sizeX,
        int sizeY,
        int sizeZ,
        Rotation defaultRotation,
        int assembleDelayTicks,
        boolean isComposite
) {
    /**
     * Загружает метаданные из NBT (только размер), не изменяя мир.
     * Возвращает empty если NBT не найден или повреждён.
     */
    static Optional<StructureMetadata> compute(ServerLevel level, PhysicalStructureDefinition def) {
        StructureTemplate template =
                org.exampl.physical_structures.structure.StructurePlacer.loadTemplate(level, def);
        if (template == null) return Optional.empty();

        Vec3i size = template.getSize();
        return Optional.of(new StructureMetadata(
                def.id(),
                size.getX(), size.getY(), size.getZ(),
                def.defaultRotation(),
                def.assembleDelayTicks(),
                false
        ));
    }
}
