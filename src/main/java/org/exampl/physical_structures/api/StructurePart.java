package org.exampl.physical_structures.api;

import net.minecraft.core.Vec3i;

/**
 * One NBT piece of a {@link PhysicalStructureSet}, placed at {@code offset}
 * relative to the set's origin (before rotation).
 */
public record StructurePart(PhysicalStructureDefinition def, Vec3i offset) {
}
