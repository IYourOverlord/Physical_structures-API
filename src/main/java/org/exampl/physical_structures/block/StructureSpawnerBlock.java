package org.exampl.physical_structures.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.exampl.physical_structures.api.PhysicalStructurePlacer;
import org.exampl.physical_structures.api.PhysicalStructurePlacer.PlaceResult;
import org.exampl.physical_structures.api.PhysicalStructureRegistry;
import org.exampl.physical_structures.api.provider.StructureSourceProviderRegistry;
import org.exampl.physical_structures.init.ModBlocks;

import javax.annotation.Nullable;

/**
 * Блок-спаунер физических структур.
 *
 * <p>Поддерживает два источника структур:</p>
 * <ul>
 *   <li><b>physical_structures</b> — собственный реестр {@link PhysicalStructureRegistry};</li>
 *   <li><b>Любой namespace, для которого зарегистрирован провайдер</b> (например {@code excraft:})
 *       через {@link StructureSourceProviderRegistry}. Провайдеры регистрируются в конструкторе
 *       своего мода без правки этого класса.</li>
 * </ul>
 */
public class StructureSpawnerBlock extends BaseEntityBlock {

    public static final MapCodec<StructureSpawnerBlock> CODEC =
            simpleCodec(StructureSpawnerBlock::new);

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    public StructureSpawnerBlock(Properties props) { super(props); }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StructureSpawnerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    // ---------------------------------------------------------------- правый клик

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level,
                                             BlockPos pos, Player player,
                                             BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        trigger((ServerLevel) level, pos, player);
        return InteractionResult.CONSUME;
    }

    // ---------------------------------------------------------------- PUBLIC API

    /**
     * Активирует блок-спаунер: определяет источник структуры и делегирует размещение.
     *
     * <p>Порядок проверки:</p>
     * <ol>
     *   <li>Есть ли зарегистрированный {@link StructureSourceProviderRegistry провайдер}
     *       для данного id — если да, делегируем ему (охватывает {@code excraft:} и
     *       любые другие форматы, добавленные сторонними модами).</li>
     *   <li>Есть ли id в {@link PhysicalStructureRegistry} — обычные структуры мода.</li>
     * </ol>
     *
     * @param level  серверный уровень
     * @param pos    позиция блока-спаунера
     * @param player игрок-инициатор (для сообщений об ошибке), или null
     * @return true если структура успешно размещена или поставлена в очередь
     */
    public boolean trigger(ServerLevel level, BlockPos pos, @Nullable Player player) {
        if (!(level.getBlockEntity(pos) instanceof StructureSpawnerBlockEntity be)) return false;

        ResourceLocation structureId = be.getStructureId();
        if (structureId == null) {
            sendMessage(player, "§c[PhysicalStructures] No structure id set on this spawner.");
            return false;
        }

        // 1 — сторонний провайдер (excraft:, или любой зарегистрированный)
        if (StructureSourceProviderRegistry.isHandled(structureId)) {
            boolean ok = StructureSourceProviderRegistry.place(level, pos.above(), structureId, player);
            if (ok) level.removeBlock(pos, false);
            else sendMessage(player, "§c[PhysicalStructures] Provider failed for: " + structureId);
            return ok;
        }

        // 2 — собственный реестр physical_structures
        if (!PhysicalStructureRegistry.contains(structureId)) {
            sendMessage(player, "§c[PhysicalStructures] Unknown structure id: " + structureId
                    + ". Registered providers: " + StructureSourceProviderRegistry.registeredProviderIds());
            return false;
        }

        PlaceResult result = PhysicalStructurePlacer.place(level, pos.above(), structureId, pos);
        if (result == PlaceResult.SUCCESS) {
            level.removeBlock(pos, false);
            return true;
        }
        sendMessage(player, "§c[PhysicalStructures] Placement failed (" + result + "): " + structureId);
        return false;
    }

    /**
     * Размещает блок-спаунер, привязывает к нему структуру и сразу активирует —
     * всё в один вызов. Поддерживает все зарегистрированные провайдеры.
     */
    public static boolean placeAndTrigger(ServerLevel level, BlockPos pos,
                                           ResourceLocation structureId) {
        level.setBlock(pos, ModBlocks.STRUCTURE_SPAWNER.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof StructureSpawnerBlockEntity be)) {
            level.removeBlock(pos, false);
            return false;
        }
        be.setStructureId(structureId);
        return ModBlocks.STRUCTURE_SPAWNER.get().trigger(level, pos, null);
    }

    // ---------------------------------------------------------------- helpers

    private static void sendMessage(@Nullable Player player, String text) {
        if (player != null) player.sendSystemMessage(Component.literal(text));
    }
}
