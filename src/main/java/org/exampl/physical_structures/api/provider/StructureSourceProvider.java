package org.exampl.physical_structures.api.provider;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * PUBLIC API — интерфейс для сторонних провайдеров структур.
 *
 * <p>Позволяет другим модам добавлять собственные источники структур
 * (форматы блупринтов, генераторы и т.д.) в {@code physical_structures}
 * без правки его ядра. Вместо хардкода {@code if (namespace.equals("excraft"))}
 * мод теперь перебирает зарегистрированных провайдеров.</p>
 *
 * <h3>Как добавить своего провайдера</h3>
 * <pre>{@code
 * // В конструкторе вашего мода (или FMLCommonSetupEvent):
 * StructureSourceProviderRegistry.register(new MyBlueprintProvider());
 *
 * // Реализация:
 * public class MyBlueprintProvider implements StructureSourceProvider {
 *
 *     @Override
 *     public boolean supports(ResourceLocation id) {
 *         return "myblueprints".equals(id.getNamespace());
 *     }
 *
 *     @Override
 *     public boolean place(ServerLevel level, BlockPos origin,
 *                          ResourceLocation id, @Nullable Player player) {
 *         return MyBlueprintSystem.spawn(level, origin, id.getPath());
 *     }
 *
 *     @Override
 *     public String providerId() { return "mymod:blueprint_provider"; }
 * }
 * }</pre>
 */
public interface StructureSourceProvider {

    /**
     * Уникальный идентификатор провайдера (для логов и отладки).
     * Рекомендуемый формат: {@code "modid:provider_name"}.
     */
    String providerId();

    /**
     * Поддерживает ли этот провайдер данный id структуры.
     * Обычно проверяет namespace: {@code "excraft".equals(id.getNamespace())}.
     */
    boolean supports(ResourceLocation id);

    /**
     * Размещает структуру с данным id в указанной позиции.
     *
     * @param level   серверный уровень
     * @param origin  позиция размещения
     * @param id      ResourceLocation структуры (тот же, что был передан в {@link #supports})
     * @param player  игрок-инициатор, или {@code null} если размещение программное
     * @return {@code true} если структура успешно размещена или поставлена в очередь
     */
    boolean place(ServerLevel level, BlockPos origin, ResourceLocation id, @Nullable Player player);
}
