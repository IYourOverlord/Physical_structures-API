package org.exampl.physical_structures.compat.toolgun;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import org.exampl.physical_structures.PhysicalStructures;
import org.exampl.physical_structures.api.provider.StructureSourceProvider;
import org.exampl.physical_structures.api.provider.StructureSourceProviderRegistry;

import javax.annotation.Nullable;

/**
 * Интеграция с {@code create_aeronautics_toolgun}: регистрируется как
 * {@link StructureSourceProvider} для namespace {@code "excraft"}.
 *
 * <p>Вместо хардкода в {@code StructureSpawnerBlock} теперь мод просто
 * вызывает {@link StructureSourceProviderRegistry#place}, и этот провайдер
 * сам подхватывает нужные id.</p>
 *
 * <p><b>Опциональная зависимость:</b> если Toolgun не установлен —
 * провайдер не регистрируется вообще; {@code StructureSourceProviderRegistry}
 * вернёт {@code false} для {@code excraft:} id с понятным логом.</p>
 */
public final class ExcraftCompat implements StructureSourceProvider {

    private static final String TOOLGUN_MOD_ID = "create_aeronautics_toolgun";
    private static boolean initialized = false;

    private ExcraftCompat() {}

    /** Вызывается из конструктора мода — если Toolgun установлен, регистрирует провайдер. */
    public static void initIfPresent() {
        if (initialized) return;
        initialized = true;

        if (!ModList.get().isLoaded(TOOLGUN_MOD_ID)) {
            PhysicalStructures.LOGGER.info(
                    "[PhysicalStructures] {} not found — excraft: structures will not be available.",
                    TOOLGUN_MOD_ID);
            return;
        }

        StructureSourceProviderRegistry.register(new ExcraftCompat());
        PhysicalStructures.LOGGER.info(
                "[PhysicalStructures] {} detected — excraft: provider registered.", TOOLGUN_MOD_ID);
    }

    /** @deprecated Используйте {@link StructureSourceProviderRegistry#isHandled(ResourceLocation)} */
    @Deprecated
    public static boolean isAvailable() {
        return ModList.get().isLoaded(TOOLGUN_MOD_ID);
    }

    /** @deprecated Используйте {@link StructureSourceProviderRegistry#place} */
    @Deprecated
    public static boolean trigger(ServerLevel level, BlockPos origin,
                                   ResourceLocation id, @Nullable Player player) {
        return StructureSourceProviderRegistry.place(level, origin, id, player);
    }

    /** @deprecated Используйте {@link StructureSourceProviderRegistry#isHandled(ResourceLocation)} */
    @Deprecated
    public static boolean isExcraftId(ResourceLocation id) {
        return ExcraftStructureHandler.isExcraftId(id);
    }

    // ---------------------------------------------------------------- StructureSourceProvider impl

    @Override
    public String providerId() { return "physical_structures:excraft_toolgun"; }

    @Override
    public boolean supports(ResourceLocation id) {
        return ExcraftStructureHandler.isExcraftId(id);
    }

    @Override
    public boolean place(ServerLevel level, BlockPos origin, ResourceLocation id, @Nullable Player player) {
        return ExcraftStructureHandler.triggerExcraft(level, origin, id, player);
    }
}
