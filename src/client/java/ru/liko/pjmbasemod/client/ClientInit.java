package ru.liko.pjmbasemod.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.network.ClientPacketHandlersImpl;
import ru.liko.pjmbasemod.common.network.PjmNetworking;

@EventBusSubscriber(modid = Pjmbasemod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientInit {

    private ClientInit() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        PjmNetworking.setClientProxy(new ClientPacketHandlersImpl());
        event.enqueueWork(() -> Pjmbasemod.LOGGER.info("PJM-BaseMod client setup complete."));
    }
}
