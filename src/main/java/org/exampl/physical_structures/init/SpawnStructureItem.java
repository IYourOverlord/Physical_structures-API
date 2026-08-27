package org.exampl.physical_structures.init;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Rotation;
import org.exampl.physical_structures.api.PhysicalStructurePlacer;
import org.exampl.physical_structures.api.PhysicalStructurePlacer.PlaceResult;
import org.exampl.physical_structures.api.provider.StructureSourceProviderRegistry;

/**
 * Item that places a physical structure when right-clicked on a block face.
 *
 * <p>Checks {@link StructureSourceProviderRegistry} first (covers {@code excraft:},
 * {@code sable:}, and any other namespace registered by a compat provider), then
 * falls back to {@link PhysicalStructurePlacer} for the mod's own registry — the
 * same order {@link org.exampl.physical_structures.block.StructureSpawnerBlock} uses.</p>
 */
public class SpawnStructureItem extends Item {

    private final ResourceLocation structureId;

    public SpawnStructureItem(Properties props, ResourceLocation structureId) {
        super(props);
        this.structureId = structureId;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide()) return InteractionResult.SUCCESS;

        ServerLevel level  = (ServerLevel) ctx.getLevel();
        BlockPos    origin = ctx.getClickedPos().relative(ctx.getClickedFace());

        // 1 — сторонний провайдер (excraft:, sable:, или любой зарегистрированный)
        if (StructureSourceProviderRegistry.isHandled(structureId)) {
            boolean ok = StructureSourceProviderRegistry.place(level, origin, structureId, ctx.getPlayer());
            if (ok) {
                ctx.getItemInHand().shrink(1);
                return InteractionResult.CONSUME;
            }
            return InteractionResult.FAIL;
        }

        // 2 — собственный реестр physical_structures
        PlaceResult result = PhysicalStructurePlacer.place(level, origin, structureId, Rotation.NONE);

        String message = switch (result) {
            case SUCCESS    -> "Structure placed and assembled!";
            case UNKNOWN_ID -> "Unknown structure id: " + structureId;
            case LOAD_FAILED -> "Failed to load structure NBT.";
        };

        if (ctx.getPlayer() != null) {
            ctx.getPlayer().sendSystemMessage(Component.literal(message));
        }

        if (result == PlaceResult.SUCCESS) {
            ctx.getItemInHand().shrink(1);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }
}
