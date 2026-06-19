package ru.liko.pjmbasemod.client.frontline;

import ru.liko.pjmbasemod.common.network.packet.FrontlineHudPacket;
import ru.liko.pjmbasemod.common.network.packet.FrontlineMapSyncPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientFrontlineState {

    private static volatile FrontlineHudPacket hud;
    private static volatile long hudUpdatedAt;
    private static volatile long mapRevision;
    private static volatile List<FrontlineMapSyncPacket.SectorEntry> sectors = List.of();
    // Карты пересобираются целиком и атомарно подменяются по ссылке (volatile), поэтому
    // читатели (в т.ч. с потока JourneyMap) всегда видят либо старую, либо полностью
    // готовую структуру — без «полуочищенного» состояния и ConcurrentModificationException.
    private static volatile Map<String, Map<Long, FrontlineMapSyncPacket.ChunkEntry>> chunksByDimension = Map.of();
    private static volatile Map<String, List<FrontlineMapSyncPacket.SectorEntry>> sectorsByDimension = Map.of();

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
        List<FrontlineMapSyncPacket.SectorEntry> newSectors = List.copyOf(packet.sectors());

        // Собираем во временные структуры, затем атомарно подменяем ссылки.
        Map<String, Map<Long, FrontlineMapSyncPacket.ChunkEntry>> newChunks = new LinkedHashMap<>();
        Map<String, List<FrontlineMapSyncPacket.SectorEntry>> newSectorsByDim = new LinkedHashMap<>();

        for (FrontlineMapSyncPacket.ChunkEntry chunk : packet.chunks()) {
            String dimension = normalizeDimensionId(chunk.dimension());
            newChunks
                    .computeIfAbsent(dimension, ignored -> new LinkedHashMap<>())
                    .put(pack(chunk.x(), chunk.z()), chunk);
        }

        for (FrontlineMapSyncPacket.SectorEntry sector : newSectors) {
            String dimension = normalizeDimensionId(sector.dimension());
            newSectorsByDim.computeIfAbsent(dimension, ignored -> new ArrayList<>()).add(sector);
        }

        sectors = newSectors;
        chunksByDimension = newChunks;
        sectorsByDimension = newSectorsByDim;

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
        chunksByDimension = Map.of();
        sectorsByDimension = Map.of();
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
