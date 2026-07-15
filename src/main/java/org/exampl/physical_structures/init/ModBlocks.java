package org.exampl.physical_structures.init;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import org.exampl.physical_structures.PhysicalStructures;
import org.exampl.physical_structures.block.StructureSpawnerBlock;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, PhysicalStructures.MOD_ID);

    /**
     * Единственный блок мода.
     * physical_structures:structure_spawner
     */
    public static final DeferredHolder<Block, StructureSpawnerBlock> STRUCTURE_SPAWNER =
            BLOCKS.register("structure_spawner", () ->
                    new StructureSpawnerBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(1.5f)
                                    .sound(SoundType.STONE)
                                    .noOcclusion()
                    ));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
