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
import ru.liko.pjmbasemod.client.gui.screen.WarehouseScreen;
import ru.liko.pjmbasemod.client.config.ClientHudConfig;
import ru.liko.pjmbasemod.client.customization.ClientSkinState;
import ru.liko.pjmbasemod.client.inventory.LockedSlotsClientState;
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
import ru.liko.pjmbasemod.common.network.packet.WarehouseSyncPacket;
import ru.liko.pjmbasemod.client.frontline.ClientFrontlineState;
import ru.liko.pjmbasemod.client.gui.overlay.NotificationOverlay;
import ru.liko.pjmbasemod.client.rank.ClientRankState;
import ru.liko.pjmbasemod.client.region.ClientRegionState;
import ru.liko.pjmbasemod.client.radio.RadioManager;
import ru.liko.pjmbasemod.client.role.ClientRoleState;
import ru.liko.pjmbasemod.common.network.ClientPacketProxy;
import ru.liko.pjmbasemod.common.network.packet.FactionCommanderSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.FrontlineHudPacket;
import ru.liko.pjmbasemod.common.network.packet.FrontlineMapSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;
import ru.liko.pjmbasemod.common.network.packet.RadioEventPacket;
import ru.liko.pjmbasemod.common.network.packet.RegionMapSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RankSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RankXpPacket;
import ru.liko.pjmbasemod.common.network.packet.RoleAccessSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RoleSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.SyncPjmDataPacket;
import ru.liko.pjmbasemod.common.network.packet.TargetRoleAccessPacket;
import ru.liko.pjmbasemod.client.gui.RadialMenuScreen;

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
    public void regionMapSync(RegionMapSyncPacket payload) {
        ClientRegionState.updateMap(payload);
    }

    @Override
    public void frontlineHud(FrontlineHudPacket payload) {
        ClientFrontlineState.updateHud(payload);
    }

    @Override
    public void frontlineMapSync(FrontlineMapSyncPacket payload) {
        ClientFrontlineState.updateMap(payload);
    }

    @Override
    public void openGarage(OpenGaragePacket payload) {
        GarageScreen.open(payload.snapshot());
    }

    @Override
    public void garageSync(GarageSyncPacket payload) {
        if (Minecraft.getInstance().screen instanceof GarageScreen screen) {
            screen.updateSnapshot(payload.snapshot());
        } else {
            GarageScreen.open(payload.snapshot());
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
    public void roleAccessSync(RoleAccessSyncPacket payload) {
        // UUID-фильтр не нужен: пакет без playerId, шлётся только владельцу (sendToPlayer) и описывает его собственные роли.
        ClientRoleState.updateAccess(payload);
    }

    @Override
    public void targetRoleAccess(TargetRoleAccessPacket payload) {
        ClientRoleState.updateTargetAccess(payload);
        // Если открыто радиальное меню — перестроить, чтобы погасить недоступные цели роли.
        if (Minecraft.getInstance().screen instanceof RadialMenuScreen radial) {
            radial.onTargetRoleAccessUpdated();
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
}
