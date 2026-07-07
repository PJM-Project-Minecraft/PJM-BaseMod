package ru.liko.pjmbasemod.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
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
import ru.liko.pjmbasemod.client.inventory.LockedSlotsClientState;
import ru.liko.pjmbasemod.client.region.ClientRegionState;
import ru.liko.pjmbasemod.client.radio.RadioManager;
import ru.liko.pjmbasemod.client.radio.VoiceChatActionBarHud;
import ru.liko.pjmbasemod.client.radio.VoiceChatBridge;
import ru.liko.pjmbasemod.client.role.ClientRoleState;
import ru.liko.pjmbasemod.common.chat.ChatMode;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.ChangeChatModePacket;
import ru.liko.pjmbasemod.common.network.packet.RequestModerationPacket;

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
        LockedSlotsClientState.reset();
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

        // F1 (скрытие HUD) — только для OP. У обычных игроков сразу возвращаем HUD,
        // чтобы нельзя было спрятать интерфейс (уровень прав синкается с сервера).
        if (mc.options.hideGui && !mc.player.hasPermissions(2)) {
            mc.options.hideGui = false;
        }

        if (mc.screen != null) return;

        while (ModKeyBindings.OPEN_RADIAL_MENU.consumeClick()) {
            mc.setScreen(new RadialMenuScreen(mc.player));
        }
        while (ModKeyBindings.CYCLE_CHAT_MODE.consumeClick()) {
            ChatMode m = ClientChatModeState.cycle();
            PjmNetworking.sendToServer(ChangeChatModePacket.setMode(m));
        }
        while (ModKeyBindings.OPEN_MODERATION.consumeClick()) {
            PjmNetworking.sendToServer(RequestModerationPacket.INSTANCE);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        VoiceChatBridge.enforceIfActive();
    }

    /** Ванильная текстура барьера — маркер заблокированного слота. */
    private static final ResourceLocation BARRIER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/item/barrier.png");

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!LockedSlotsClientState.isActive()) return;
        if (pjm_isCreative()) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> acs)) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int left = acs.getGuiLeft();
        int top = acs.getGuiTop();
        for (Slot slot : acs.getMenu().slots) {
            if (!(slot.container instanceof Inventory)) continue;
            if (!LockedSlotsClientState.isLocked(slot.getContainerSlot())) continue;
            int x = left + slot.x;
            int y = top + slot.y;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300.0F);
            graphics.blit(BARRIER_TEXTURE, x, y, 0, 0, 16, 16, 16, 16);
            graphics.pose().popPose();
        }
    }

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!LockedSlotsClientState.isActive() || !LockedSlotsClientState.cancelClicks()) return;
        if (pjm_isCreative()) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> acs)) return;
        Slot slot = acs.getSlotUnderMouse();
        if (slot != null && slot.container instanceof Inventory
                && LockedSlotsClientState.isLocked(slot.getContainerSlot())) {
            event.setCanceled(true);
        }
    }

    private static boolean pjm_isCreative() {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
    }
}
