package ru.liko.pjmbasemod.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.chat.ClientChatModeState;
import ru.liko.pjmbasemod.client.faction.ClientFactionCommanderState;
import ru.liko.pjmbasemod.client.frontline.ClientFrontlineState;
import ru.liko.pjmbasemod.client.frontline.journeymap.FrontlineJourneyMapBridge;
import ru.liko.pjmbasemod.client.gui.RadialMenuScreen;
import ru.liko.pjmbasemod.client.gui.screen.TacticalMainMenuScreen;
import ru.liko.pjmbasemod.client.input.ModKeyBindings;
import ru.liko.pjmbasemod.client.region.ClientRegionState;
import ru.liko.pjmbasemod.client.radio.RadioManager;
import ru.liko.pjmbasemod.client.radio.VoiceChatActionBarHud;
import ru.liko.pjmbasemod.client.radio.VoiceChatBridge;
import ru.liko.pjmbasemod.client.role.ClientRoleState;
import ru.liko.pjmbasemod.common.chat.ChatMode;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.ChangeChatModePacket;

@EventBusSubscriber(modid = Pjmbasemod.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private static boolean firstTitleReplaced = false;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof TitleScreen && !firstTitleReplaced) {
            firstTitleReplaced = true;
            event.setNewScreen(new TacticalMainMenuScreen());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ru.liko.pjmbasemod.client.gui.overlay.HudOverlay.reset();
        ClientRegionState.reset();
        ClientFrontlineState.reset();
        ClientFactionCommanderState.reset();
        ClientRoleState.reset();
        FrontlineJourneyMapBridge.onLogout();
        RadioManager.get().reset();
        firstTitleReplaced = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        FrontlineJourneyMapBridge.onClientTick();
        if (mc.player == null) {
            VoiceChatActionBarHud.tick(mc);
            return;
        }

        RadioManager.get().tick();
        VoiceChatActionBarHud.tick(mc);
        if (mc.screen != null) return;

        while (ModKeyBindings.OPEN_RADIAL_MENU.consumeClick()) {
            mc.setScreen(new RadialMenuScreen(mc.player));
        }
        while (ModKeyBindings.CYCLE_CHAT_MODE.consumeClick()) {
            ChatMode m = ClientChatModeState.cycle();
            PjmNetworking.sendToServer(ChangeChatModePacket.setMode(m));
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        VoiceChatBridge.enforceIfActive();
    }
}
