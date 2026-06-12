package ru.liko.pjmbasemod.client.region;

import ru.liko.pjmbasemod.common.network.packet.RegionMapSyncPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ClientRegionState {

    private static long mapRevision;
    private static List<RegionMapSyncPacket.RegionEntry> regions = List.of();
    private static final Map<String, List<RegionMapSyncPacket.RegionEntry>> regionsByDimension = new LinkedHashMap<>();
    private static final Map<String, Map<Long, String>> regionBorderNamesByDimension = new LinkedHashMap<>();

    private ClientRegionState() {}

    public static void updateMap(RegionMapSyncPacket packet) {
        regions = List.copyOf(packet.regions());
        regionsByDimension.clear();
        regionBorderNamesByDimension.clear();

        for (RegionMapSyncPacket.RegionEntry region : regions) {
            String dimension = normalizeDimensionId(region.dimension());
            regionsByDimension.computeIfAbsent(dimension, ignored -> new ArrayList<>()).add(region);
        }

        for (Map.Entry<String, List<RegionMapSyncPacket.RegionEntry>> entry : regionsByDimension.entrySet()) {
            Map<Long, String> borders = new LinkedHashMap<>();
            for (RegionMapSyncPacket.RegionEntry region : entry.getValue()) {
                addRegionBorder(borders, region);
            }
            regionBorderNamesByDimension.put(entry.getKey(), borders);
        }

        mapRevision++;
    }

    public static long mapRevision() {
        return mapRevision;
    }

    public static List<RegionMapSyncPacket.RegionEntry> regions() {
        return regions;
    }

    public static List<RegionMapSyncPacket.RegionEntry> regions(String dimension) {
        return regions(dimension, region -> true);
    }

    public static List<RegionMapSyncPacket.RegionEntry> frontlineRegions() {
        return regions.stream().filter(RegionMapSyncPacket.RegionEntry::frontline).toList();
    }

    public static List<RegionMapSyncPacket.RegionEntry> frontlineRegions(String dimension) {
        return regions(dimension, RegionMapSyncPacket.RegionEntry::frontline);
    }

    private static List<RegionMapSyncPacket.RegionEntry> regions(String dimension, Predicate<RegionMapSyncPacket.RegionEntry> predicate) {
        List<RegionMapSyncPacket.RegionEntry> exact = regionsByDimension.get(normalizeDimensionId(dimension));
        if (exact != null) return exact.stream().filter(predicate).toList();

        String alias = legacyAliasFor(dimension);
        if (alias == null) return List.of();
        return regionsByDimension.getOrDefault(alias, List.of()).stream().filter(predicate).toList();
    }

    @Nullable
    public static String regionAt(String dimension, int x, int z) {
        for (RegionMapSyncPacket.RegionEntry region : regions(dimension)) {
            if (x >= region.minX() && x <= region.maxX() && z >= region.minZ() && z <= region.maxZ()) {
                return region.name();
            }
        }
        return null;
    }

    public static boolean isRegionBorderChunk(String dimension, int x, int z) {
        Map<Long, String> borders = regionBorderNamesByDimension.get(normalizeDimensionId(dimension));
        if (borders == null) {
            String alias = legacyAliasFor(dimension);
            if (alias != null) borders = regionBorderNamesByDimension.get(alias);
        }
        return borders != null && borders.containsKey(pack(x, z));
    }

    @Nullable
    public static String regionBorderName(String dimension, int x, int z) {
        Map<Long, String> borders = regionBorderNamesByDimension.get(normalizeDimensionId(dimension));
        if (borders == null) {
            String alias = legacyAliasFor(dimension);
            if (alias != null) borders = regionBorderNamesByDimension.get(alias);
        }
        if (borders == null) return null;
        return borders.get(pack(x, z));
    }

    public static void reset() {
        regions = List.of();
        regionsByDimension.clear();
        regionBorderNamesByDimension.clear();
        mapRevision++;
    }

    private static void addRegionBorder(Map<Long, String> borders, RegionMapSyncPacket.RegionEntry region) {
        for (int x = region.minX(); x <= region.maxX(); x++) {
            borders.putIfAbsent(pack(x, region.minZ()), region.name());
            borders.putIfAbsent(pack(x, region.maxZ()), region.name());
        }
        for (int z = region.minZ(); z <= region.maxZ(); z++) {
            borders.putIfAbsent(pack(region.minX(), z), region.name());
            borders.putIfAbsent(pack(region.maxX(), z), region.name());
        }
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
