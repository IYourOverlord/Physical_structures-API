package org.exampl.physical_structures.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.exampl.physical_structures.PhysicalStructures;

/**
 * Регистрация Features мода для ворлдген-интеграции.
 * Вызывается из конструктора {@link PhysicalStructures}.
 */
public final class ModFeatures {

    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, PhysicalStructures.MOD_ID);

    /**
     * Feature для размещения физических структур в ворлдгене.
     * Используйте id {@code physical_structures:physical_structure} в JSON датапаке.
     */
    public static final DeferredHolder<Feature<?>, PhysicalStructureFeature> PHYSICAL_STRUCTURE =
            FEATURES.register("physical_structure",
                    () -> new PhysicalStructureFeature(PhysicalStructureFeatureConfig.CODEC.codec()));

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }

    private ModFeatures() {}
}
