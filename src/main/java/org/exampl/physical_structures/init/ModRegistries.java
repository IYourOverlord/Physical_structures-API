package org.exampl.physical_structures.init;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

public class ModRegistries {
    public static void register(IEventBus modEventBus) {
        ModItems.ITEMS.register(modEventBus);
    }
}
