package ru.liko.pjmbasemod.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
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
import ru.liko.pjmbasemod.client.capturepoint.ClientCapturePointState;
import ru.liko.pjmbasemod.client.faction.ClientFactionCommanderState;
import ru.liko.pjmbasemod.client.gui.RadialMenuScreen;
import ru.liko.pjmbasemod.client.gui.screen.PjmDeathScreen;
import ru.liko.pjmbasemod.client.gui.screen.TacticalMainMenuScreen;
import ru.liko.pjmbasemod.client.gui.screen.TacticalPauseMenuScreen;
import ru.liko.pjmbasemod.client.input.ModKeyBindings;
import ru.liko.pjmbasemod.client.inventory.LockedSlotsClientState;
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

    private ClientEvents() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        // Ни один путь возврата из дочерних экранов или после отключения
        // не должен показывать ванильное меню и его панораму.
        if (event.getNewScreen() instanceof TitleScreen) {
            event.setNewScreen(new TacticalMainMenuScreen());
        }
        // Замена ванильного ESC-меню паузы на tactical-стиль (с blur-фоном по миру).
        // Только когда showPauseMenu=true (само меню, не оверлей-«пауза» в singleplayer).
        if (event.getNewScreen() instanceof PauseScreen pause && pause.showsPauseMenu()) {
            event.setNewScreen(new TacticalPauseMenuScreen());
        }
        // Кинематографичный экран смерти вместо ванильного. В хардкоре не трогаем:
        // там кнопка «наблюдать» и своя логика выхода, ради которых не стоит городить ветку.
        if (event.getNewScreen() instanceof DeathScreen) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof PjmDeathScreen) {
                // Наш экран уже открыт мгновенно по DeathScreenPacket — не пересоздаём,
                // иначе сбросились бы состояния кнопок.
                event.setCanceled(true);
            } else if (mc.level != null && !mc.level.getLevelData().isHardcore()) {
                event.setNewScreen(new PjmDeathScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // Пиксельный курсор — глобально во всех GUI игры (как ресурс-пак), не только на экранах мода.
        ru.liko.pjmbasemod.client.gui.PjmCursor.applyDefault();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ru.liko.pjmbasemod.client.gui.overlay.HudOverlay.reset();
        ClientCapturePointState.reset();
        ru.liko.pjmbasemod.client.campaign.ClientCampaignState.reset();
        ClientFactionCommanderState.reset();
        ClientRoleState.reset();
        ru.liko.pjmbasemod.client.serverevent.ClientServerEventState.clear();
        ru.liko.pjmbasemod.client.serverevent.SignalHuntActionBarHud.reset();
        RadioManager.get().reset();
        LockedSlotsClientState.reset();
        ru.liko.pjmbasemod.client.gui.screen.WelcomeGuideScreen.reset();
        ru.liko.pjmbasemod.client.worldmap.WorldMapEngine.get().reset();
        ru.liko.pjmbasemod.client.basezone.ClientBaseZoneState.reset();
        ru.liko.pjmbasemod.client.worldmap.edit.CapturePointEditor.get().onLogout();
        ru.liko.pjmbasemod.client.radiospawn.ClientRadioCarrierState.reset();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            VoiceChatActionBarHud.tick(mc);
            return;
        }

        RadioManager.get().tick();
        VoiceChatActionBarHud.tick(mc);
        ru.liko.pjmbasemod.client.serverevent.SignalHuntActionBarHud.tick(mc);

        // Скан мировой карты идёт всегда (и с открытым экраном карты) — до guard'а на mc.screen ниже.
        ru.liko.pjmbasemod.client.worldmap.WorldMapEngine.get().onClientTick(mc);

        // F1 (скрытие HUD) — только для OP. У обычных игроков сразу возвращаем HUD,
        // чтобы нельзя было спрятать интерфейс (уровень прав синкается с сервера).
        if (mc.options.hideGui && !mc.player.hasPermissions(2)) {
            mc.options.hideGui = false;
        }

        // Руководство по серверу открываем только когда игрок реально в мире (не поверх
        // экрана выбора фракции, который FactionMenuService держит открытым до выбора).
        if (mc.screen == null && ru.liko.pjmbasemod.client.gui.screen.WelcomeGuideScreen.consumePending()) {
            mc.setScreen(new ru.liko.pjmbasemod.client.gui.screen.WelcomeGuideScreen());
            return;
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
        while (ModKeyBindings.OPEN_WORLD_MAP.consumeClick()) {
            mc.setScreen(new ru.liko.pjmbasemod.client.worldmap.gui.MapScreen());
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
