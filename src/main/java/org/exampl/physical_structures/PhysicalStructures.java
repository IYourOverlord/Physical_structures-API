package org.exampl.physical_structures;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.exampl.physical_structures.api.PhysicalStructureDefinition;
import org.exampl.physical_structures.api.PhysicalStructureRegistry;
import org.exampl.physical_structures.compat.sable.SableCompat;
import org.exampl.physical_structures.compat.toolgun.ExcraftCompat;
import org.exampl.physical_structures.init.ModBlockEntities;
import org.exampl.physical_structures.init.ModBlocks;
import org.exampl.physical_structures.init.ModDataComponents;
import org.exampl.physical_structures.init.ModItems;
import org.exampl.physical_structures.worldgen.ModFeatures;
import org.slf4j.Logger;

@Mod(PhysicalStructures.MOD_ID)
public class PhysicalStructures {

    public static final String MOD_ID = "physical_structures";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PhysicalStructures(IEventBus modEventBus) {
        // Регистрация блоков/предметов/etc
        ModDataComponents.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);

        // Ворлдген Feature (PhysicalStructureFeature)
        ModFeatures.register(modEventBus);

        // Регистрация встроенных структур
        PhysicalStructureRegistry.register(new PhysicalStructureDefinition(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "gun6"),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "structures/gun6.nbt")
        ));

        // Инициализация опциональных compat-провайдеров на этапе common setup
        // (после того как все моды зарегистрировались)
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Регистрирует ExcraftCompat как StructureSourceProvider,
            // если create_aeronautics_toolgun установлен
            ExcraftCompat.initIfPresent();

            // Регистрирует SableCompat как StructureSourceProvider (namespace "sable"),
            // если sable-schematic-api установлен — см. compat/sable/SableCompat.java
            SableCompat.initIfPresent();
        });
    }
}
