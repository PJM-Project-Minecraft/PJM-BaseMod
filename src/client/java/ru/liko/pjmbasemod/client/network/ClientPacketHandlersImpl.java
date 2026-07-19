package ru.liko.pjmbasemod.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.liko.pjmbasemod.client.chat.ClientChatModeState;
import ru.liko.pjmbasemod.client.faction.ClientFactionCommanderState;
import ru.liko.pjmbasemod.client.faction.ClientFactionOrderState;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;
import ru.liko.pjmbasemod.client.gui.overlay.RankHudOverlay;
import ru.liko.pjmbasemod.client.gui.screen.FactionManagementScreen;
import ru.liko.pjmbasemod.client.gui.screen.FactionSelectionScreen;
import ru.liko.pjmbasemod.client.gui.screen.GarageScreen;
import ru.liko.pjmbasemod.client.gui.screen.ModerationScreen;
import ru.liko.pjmbasemod.client.gui.screen.ReportAdminScreen;
import ru.liko.pjmbasemod.client.gui.screen.ReportScreen;
import ru.liko.pjmbasemod.common.network.packet.OpenReportsPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenWelcomeGuidePacket;
import ru.liko.pjmbasemod.common.network.packet.PlayerReportThreadPacket;
import ru.liko.pjmbasemod.common.network.packet.ReportSyncPacket;
import ru.liko.pjmbasemod.client.gui.screen.WarehouseScreen;
import ru.liko.pjmbasemod.client.gui.screen.WelcomeGuideScreen;
import ru.liko.pjmbasemod.client.config.ClientHudConfig;
import ru.liko.pjmbasemod.client.customization.ClientSkinState;
import ru.liko.pjmbasemod.client.inventory.LockedSlotsClientState;
import ru.liko.pjmbasemod.client.moderation.ClientModerationState;
import ru.liko.pjmbasemod.common.customization.PlayerSkinClientCache;
import ru.liko.pjmbasemod.common.network.packet.HudConfigPacket;
import ru.liko.pjmbasemod.common.network.packet.PlayerSkinSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.SkinSelectionSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.FactionManagementSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.FactionOrderSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.LockedSlotsPacket;
import ru.liko.pjmbasemod.common.network.packet.GarageSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenFactionManagementPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenFactionSelectionPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenGaragePacket;
import ru.liko.pjmbasemod.common.network.packet.OpenWarehousePacket;
import ru.liko.pjmbasemod.common.network.packet.OpenModerationPacket;
import ru.liko.pjmbasemod.common.network.packet.WarehouseSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.ModerationSyncPacket;
import ru.liko.pjmbasemod.client.gui.overlay.NotificationOverlay;
import ru.liko.pjmbasemod.client.capturepoint.ClientCapturePointState;
import ru.liko.pjmbasemod.client.rank.ClientRankState;
import ru.liko.pjmbasemod.client.radio.RadioManager;
import ru.liko.pjmbasemod.client.role.ClientRoleState;
import ru.liko.pjmbasemod.common.network.ClientPacketProxy;
import ru.liko.pjmbasemod.common.network.packet.FactionCommanderSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;
import ru.liko.pjmbasemod.common.network.packet.DeathScreenPacket;
import ru.liko.pjmbasemod.common.network.packet.RadioSpawnListPacket;
import ru.liko.pjmbasemod.client.gui.screen.PjmDeathScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import ru.liko.pjmbasemod.common.network.packet.CapturePointMapSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.CapturePointHudPacket;
import ru.liko.pjmbasemod.common.network.packet.RadioEventPacket;
import ru.liko.pjmbasemod.common.network.packet.RankSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RankXpPacket;
import ru.liko.pjmbasemod.common.network.packet.RoleSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.SyncPjmDataPacket;

public final class ClientPacketHandlersImpl implements ClientPacketProxy {

