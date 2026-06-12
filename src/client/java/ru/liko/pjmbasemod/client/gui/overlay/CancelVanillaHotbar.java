package ru.liko.pjmbasemod.client.gui.overlay;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * Скрывает ванильные слои, которые мы перерисовываем сами:
 *   • HOTBAR — сдвигается за кадр (не отменяется, чтобы не ломать Voice Chat)
 *   • EXPERIENCE_BAR / EXPERIENCE_LEVEL — полностью отменяются
 *   • TAB_LIST — отменяется (наш {@link CustomTabOverlay#LAYER} рендерит по TAB)
 *   • TaCZ HUD (если установлен) — отменяется для чистого экрана
 */
@EventBusSubscriber(modid = Pjmbasemod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class CancelVanillaHotbar {

    private static final int HOTBAR_OFFSCREEN_OFFSET = 1000;

    private CancelVanillaHotbar() {}

    @SubscribeEvent
    public static void onPre(RenderGuiLayerEvent.Pre e) {
        var name = e.getName();

        if (name.equals(VanillaGuiLayers.HOTBAR)) {
            e.getGuiGraphics().pose().pushPose();
            e.getGuiGraphics().pose().translate(0, HOTBAR_OFFSCREEN_OFFSET, 0);
            return;
        }

        if (name.equals(VanillaGuiLayers.EXPERIENCE_BAR)
                || name.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)
                || name.equals(VanillaGuiLayers.TAB_LIST)) {
            e.setCanceled(true);
            return;
        }

        // Compat: скрываем HUD стороннего гана-фреймворка TaCZ, если установлен.
        if ("tacz".equals(name.getNamespace()) && "tac_gun_hud_overlay".equals(name.getPath())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPost(RenderGuiLayerEvent.Post e) {
        if (e.getName().equals(VanillaGuiLayers.HOTBAR)) {
            e.getGuiGraphics().pose().popPose();
        }
    }
}
