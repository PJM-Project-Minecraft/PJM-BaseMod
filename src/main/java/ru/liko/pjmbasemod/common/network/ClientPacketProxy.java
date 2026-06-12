package ru.liko.pjmbasemod.common.network;

import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;
import ru.liko.pjmbasemod.common.network.packet.FactionManagementSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.FrontlineHudPacket;
import ru.liko.pjmbasemod.common.network.packet.FrontlineMapSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.GarageSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.LockedSlotsPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenFactionManagementPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenFactionSelectionPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenGaragePacket;
import ru.liko.pjmbasemod.common.network.packet.OpenWarehousePacket;
import ru.liko.pjmbasemod.common.network.packet.WarehouseSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.FactionCommanderSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RadioEventPacket;
import ru.liko.pjmbasemod.common.network.packet.RegionMapSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RankSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RankXpPacket;
import ru.liko.pjmbasemod.common.network.packet.RoleSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.StoreOptionsPacket;
import ru.liko.pjmbasemod.common.network.packet.SyncPjmDataPacket;

public interface ClientPacketProxy {

    ClientPacketProxy NOOP = new ClientPacketProxy() {};

    default void syncPlayerData(SyncPjmDataPacket payload) {}
    default void notification(NotificationPacket payload) {}
    default void radioEvent(RadioEventPacket payload) {}
    default void regionMapSync(RegionMapSyncPacket payload) {}
    default void frontlineHud(FrontlineHudPacket payload) {}
    default void frontlineMapSync(FrontlineMapSyncPacket payload) {}
    default void openGarage(OpenGaragePacket payload) {}
    default void garageSync(GarageSyncPacket payload) {}
    default void openStoreOptions(StoreOptionsPacket payload) {}
    default void rankSync(RankSyncPacket payload) {}
    default void rankXp(RankXpPacket payload) {}
    default void roleSync(RoleSyncPacket payload) {}
    default void factionCommanderSync(FactionCommanderSyncPacket payload) {}
    default void openWarehouse(OpenWarehousePacket payload) {}
    default void warehouseSync(WarehouseSyncPacket payload) {}
    default void openFactionSelection(OpenFactionSelectionPacket payload) {}
    default void openFactionManagement(OpenFactionManagementPacket payload) {}
    default void factionManagementSync(FactionManagementSyncPacket payload) {}
    default void lockedSlots(LockedSlotsPacket payload) {}
}
