package ru.liko.pjmbasemod.client.gui.overlay;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * Регистрация HUD-оверлеев. NeoForge 1.21.1 — {@link RegisterGuiLayersEvent}.
 *
 * Расположение:
 *   • {@link CustomHotbarOverlay}, {@link HudOverlay#COMPASS_OVERLAY},
 *     {@link GameModeHudOverlay#OVERLAY}, {@link VoiceChatOverlay} — под ванильным hotbar
 *   • {@link NotificationOverlay} — над hotbar
 *   • {@link CustomTabOverlay#LAYER} — над ванильным tab list (показывается при удержании TAB)
 */
@EventBusSubscriber(modid = Pjmbasemod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientOverlays {

    private ClientOverlays() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, path);
    }

    @SubscribeEvent
    public static void onRegister(RegisterGuiLayersEvent e) {
        e.registerBelow(VanillaGuiLayers.HOTBAR, id("custom_hotbar"),     CustomHotbarOverlay.INSTANCE);
        e.registerBelow(VanillaGuiLayers.HOTBAR, id("compass_overlay"),   HudOverlay.COMPASS_OVERLAY);
        e.registerBelow(VanillaGuiLayers.HOTBAR, id("voicechat_overlay"), VoiceChatOverlay.INSTANCE);
        e.registerAbove(VanillaGuiLayers.HOTBAR, id("capturepoint_hud"), CapturePointHudOverlay.OVERLAY);
        e.registerAbove(VanillaGuiLayers.HOTBAR, id("rank_hud"),          RankHudOverlay.OVERLAY);
        e.registerAbove(VanillaGuiLayers.HOTBAR, id("faction_order_hud"), FactionOrderHudOverlay.OVERLAY);
        e.registerAbove(VanillaGuiLayers.HOTBAR, id("notifications"),     NotificationOverlay.OVERLAY);
        e.registerAbove(VanillaGuiLayers.HOTBAR, id("beta_watermark"),    BetaWatermarkOverlay.OVERLAY);
        e.registerAbove(VanillaGuiLayers.TAB_LIST, id("tactical_tab"), TacticalTabOverlay.LAYER);
    }
}