    @Override
    public void syncPlayerData(SyncPjmDataPacket payload) {
        var localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && payload.playerId().equals(localPlayer.getUUID())) {
            ClientChatModeState.setMode(payload.chatModeEnum());
        }
    }

    @Override
    public void notification(NotificationPacket payload) {
        NotificationOverlay.show(payload.title(), payload.subtitle(), null, payload.color(), payload.durationMs());
    }

    @Override
    public void radioEvent(RadioEventPacket payload) {
        if (payload.isStart()) {
            RadioManager.get().onTeammateStartRadio(payload.senderId());
        } else {
            RadioManager.get().onTeammateStopRadio(payload.senderId());
        }
    }

    @Override
    public void openGarage(OpenGaragePacket payload) {
        GarageScreen.open(payload.snapshot());
    }

    @Override
    public void garageSync(GarageSyncPacket payload) {
        // Только обновление уже открытого экрана. Открывает гараж исключительно OpenGaragePacket:
        // sync прилетает и по завершении сборки техники, а он игрока в бою не спрашивает.
        if (Minecraft.getInstance().screen instanceof GarageScreen screen) {
            screen.updateSnapshot(payload.snapshot());
        }
    }

    @Override
    public void openStoreOptions(ru.liko.pjmbasemod.common.network.packet.StoreOptionsPacket payload) {
        if (Minecraft.getInstance().screen instanceof GarageScreen screen) {
            screen.showStoreOptions(payload.options());
        }
    }

    @Override
    public void openSpawnPointOptions(ru.liko.pjmbasemod.common.network.packet.SpawnPointOptionsPacket payload) {
        if (Minecraft.getInstance().screen instanceof GarageScreen screen) {
            screen.showSpawnOptions(payload.instanceId(), payload.points());
        }
    }

    @Override
    public void openWarehouse(OpenWarehousePacket payload) {
        WarehouseScreen.open(payload.snapshot());
    }

    @Override
    public void warehouseSync(WarehouseSyncPacket payload) {
        if (Minecraft.getInstance().screen instanceof WarehouseScreen screen) {
            screen.updateSnapshot(payload.snapshot());
        }
    }

    @Override
    public void rankSync(RankSyncPacket payload) {
        ClientRankState.update(payload);
    }

    @Override
    public void rankXp(RankXpPacket payload) {
        ClientRankState.update(payload);
        if (!payload.enabled()) return;

        if (payload.showXpPopups()) {
            RankHudOverlay.showDelta(payload.delta(), payload.reason(), payload.accentColor());
        }

        if (payload.rankChanged()) {
            String title = payload.promoted() ? "Повышение" : "Понижение";
            String subtitle = "Новое звание: " + payload.displayName();
            NotificationOverlay.show(Component.literal(title), Component.literal(subtitle),
                    RankHudOverlay.icon(payload.icon()), payload.accentColor(), 3500L);
            if (payload.promoted()) {
                PjmUiSounds.playPromoted();
            }
        }
    }

    @Override
    public void roleSync(RoleSyncPacket payload) {
        var localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && payload.playerId().equals(localPlayer.getUUID())) {
            ClientRoleState.update(payload);
        }
    }

    @Override
    public void factionCommanderSync(FactionCommanderSyncPacket payload) {
        var localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && payload.playerId().equals(localPlayer.getUUID())) {
            ClientFactionCommanderState.update(payload);
        }
    }

    @Override
    public void openFactionSelection(OpenFactionSelectionPacket payload) {
        FactionSelectionScreen.open(payload.snapshot());
    }

    @Override
    public void openFactionManagement(OpenFactionManagementPacket payload) {
        FactionManagementScreen.open(payload.snapshot());
    }

    @Override
    public void factionManagementSync(FactionManagementSyncPacket payload) {
        if (Minecraft.getInstance().screen instanceof FactionManagementScreen screen) {
            screen.updateSnapshot(payload.snapshot());
        } else {
            FactionManagementScreen.open(payload.snapshot());
        }
    }

    @Override
    public void factionOrderSync(FactionOrderSyncPacket payload) {
        ClientFactionOrderState.update(payload);
    }

    @Override
    public void campaignSync(ru.liko.pjmbasemod.common.network.packet.CampaignSyncPacket payload) {
        ru.liko.pjmbasemod.client.campaign.ClientCampaignState.update(payload);
    }

    @Override
    public void openModeration(OpenModerationPacket payload) {
        ModerationScreen.open(payload.snapshot());
    }

    @Override
    public void moderationSync(ModerationSyncPacket payload) {
        ClientModerationState.update(payload.snapshot());
        if (Minecraft.getInstance().screen instanceof ModerationScreen screen) {
            screen.updateSnapshot(payload.snapshot());
        }
    }

    @Override
    public void openReports(OpenReportsPacket payload) {
        ReportAdminScreen.open(payload.snapshot());
    }

    @Override
    public void reportSync(ReportSyncPacket payload) {
        if (Minecraft.getInstance().screen instanceof ReportAdminScreen screen) {
            screen.updateSnapshot(payload.snapshot());
        }
    }

    @Override
    public void playerReportThread(PlayerReportThreadPacket payload) {
        if (Minecraft.getInstance().screen instanceof ReportScreen screen) {
            screen.updateThread(payload.thread());
        } else {
            ReportScreen.open(payload.thread());
        }
    }

    @Override
    public void eventMapSync(ru.liko.pjmbasemod.common.network.packet.EventMapSyncPacket payload) {
        ru.liko.pjmbasemod.client.serverevent.ClientServerEventState.update(payload);
    }

    @Override
    public void capturePointMapSync(CapturePointMapSyncPacket payload) {
        ClientCapturePointState.updateMap(payload);
    }

    @Override
    public void capturePointHud(CapturePointHudPacket payload) {
        ClientCapturePointState.updateHud(payload);
    }

    @Override
    public void signalHuntHud(ru.liko.pjmbasemod.common.network.packet.SignalHuntHudPacket payload) {
        ru.liko.pjmbasemod.client.serverevent.ClientSignalHuntState.update(payload);
    }

    @Override
    public void lockedSlots(LockedSlotsPacket payload) {
        LockedSlotsClientState.apply(payload);
    }

    @Override
    public void playerSkinSync(PlayerSkinSyncPacket payload) {
        PlayerSkinClientCache.put(payload.playerId(), payload.skinId());
    }

    @Override
    public void skinSelectionSync(SkinSelectionSyncPacket payload) {
        ClientSkinState.update(payload);
    }

    @Override
    public void hudConfig(HudConfigPacket payload) {
        ClientHudConfig.update(payload);
    }

    @Override
    public void openWelcomeGuide(OpenWelcomeGuidePacket payload) {
        // Не открываем поверх экрана выбора фракции — ждём, пока игрок войдёт в мир
        // (см. ClientEvents.onClientTick: открытие, когда mc.screen == null).
        WelcomeGuideScreen.requestOpen();
    }

    @Override
    public void deathScreen(DeathScreenPacket payload) {
        ItemStack stack = ItemStack.EMPTY;
        if (!payload.itemId().isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(payload.itemId());
            if (id != null) {
                var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item != null) stack = new ItemStack(item);
            }
        }
        PjmDeathScreen.trigger(payload.message(), stack, payload.vehicleId());
        // Резкий кат в чёрное сразу в момент смерти: не ждём ванильный
        // ClientboundPlayerCombatKillPacket (он приходит позже и мир успевает мелькнуть).
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null && mc.level != null && !mc.level.getLevelData().isHardcore()
                    && !(mc.screen instanceof PjmDeathScreen)) {
                mc.setScreen(new PjmDeathScreen());
            }
        });
    }

    @Override
    public void radioSpawnList(RadioSpawnListPacket payload) {
        PjmDeathScreen.setRadioOptions(payload.entries());
    }
}
