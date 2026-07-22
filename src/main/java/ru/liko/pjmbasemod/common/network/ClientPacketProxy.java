package ru.liko.pjmbasemod.common.network;

import ru.liko.pjmbasemod.common.network.packet.MapMarkerSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;
import ru.liko.pjmbasemod.common.network.packet.FactionManagementSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.FactionOrderSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.HudConfigPacket;
import ru.liko.pjmbasemod.common.network.packet.GarageSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.LockedSlotsPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenFactionManagementPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenFactionInvitePacket;
import ru.liko.pjmbasemod.common.network.packet.OpenFactionSelectionPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenGaragePacket;
import ru.liko.pjmbasemod.common.network.packet.OpenModerationPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenReportsPacket;
import ru.liko.pjmbasemod.common.network.packet.PlayerReportThreadPacket;
import ru.liko.pjmbasemod.common.network.packet.ReportSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.EventMapSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.SignalHuntHudPacket;
import ru.liko.pjmbasemod.common.network.packet.CampaignSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.CapturePointMapSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.CapturePointHudPacket;
import ru.liko.pjmbasemod.common.network.packet.BaseZoneMapSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RadioCarrierSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.DeathScreenPacket;
import ru.liko.pjmbasemod.common.network.packet.RadioSpawnListPacket;
import ru.liko.pjmbasemod.common.network.packet.ModerationSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenWarehousePacket;
import ru.liko.pjmbasemod.common.network.packet.OpenWelcomeGuidePacket;
import ru.liko.pjmbasemod.common.network.packet.PlayerSkinSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.SkinSelectionSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.WarehouseSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.FactionCommanderSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RadioEventPacket;
import ru.liko.pjmbasemod.common.network.packet.RankSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RankXpPacket;
import ru.liko.pjmbasemod.common.network.packet.RoleSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.SpawnPointOptionsPacket;
import ru.liko.pjmbasemod.common.network.packet.StoreOptionsPacket;
import ru.liko.pjmbasemod.common.network.packet.SyncPjmDataPacket;
import ru.liko.pjmbasemod.common.network.packet.MissileCatalogSyncPacket;

public interface ClientPacketProxy {

    ClientPacketProxy NOOP = new ClientPacketProxy() {};

    default void syncPlayerData(SyncPjmDataPacket payload) {}
    default void notification(NotificationPacket payload) {}
    default void radioEvent(RadioEventPacket payload) {}
    default void openGarage(OpenGaragePacket payload) {}
    default void garageSync(GarageSyncPacket payload) {}
    default void openStoreOptions(StoreOptionsPacket payload) {}
    default void openSpawnPointOptions(SpawnPointOptionsPacket payload) {}
    default void rankSync(RankSyncPacket payload) {}
    default void rankXp(RankXpPacket payload) {}
    default void roleSync(RoleSyncPacket payload) {}
    default void factionCommanderSync(FactionCommanderSyncPacket payload) {}
    default void openWarehouse(OpenWarehousePacket payload) {}
    default void warehouseSync(WarehouseSyncPacket payload) {}
    default void openFactionSelection(OpenFactionSelectionPacket payload) {}
    default void openFactionInvite(OpenFactionInvitePacket payload) {}
    default void openFactionManagement(OpenFactionManagementPacket payload) {}
    default void factionManagementSync(FactionManagementSyncPacket payload) {}
    default void factionOrderSync(FactionOrderSyncPacket payload) {}
    default void lockedSlots(LockedSlotsPacket payload) {}
    default void playerSkinSync(PlayerSkinSyncPacket payload) {}
    default void skinSelectionSync(SkinSelectionSyncPacket payload) {}
    default void hudConfig(HudConfigPacket payload) {}
    default void openModeration(OpenModerationPacket payload) {}
    default void moderationSync(ModerationSyncPacket payload) {}
    default void eventMapSync(EventMapSyncPacket payload) {}
    default void signalHuntHud(SignalHuntHudPacket payload) {}
    default void capturePointMapSync(CapturePointMapSyncPacket payload) {}
    default void capturePointHud(CapturePointHudPacket payload) {}
    default void baseZoneMapSync(BaseZoneMapSyncPacket payload) {}
    default void radioCarrierSync(RadioCarrierSyncPacket payload) {}
    default void campaignSync(CampaignSyncPacket payload) {}
    default void openReports(OpenReportsPacket payload) {}
    default void reportSync(ReportSyncPacket payload) {}
    default void playerReportThread(PlayerReportThreadPacket payload) {}
    default void openWelcomeGuide(OpenWelcomeGuidePacket payload) {}
    default void deathScreen(DeathScreenPacket payload) {}
    default void radioSpawnList(RadioSpawnListPacket payload) {}
    default void mapMarkerSync(MapMarkerSyncPacket payload) {}
    default void missileCatalogSync(MissileCatalogSyncPacket payload) {}
}
