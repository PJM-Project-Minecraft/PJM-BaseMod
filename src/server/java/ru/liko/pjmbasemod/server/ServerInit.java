package ru.liko.pjmbasemod.server;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * Dedicated-server-only hook. Заметь: код, нужный и одиночке и dedicated server, живёт в main SS.
 * Здесь — только то, что должно выполняться исключительно на dedicated.
 */
@EventBusSubscriber(modid = Pjmbasemod.MODID, value = Dist.DEDICATED_SERVER)
public final class ServerInit {

    private ServerInit() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Pjmbasemod.LOGGER.info("PJM-BaseMod: dedicated server is up.");
    }
}
