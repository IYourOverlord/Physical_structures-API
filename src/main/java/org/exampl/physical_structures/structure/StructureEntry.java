package org.exampl.physical_structures.structure;

import net.minecraft.resources.ResourceLocation;

/**
 * Describes a structure that will be placed in the world and then
 * assembled into a Sable physics sub-level after ASSEMBLE_DELAY_TICKS.
 */
public record StructureEntry(
        /** Path to the .nbt file: data/<namespace>/structures/<path>.nbt */
        ResourceLocation id,
        /** Ticks between placement and physics assembly. */
        int assembleDelayTicks
) {
    /** Pre-defined entries - add more here as needed. */
    public static final StructureEntry GUN4 = new StructureEntry(
            ResourceLocation.fromNamespaceAndPath("physical_structures", "gun4"),
            40 // ~2 seconds
    );

    public static final StructureEntry GUN6 = new StructureEntry(
            ResourceLocation.fromNamespaceAndPath("physical_structures", "gun6"),
            40 // ~2 seconds
    );
}
