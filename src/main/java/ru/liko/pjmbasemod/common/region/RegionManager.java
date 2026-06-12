package ru.liko.pjmbasemod.common.region;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.common.frontline.bluemap.FrontlineBlueMapService;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.RegionMapSyncPacket;

import java.util.ArrayList;
import java.util.List;

public final class RegionManager {

    private static volatile long mapSyncRevision;
    private static volatile long lastMapSyncAtMs;
    private static volatile String lastMapSyncReason = "startup";

    private RegionManager() {}

    public static void sendInitialSync(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        PjmNetworking.sendToPlayer(player, createMapSync(RegionSavedData.get(player.getServer())));
    }

    public static void broadcastMapSync(MinecraftServer server, RegionSavedData data) {
        broadcastMapSync(server, data, "region_data_changed");
    }

    public static void broadcastMapSync(MinecraftServer server, RegionSavedData data, String reason) {
        if (server == null || data == null) return;
        PjmNetworking.sendToAll(server, createMapSync(data));
        mapSyncRevision++;
        lastMapSyncAtMs = System.currentTimeMillis();
        lastMapSyncReason = (reason == null || reason.isBlank()) ? "unknown" : reason;
        FrontlineBlueMapService.requestSync(lastMapSyncReason);
    }

    public static RegionMapSyncPacket createMapSync(RegionSavedData data) {
        List<RegionMapSyncPacket.RegionEntry> regions = new ArrayList<>();
        for (Region region : data.regions()) {
            if (!region.isComplete()) continue;
            regions.add(new RegionMapSyncPacket.RegionEntry(
                    region.name(), region.displayName(), region.dimension(), region.minX(), region.minZ(), region.maxX(), region.maxZ(), region.isFrontline()));
        }
        return new RegionMapSyncPacket(List.copyOf(regions));
    }

    public static MapSyncStatus mapSyncStatus() {
        return new MapSyncStatus(mapSyncRevision, lastMapSyncAtMs, lastMapSyncReason);
    }

    public record MapSyncStatus(long revision, long lastBroadcastAtMs, String lastReason) {}
}
