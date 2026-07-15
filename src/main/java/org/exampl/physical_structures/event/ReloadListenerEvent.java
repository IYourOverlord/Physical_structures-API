package org.exampl.physical_structures.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.exampl.physical_structures.PhysicalStructures;
import org.exampl.physical_structures.api.PhysicalStructureJsonLoader;

// В NeoForge 21.1 game bus является умолчанием — параметр bus не нужен
@EventBusSubscriber(modid = PhysicalStructures.MOD_ID)
public class ReloadListenerEvent {

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new PhysicalStructureJsonLoader());
    }
}
