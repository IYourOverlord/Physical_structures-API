package org.exampl.physical_structures.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.exampl.physical_structures.PhysicalStructures;
import org.exampl.physical_structures.structure.PendingAssemblyQueue;

// В NeoForge 21.1 game bus является умолчанием — параметр bus не нужен
@EventBusSubscriber(modid = PhysicalStructures.MOD_ID)
public class ServerTickListener {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PendingAssemblyQueue.tick(event.getServer());
    }
}
