package org.exampl.physical_structures.init;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import org.exampl.physical_structures.PhysicalStructures;
import org.exampl.physical_structures.block.StructureSpawnerItem;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, PhysicalStructures.MOD_ID);

    /**
     * Единственный предмет мода.
     * physical_structures:structure_spawner
     *
     * Получить предмет с нужной структурой:
     *   /give @s physical_structures:structure_spawner[physical_structures:structure_id="mymod:my_cannon"]
     *
     * Или из кода другого мода:
     *   StructureSpawnerItem.forStructure(ResourceLocation.fromNamespaceAndPath("mymod","my_cannon"))
     */
    public static final DeferredHolder<Item, StructureSpawnerItem> STRUCTURE_SPAWNER =
            ITEMS.register("structure_spawner",
                    () -> new StructureSpawnerItem(new Item.Properties().stacksTo(64)));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
