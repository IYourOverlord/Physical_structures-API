package org.exampl.physical_structures.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

/**
 * PUBLIC API — a composite structure made of several NBT parts placed at
 * relative offsets and assembled together into a single Sable sub-level.
 * Useful for large builds (ships, bases) split across multiple .nbt files.
 *
 * <pre>{@code
 * new PhysicalStructureSet(
 *     ResourceLocation.fromNamespaceAndPath("mymod", "battleship"),
 *     List.of(
 *         new StructurePart(hullDef, new Vec3i(0, 0, 0)),
 *         new StructurePart(bridgeDef, new Vec3i(0, 5, 2))
 *     ),
 *     Rotation.NONE,
 *     0
 * );
 * }</pre>
 */
public record PhysicalStructureSet(
        ResourceLocation id,
        List<StructurePart> parts,
        Rotation defaultRotation,
        int assembleDelayTicks
) {
    public PhysicalStructureSet(ResourceLocation id, List<StructurePart> parts) {
        this(id, parts, Rotation.NONE, 0);
    }
}
