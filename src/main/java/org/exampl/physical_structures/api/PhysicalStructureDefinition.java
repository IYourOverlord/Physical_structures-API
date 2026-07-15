package org.exampl.physical_structures.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * PUBLIC API — immutable description of one physical structure.
 *
 * <p>There are two ways to point at the NBT file:</p>
 *
 * <p><b>1. As a resource</b> — the NBT lives in any mod's resources/datapack
 * (your own or a foreign namespace, as long as it's loaded by the
 * {@code ResourceManager}):</p>
 * <pre>{@code
 * // file: data/mymod/structures/my_cannon.nbt
 * new PhysicalStructureDefinition(
 *     ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"),
 *     ResourceLocation.fromNamespaceAndPath("mymod", "structures/my_cannon.nbt")
 * );
 * }</pre>
 *
 * <p><b>2. As an absolute file</b> — the NBT lives on disk (e.g. in
 * {@code config/}, a player's datapack folder, or a generated file) and is
 * read directly, bypassing the resource pack system:</p>
 * <pre>{@code
 * PhysicalStructureDefinition.fromFile(
 *     ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"),
 *     Paths.get("config/mymod/structures/my_cannon.nbt"));
 * }</pre>
 */
public final class PhysicalStructureDefinition {

    private final ResourceLocation id;
    @Nullable
    private final ResourceLocation nbtLocation;
    @Nullable
    private final Path             absoluteNbtPath;
    private final Rotation         defaultRotation;
    private final int              assembleDelayTicks;

    public PhysicalStructureDefinition(ResourceLocation id,
                                       ResourceLocation nbtLocation,
                                       Rotation defaultRotation,
                                       int assembleDelayTicks) {
        this.id                 = id;
        this.nbtLocation        = nbtLocation;
        this.absoluteNbtPath    = null;
        this.defaultRotation    = defaultRotation;
        this.assembleDelayTicks = assembleDelayTicks;
    }

    public PhysicalStructureDefinition(ResourceLocation id,
                                       ResourceLocation nbtLocation,
                                       Rotation defaultRotation) {
        this(id, nbtLocation, defaultRotation, 0);
    }

    public PhysicalStructureDefinition(ResourceLocation id, ResourceLocation nbtLocation) {
        this(id, nbtLocation, Rotation.NONE, 0);
    }

    private PhysicalStructureDefinition(ResourceLocation id,
                                        Path absoluteNbtPath,
                                        Rotation defaultRotation,
                                        int assembleDelayTicks) {
        this.id                 = id;
        this.nbtLocation        = null;
        this.absoluteNbtPath    = absoluteNbtPath;
        this.defaultRotation    = defaultRotation;
        this.assembleDelayTicks = assembleDelayTicks;
    }

    /** Creates a definition whose NBT is read directly from an absolute/relative file path on disk. */
    public static PhysicalStructureDefinition fromFile(ResourceLocation id, Path nbtFile,
                                                        Rotation defaultRotation, int assembleDelayTicks) {
        return new PhysicalStructureDefinition(id, nbtFile, defaultRotation, assembleDelayTicks);
    }

    /** Creates a definition whose NBT is read directly from an absolute/relative file path on disk. */
    public static PhysicalStructureDefinition fromFile(ResourceLocation id, Path nbtFile) {
        return new PhysicalStructureDefinition(id, nbtFile, Rotation.NONE, 0);
    }

    public ResourceLocation id()                 { return id; }

    /** Resource-pack location of the NBT, or null if {@link #absoluteNbtPath()} is used instead. */
    @Nullable
    public ResourceLocation nbtLocation()        { return nbtLocation; }

    /** Absolute/relative filesystem path to the NBT, or null if {@link #nbtLocation()} is used instead. */
    @Nullable
    public Path             absoluteNbtPath()    { return absoluteNbtPath; }

    public Rotation         defaultRotation()    { return defaultRotation; }
    /** Ticks to wait after placement before assembling into a Sable sub-level. 0 = immediately. */
    public int               assembleDelayTicks() { return assembleDelayTicks; }

    @Override public String toString() {
        return "PhysicalStructureDefinition{id=" + id
                + ", nbt=" + (nbtLocation != null ? nbtLocation : absoluteNbtPath) + "}";
    }
}
