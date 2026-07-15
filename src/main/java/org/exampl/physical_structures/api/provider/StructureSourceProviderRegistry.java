package org.exampl.physical_structures.api.provider;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.exampl.physical_structures.PhysicalStructures;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * PUBLIC API — реестр {@link StructureSourceProvider}.
 *
 * <p>Заменяет хардкод {@code if (namespace.equals("excraft"))} в
 * {@code StructureSpawnerBlock}. Провайдеры проверяются в порядке регистрации;
 * побеждает первый, чей {@link StructureSourceProvider#supports} вернёт {@code true}.</p>
 *
 * <pre>{@code
 * // Регистрация в конструкторе мода:
 * StructureSourceProviderRegistry.register(new MyBlueprintProvider());
 *
 * // Проверка, есть ли провайдер для данного id:
 * boolean handled = StructureSourceProviderRegistry.isHandled(id);
 *
 * // Размещение через реестр:
 * boolean ok = StructureSourceProviderRegistry.place(level, origin, id, player);
 * }</pre>
 */
public final class StructureSourceProviderRegistry {

    private static final List<StructureSourceProvider> PROVIDERS = new ArrayList<>();

    private StructureSourceProviderRegistry() {}

    /**
     * Регистрирует нового провайдера. Безопасно вызывать из {@code FMLCommonSetupEvent}
     * или конструктора мода.
     */
    public static synchronized void register(StructureSourceProvider provider) {
        PROVIDERS.add(provider);
        PhysicalStructures.LOGGER.info(
                "[PhysicalStructures] Registered StructureSourceProvider: {}", provider.providerId());
    }

    /**
     * Есть ли хотя бы один провайдер, поддерживающий данный id.
     */
    public static boolean isHandled(ResourceLocation id) {
        return PROVIDERS.stream().anyMatch(p -> p.supports(id));
    }

    /**
     * Находит первый подходящий провайдер и вызывает {@link StructureSourceProvider#place}.
     *
     * @return {@code true} если нашёлся провайдер и он успешно разместил структуру
     */
    public static boolean place(ServerLevel level, BlockPos origin,
                                 ResourceLocation id, @Nullable Player player) {
        for (StructureSourceProvider provider : PROVIDERS) {
            if (!provider.supports(id)) continue;
            PhysicalStructures.LOGGER.debug(
                    "[PhysicalStructures] Delegating '{}' to provider '{}'.", id, provider.providerId());
            return provider.place(level, origin, id, player);
        }
        PhysicalStructures.LOGGER.warn(
                "[PhysicalStructures] No provider found for id '{}'. Registered providers: {}",
                id, PROVIDERS.stream().map(StructureSourceProvider::providerId).toList());
        return false;
    }

    /** Список всех зарегистрированных провайдеров (read-only, для отладки). */
    public static List<String> registeredProviderIds() {
        return PROVIDERS.stream().map(StructureSourceProvider::providerId).toList();
    }
}
