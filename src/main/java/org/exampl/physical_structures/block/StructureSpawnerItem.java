package org.exampl.physical_structures.block;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.exampl.physical_structures.init.ModBlocks;
import org.exampl.physical_structures.init.ModDataComponents;

import java.util.List;

public class StructureSpawnerItem extends BlockItem {

    public StructureSpawnerItem(Properties props) {
        // ModBlocks.STRUCTURE_SPAWNER.get() — получаем Block из DeferredHolder
        super(ModBlocks.STRUCTURE_SPAWNER.get(), props);
    }

    // ---------------------------------------------------------------- PUBLIC API

    /**
     * Создать ItemStack с привязанным id структуры.
     * Используй из другого мода чтобы выдать/разместить предмет программно.
     *
     * <pre>{@code
     * ItemStack stack = StructureSpawnerItem.forStructure(
     *     ResourceLocation.fromNamespaceAndPath("mymod", "my_cannon"));
     * player.addItem(stack);
     * }</pre>
     */
    public static ItemStack forStructure(ResourceLocation structureId) {
        // new ItemStack(Item) — Item достаём через Items.AIR-подобный путь;
        // здесь безопасно использовать ModItems напрямую после регистрации.
        ItemStack stack = new ItemStack(ModBlocks.STRUCTURE_SPAWNER.get().asItem());
        stack.set(ModDataComponents.STRUCTURE_ID.get(), structureId.toString());
        return stack;
    }

    // ---------------------------------------------------------------- placement hook

    @Override
    protected boolean updateCustomBlockEntityTag(net.minecraft.core.BlockPos pos,
                                                  net.minecraft.world.level.Level level,
                                                  net.minecraft.world.entity.player.@org.jetbrains.annotations.Nullable Player player,
                                                  ItemStack stack,
                                                  net.minecraft.world.level.block.state.BlockState state) {
        boolean result = super.updateCustomBlockEntityTag(pos, level, player, stack, state);

        String rawId = stack.get(ModDataComponents.STRUCTURE_ID.get());
        if (rawId != null && !rawId.isBlank()
                && level.getBlockEntity(pos) instanceof StructureSpawnerBlockEntity be) {
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            if (id != null) be.setStructureId(id);
        }

        return result;
    }

    // ---------------------------------------------------------------- tooltip

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                 List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, lines, flag);

        String rawId = stack.get(ModDataComponents.STRUCTURE_ID.get());
        if (rawId != null && !rawId.isBlank()) {
            lines.add(Component.literal("Structure: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(rawId).withStyle(ChatFormatting.AQUA)));
        } else {
            lines.add(Component.literal("No structure set")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
