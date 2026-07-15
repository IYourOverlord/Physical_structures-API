package org.exampl.physical_structures.init;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import com.mojang.serialization.Codec;
import org.exampl.physical_structures.PhysicalStructures;

public class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, PhysicalStructures.MOD_ID);

    /**
     * Хранит id структуры как строку в ItemStack.
     * Ключ компонента: physical_structures:structure_id
     *
     * Использование в /give:
     * /give @s physical_structures:structure_spawner[physical_structures:structure_id="mymod:my_cannon"]
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> STRUCTURE_ID =
            DATA_COMPONENTS.register("structure_id", () ->
                    DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build());

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
