package org.exampl.physical_structures.structure;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Holds structures that are placed in the world but whose physics assembly
 * is delayed. Ticked once per server tick from
 * {@link org.exampl.physical_structures.event.ServerTickListener}.
 */
public final class PendingAssemblyQueue {

    private static final List<PendingAssembly> QUEUE = new ArrayList<>();

    private PendingAssemblyQueue() {}

    public static void add(PendingAssembly pending) {
        QUEUE.add(pending);
    }

    public static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty()) return;

        Iterator<PendingAssembly> it = QUEUE.iterator();
        while (it.hasNext()) {
            PendingAssembly pending = it.next();
            if (!pending.tick()) continue;

            it.remove();
            ServerLevel level = server.getLevel(pending.dimension());
            if (level == null) continue;

            StructurePlacer.performAssembly(
                    level, pending.anchor(), pending.definition(),
                    pending.blocks(), pending.boundingBox(),
                    pending.spawnerPos(), pending.placementId());
        }
    }

    public static int pendingCount() { return QUEUE.size(); }
}
