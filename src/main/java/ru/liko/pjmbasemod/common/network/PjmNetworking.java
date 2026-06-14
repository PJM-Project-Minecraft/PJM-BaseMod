package ru.liko.pjmbasemod.common.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.garage.GarageManager;
import ru.liko.pjmbasemod.common.network.handler.ServerPacketHandlers;
import ru.liko.pjmbasemod.common.network.packet.*;
import ru.liko.pjmbasemod.common.warehouse.WarehouseManager;

public final class PjmNetworking {

    public static final String VERSION = "14";

    private static ClientPacketProxy CLIENT = ClientPacketProxy.NOOP;

    private PjmNetworking() {}

    public static void setClientProxy(ClientPacketProxy proxy) {
        CLIENT = proxy == null ? ClientPacketProxy.NOOP : proxy;
    }

    public static ClientPacketProxy client() { return CLIENT; }

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(Pjmbasemod.MODID).versioned(VERSION).optional();

        // ===== Client → Server =====
        r.playToServer(ChangeChatModePacket.TYPE,      ChangeChatModePacket.STREAM_CODEC,      (p, ctx) -> ctx.enqueueWork(() -> ServerPacketHandlers.handleChangeChatMode(p, (ServerPlayer) ctx.player())));
        r.playToServer(SelectCustomizationPacket.TYPE, SelectCustomizationPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ServerPacketHandlers.handleSelectCustomization(p, (ServerPlayer) ctx.player())));
        r.playToServer(RadioSwitchPacket.TYPE,         RadioSwitchPacket.STREAM_CODEC,         (p, ctx) -> ctx.enqueueWork(() -> ServerPacketHandlers.handleRadioSwitch(p, (ServerPlayer) ctx.player())));
        r.playToServer(CraftVehiclePacket.TYPE,        CraftVehiclePacket.STREAM_CODEC,        (p, ctx) -> ctx.enqueueWork(() -> GarageManager.handleCraft((ServerPlayer) ctx.player(), p.defId())));
        r.playToServer(SpawnVehiclePacket.TYPE,        SpawnVehiclePacket.STREAM_CODEC,        (p, ctx) -> ctx.enqueueWork(() -> GarageManager.handleSpawn((ServerPlayer) ctx.player(), p.instanceId())));
        r.playToServer(StoreVehiclePacket.TYPE,        StoreVehiclePacket.STREAM_CODEC,        (p, ctx) -> ctx.enqueueWork(() -> GarageManager.handleStore((ServerPlayer) ctx.player())));
        r.playToServer(StoreSelectedPacket.TYPE,       StoreSelectedPacket.STREAM_CODEC,       (p, ctx) -> ctx.enqueueWork(() -> GarageManager.handleStoreSelected((ServerPlayer) ctx.player(), p.entityId())));
        r.playToServer(SpawnAtPointPacket.TYPE,        SpawnAtPointPacket.STREAM_CODEC,        (p, ctx) -> ctx.enqueueWork(() -> GarageManager.handleSpawnAtPoint((ServerPlayer) ctx.player(), p.instanceId(), p.index())));
        r.playToServer(RecycleVehiclePacket.TYPE,      RecycleVehiclePacket.STREAM_CODEC,      (p, ctx) -> ctx.enqueueWork(() -> GarageManager.handleRecycle((ServerPlayer) ctx.player(), p.instanceId())));
        r.playToServer(WithdrawItemPacket.TYPE,        WithdrawItemPacket.STREAM_CODEC,        (p, ctx) -> ctx.enqueueWork(() -> WarehouseManager.handleWithdraw((ServerPlayer) ctx.player(), p.defId(), p.count())));
        r.playToServer(DepositItemPacket.TYPE,         DepositItemPacket.STREAM_CODEC,         (p, ctx) -> ctx.enqueueWork(() -> WarehouseManager.handleDeposit((ServerPlayer) ctx.player(), p.defId(), p.count())));
        r.playToServer(SelectRolePacket.TYPE,          SelectRolePacket.STREAM_CODEC,          (p, ctx) -> ctx.enqueueWork(() -> ServerPacketHandlers.handleSelectRole(p, (ServerPlayer) ctx.player())));
        r.playToServer(SubmitFactionSelectionPacket.TYPE, SubmitFactionSelectionPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ServerPacketHandlers.handleSubmitFactionSelection(p, (ServerPlayer) ctx.player())));
        r.playToServer(ManageFactionRolePacket.TYPE,   ManageFactionRolePacket.STREAM_CODEC,   (p, ctx) -> ctx.enqueueWork(() -> ServerPacketHandlers.handleManageFactionRole(p, (ServerPlayer) ctx.player())));

        // ===== Server → Client =====
        r.playToClient(SyncPjmDataPacket.TYPE,  SyncPjmDataPacket.STREAM_CODEC,  (p, ctx) -> ctx.enqueueWork(() -> CLIENT.syncPlayerData(p)));
        r.playToClient(NotificationPacket.TYPE, NotificationPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.notification(p)));
        r.playToClient(RadioEventPacket.TYPE,   RadioEventPacket.STREAM_CODEC,   (p, ctx) -> ctx.enqueueWork(() -> CLIENT.radioEvent(p)));
        r.playToClient(RegionMapSyncPacket.TYPE, RegionMapSyncPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.regionMapSync(p)));
        r.playToClient(FrontlineHudPacket.TYPE, FrontlineHudPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.frontlineHud(p)));
        r.playToClient(FrontlineMapSyncPacket.TYPE, FrontlineMapSyncPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.frontlineMapSync(p)));
        r.playToClient(OpenGaragePacket.TYPE,   OpenGaragePacket.STREAM_CODEC,   (p, ctx) -> ctx.enqueueWork(() -> CLIENT.openGarage(p)));
        r.playToClient(GarageSyncPacket.TYPE,   GarageSyncPacket.STREAM_CODEC,   (p, ctx) -> ctx.enqueueWork(() -> CLIENT.garageSync(p)));
        r.playToClient(StoreOptionsPacket.TYPE, StoreOptionsPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.openStoreOptions(p)));
        r.playToClient(SpawnPointOptionsPacket.TYPE, SpawnPointOptionsPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.openSpawnPointOptions(p)));
        r.playToClient(RankSyncPacket.TYPE,     RankSyncPacket.STREAM_CODEC,     (p, ctx) -> ctx.enqueueWork(() -> CLIENT.rankSync(p)));
        r.playToClient(RankXpPacket.TYPE,       RankXpPacket.STREAM_CODEC,       (p, ctx) -> ctx.enqueueWork(() -> CLIENT.rankXp(p)));
        r.playToClient(RoleSyncPacket.TYPE,     RoleSyncPacket.STREAM_CODEC,     (p, ctx) -> ctx.enqueueWork(() -> CLIENT.roleSync(p)));
        r.playToClient(FactionCommanderSyncPacket.TYPE, FactionCommanderSyncPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.factionCommanderSync(p)));
        r.playToClient(OpenWarehousePacket.TYPE, OpenWarehousePacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.openWarehouse(p)));
        r.playToClient(WarehouseSyncPacket.TYPE, WarehouseSyncPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.warehouseSync(p)));
        r.playToClient(OpenFactionSelectionPacket.TYPE, OpenFactionSelectionPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.openFactionSelection(p)));
        r.playToClient(OpenFactionManagementPacket.TYPE, OpenFactionManagementPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.openFactionManagement(p)));
        r.playToClient(FactionManagementSyncPacket.TYPE, FactionManagementSyncPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.factionManagementSync(p)));
        r.playToClient(LockedSlotsPacket.TYPE, LockedSlotsPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.lockedSlots(p)));
        r.playToClient(PlayerSkinSyncPacket.TYPE, PlayerSkinSyncPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.playerSkinSync(p)));
        r.playToClient(SkinSelectionSyncPacket.TYPE, SkinSelectionSyncPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.skinSelectionSync(p)));

        Pjmbasemod.LOGGER.info("PJM-BaseMod: registered {} network payloads.", 32);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToAll(net.minecraft.server.MinecraftServer server, CustomPacketPayload payload) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, payload);
        }
    }
}
