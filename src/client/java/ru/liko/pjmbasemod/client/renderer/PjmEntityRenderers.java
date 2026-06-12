package ru.liko.pjmbasemod.client.renderer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.renderer.entity.NotebookRenderer;
import ru.liko.pjmbasemod.client.renderer.entity.QuartermasterRenderer;
import ru.liko.pjmbasemod.common.init.PjmEntities;

@EventBusSubscriber(modid = Pjmbasemod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PjmEntityRenderers {

    private PjmEntityRenderers() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PjmEntities.NOTEBOOK.get(), NotebookRenderer::new);
        event.registerEntityRenderer(PjmEntities.QUARTERMASTER.get(), QuartermasterRenderer::new);
    }
}
