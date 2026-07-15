package org.exampl.physical_structures.init;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import org.exampl.physical_structures.PhysicalStructures;
import org.exampl.physical_structures.block.StructureSpawnerBlockEntity;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PhysicalStructures.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StructureSpawnerBlockEntity>>
            STRUCTURE_SPAWNER = BLOCK_ENTITIES.register("structure_spawner", () ->
                    BlockEntityType.Builder
                            .of(StructureSpawnerBlockEntity::new,
                                ModBlocks.STRUCTURE_SPAWNER.get())
                            .build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
