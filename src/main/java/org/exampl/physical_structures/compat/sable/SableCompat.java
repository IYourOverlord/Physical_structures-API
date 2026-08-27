package org.exampl.physical_structures.compat.sable;

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
 * Интеграция с {@code sable-schematic-api}: регистрируется как
 * {@link StructureSourceProvider} для namespace {@code "sable"}.
 *
 * <p>Даёт возможность ставить {@code .nbt} блупринты, сохранённые
 * sable-schematic-api (командой, инструментом {@code blueprint_tool} или
 * предметом {@code camera}), средствами physical_structures —
 * {@link org.exampl.physical_structures.block.StructureSpawnerBlock} и
 * {@link org.exampl.physical_structures.init.SpawnStructureItem} — без
 * необходимости открывать UI blueprint_tool или иметь при себе camera на
 * стороне игрока в момент размещения.</p>
 *
 * <p><b>Опциональная зависимость:</b> если sable-schematic-api не установлен —
 * провайдер не регистрируется вообще; {@code StructureSourceProviderRegistry}
 * вернёт {@code false} для {@code sable:} id с понятным логом. Ничего не ломается
 * ни при отсутствии, ни при обновлении sable-schematic-api, поскольку вся
 * интеграция идёт через его публичную команду {@code /sablebp load}, а не через
 * прямую Java-зависимость на его внутренние классы — подробности в
 * {@link SablePlacementBridge}.</p>
 */
public final class SableCompat implements StructureSourceProvider {

    private static final String SABLE_MOD_ID = "sable_schematic_api";
    private static boolean initialized = false;

    private SableCompat() {}

    /** Вызывается из конструктора мода — если sable-schematic-api установлен, регистрирует провайдер. */
    public static void initIfPresent() {
        if (initialized) return;
        initialized = true;

        if (!ModList.get().isLoaded(SABLE_MOD_ID)) {
            PhysicalStructures.LOGGER.info(
                    "[PhysicalStructures] {} not found — sable: structures will not be available.",
                    SABLE_MOD_ID);
            return;
        }

        StructureSourceProviderRegistry.register(new SableCompat());
        PhysicalStructures.LOGGER.info(
                "[PhysicalStructures] {} detected — sable: provider registered.", SABLE_MOD_ID);
    }

    // ---------------------------------------------------------------- StructureSourceProvider impl

    @Override
    public String providerId() { return "physical_structures:sable_schematic_api"; }

    @Override
    public boolean supports(ResourceLocation id) {
        return SableStructureHandler.isSableId(id);
    }

    @Override
    public boolean place(ServerLevel level, BlockPos origin, ResourceLocation id, @Nullable Player player) {
        return SableStructureHandler.triggerSable(level, origin, id, player);
    }
}
