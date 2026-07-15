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

/**
 * Item that places a physical structure when right-clicked on a block face.
 * Uses {@link PhysicalStructurePlacer} — the same public API any other mod uses.
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
