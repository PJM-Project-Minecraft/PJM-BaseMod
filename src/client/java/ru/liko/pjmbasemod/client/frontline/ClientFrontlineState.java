package ru.liko.pjmbasemod.client.frontline;

import ru.liko.pjmbasemod.common.network.packet.FrontlineHudPacket;
import ru.liko.pjmbasemod.common.network.packet.FrontlineMapSyncPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientFrontlineState {

    private static FrontlineHudPacket hud;
    private static long hudUpdatedAt;
    private static long mapRevision;
    private static List<FrontlineMapSyncPacket.SectorEntry> sectors = List.of();
    private static final Map<String, Map<Long, FrontlineMapSyncPacket.ChunkEntry>> chunksByDimension = new LinkedHashMap<>();
    private static final Map<String, List<FrontlineMapSyncPacket.SectorEntry>> sectorsByDimension = new LinkedHashMap<>();

    private ClientFrontlineState() {}

    public static void updateHud(FrontlineHudPacket packet) {
        hud = packet;
        hudUpdatedAt = System.currentTimeMillis();
    }

    @Nullable
    public static FrontlineHudPacket hud() {
        if (hud == null) return null;
        if (System.currentTimeMillis() - hudUpdatedAt > 5000L) return null;
        return hud;
    }

    public static void updateMap(FrontlineMapSyncPacket packet) {
        sectors = List.copyOf(packet.sectors());
        chunksByDimension.clear();
        sectorsByDimension.clear();

        for (FrontlineMapSyncPacket.ChunkEntry chunk : packet.chunks()) {
            String dimension = normalizeDimensionId(chunk.dimension());
            chunksByDimension
                    .computeIfAbsent(dimension, ignored -> new LinkedHashMap<>())
                    .put(pack(chunk.x(), chunk.z()), chunk);
        }

        for (FrontlineMapSyncPacket.SectorEntry sector : sectors) {
            String dimension = normalizeDimensionId(sector.dimension());
            sectorsByDimension.computeIfAbsent(dimension, ignored -> new ArrayList<>()).add(sector);
        }

        mapRevision++;
    }

    public static long mapRevision() {
        return mapRevision;
    }

    public static List<FrontlineMapSyncPacket.SectorEntry> sectors() {
        return sectors;
    }

    public static List<FrontlineMapSyncPacket.SectorEntry> sectors(String dimension) {
        List<FrontlineMapSyncPacket.SectorEntry> exact = sectorsByDimension.get(normalizeDimensionId(dimension));
        if (exact != null) return exact;

        String alias = legacyAliasFor(dimension);
        if (alias == null) return List.of();
        return sectorsByDimension.getOrDefault(alias, List.of());
    }

    public static List<FrontlineMapSyncPacket.ChunkEntry> chunks() {
        List<FrontlineMapSyncPacket.ChunkEntry> result = new ArrayList<>();
        for (Map<Long, FrontlineMapSyncPacket.ChunkEntry> chunks : chunksByDimension.values()) {
            result.addAll(chunks.values());
        }
        return List.copyOf(result);
    }

    public static List<FrontlineMapSyncPacket.ChunkEntry> chunks(String dimension) {
        Map<Long, FrontlineMapSyncPacket.ChunkEntry> chunks = chunksByDimension.get(normalizeDimensionId(dimension));
        if (chunks == null) {
            String alias = legacyAliasFor(dimension);
            if (alias != null) chunks = chunksByDimension.get(alias);
        }
        if (chunks == null) return List.of();
        return List.copyOf(chunks.values());
    }

    @Nullable
    public static FrontlineMapSyncPacket.ChunkEntry chunk(String dimension, int x, int z) {
        Map<Long, FrontlineMapSyncPacket.ChunkEntry> chunks = chunksByDimension.get(normalizeDimensionId(dimension));
        if (chunks == null) {
            String alias = legacyAliasFor(dimension);
            if (alias != null) chunks = chunksByDimension.get(alias);
        }
        if (chunks == null) return null;
        return chunks.get(pack(x, z));
    }

    public static void reset() {
        hud = null;
        hudUpdatedAt = 0L;
        sectors = List.of();
        chunksByDimension.clear();
        sectorsByDimension.clear();
        mapRevision++;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static String normalizeDimensionId(String dimension) {
        if (dimension == null) return "";
        return dimension.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Nullable
    private static String legacyAliasFor(String dimension) {
        String normalized = normalizeDimensionId(dimension);
        if (normalized.isBlank()) return null;
        if (normalized.startsWith("minecraft:")) {
            return normalized.substring("minecraft:".length());
        }
        if (!normalized.contains(":")) {
            return "minecraft:" + normalized;
        }
        return null;
    }
}
