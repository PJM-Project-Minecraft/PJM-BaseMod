package ru.liko.pjmbasemod.common.frontline.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import net.minecraft.server.MinecraftServer;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.frontline.FrontlineChunkKey;
import ru.liko.pjmbasemod.common.frontline.FrontlineChunkState;
import ru.liko.pjmbasemod.common.frontline.FrontlineManager;
import ru.liko.pjmbasemod.common.frontline.FrontlineSavedData;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.network.packet.FrontlineMapSyncPacket;
import ru.liko.pjmbasemod.common.region.Region;
import ru.liko.pjmbasemod.common.region.RegionSavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class FrontlineBlueMapRuntime {

    private static final AtomicReference<BlueMapAPI> API = new AtomicReference<>();

    private static final Consumer<BlueMapAPI> ON_ENABLE = api -> {
        API.set(api);
        String version = safeBlueMapVersion(api);
        lastBlueMapVersion = version;
        needsResnapshot = true;
        Pjmbasemod.LOGGER.info("[FRONTLINE][BlueMap] API enabled (BlueMap {}).", version);
    };
    private static final Consumer<BlueMapAPI> ON_DISABLE = api -> {
        API.compareAndSet(api, null);
        lastBlueMapVersion = "";
        lastDimensionMapping = Map.of();
        Pjmbasemod.LOGGER.info("[FRONTLINE][BlueMap] API disabled.");
    };

    private static volatile boolean initialized;
    private static volatile boolean syncRequested;
    private static volatile boolean needsResnapshot;
    private static volatile int debounceTicksLeft;
    private static volatile String lastReason = "startup";
    private static volatile Snapshot pendingSnapshot;
    private static volatile String lastBlueMapVersion = "";
    private static volatile long lastSuccessfulSyncAtMs;
    private static volatile Map<String, String> lastDimensionMapping = Map.of();
    private static volatile Set<String> lastTouchedMapIds = Set.of();
    private static volatile Set<String> warnedMissingDimensions = Set.of();
    private static volatile boolean activePulseMarkers;

    private FrontlineBlueMapRuntime() {}

    public static void init() {
        if (initialized) return;
        synchronized (FrontlineBlueMapRuntime.class) {
            if (initialized) return;
            try {
                BlueMapAPI.onEnable(ON_ENABLE);
                BlueMapAPI.onDisable(ON_DISABLE);
                initialized = true;
            } catch (Throwable t) {
                Pjmbasemod.LOGGER.info("[FRONTLINE][BlueMap] API listeners not available ({}).", t.getClass().getSimpleName());
            }
        }
    }

    public static void onServerStarted(MinecraftServer server) {
        init();
        if (server == null || !Config.isFrontlineBlueMapEnabled()) return;
        requestSync("server_started");
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null || !Config.isFrontlineBlueMapEnabled()) return;
        init();

        if (needsResnapshot) {
            needsResnapshot = false;
            requestSync("bluemap_reload");
        }

        if (syncRequested) {
            if (debounceTicksLeft > 0) {
                debounceTicksLeft--;
            }
            if (debounceTicksLeft <= 0) {
                pendingSnapshot = captureSnapshot(server, RegionSavedData.get(server), FrontlineSavedData.get(server));
                syncRequested = false;
            }
        }

        tryApplyPendingSnapshot();

        if (activePulseMarkers && !syncRequested && pendingSnapshot == null) {
            syncRequested = true;
            lastReason = "bluemap_active_sector_pulse";
            debounceTicksLeft = 10;
        }
    }

    public static void onServerStopping() {
        pendingSnapshot = null;
        syncRequested = false;
        debounceTicksLeft = 0;
    }

    public static void requestSync(String reason) {
        if (!Config.isFrontlineBlueMapEnabled()) return;
        syncRequested = true;
        lastReason = reason == null || reason.isBlank() ? "unknown" : reason;
        debounceTicksLeft = Math.max(1, Config.getFrontlineBlueMapSyncDebounceTicks());
    }

    public static boolean forceSyncNow(MinecraftServer server, String reason) {
        if (server == null || !Config.isFrontlineBlueMapEnabled()) return false;
        pendingSnapshot = captureSnapshot(server, RegionSavedData.get(server), FrontlineSavedData.get(server));
        syncRequested = false;
        debounceTicksLeft = 0;
        lastReason = reason == null || reason.isBlank() ? "manual_force" : reason;
        return tryApplyPendingSnapshot();
    }

    public static FrontlineBlueMapService.StatusSnapshot status() {
        BlueMapAPI api = API.get();
        return new FrontlineBlueMapService.StatusSnapshot(
                Config.isFrontlineBlueMapEnabled(),
                api != null,
                api == null ? "" : safeBlueMapVersion(api),
                syncRequested,
                pendingSnapshot != null,
                debounceTicksLeft,
                lastReason,
                lastSuccessfulSyncAtMs,
                lastDimensionMapping
        );
    }

    private static boolean tryApplyPendingSnapshot() {
        Snapshot snapshot = pendingSnapshot;
        BlueMapAPI api = API.get();
        if (snapshot == null || api == null) return false;
        if (!applySnapshot(api, snapshot)) return false;
        pendingSnapshot = null;
        activePulseMarkers = snapshot.hasActiveSectors();
        lastSuccessfulSyncAtMs = System.currentTimeMillis();
        return true;
    }

    private static Snapshot captureSnapshot(MinecraftServer server, RegionSavedData regionsData, FrontlineSavedData data) {
        Map<String, List<RegionArea>> regionsByDimension = new LinkedHashMap<>();
        for (Region region : regionsData.frontlineRegions()) {
            if (!region.isComplete()) continue;
            regionsByDimension.computeIfAbsent(region.dimension(), ignored -> new ArrayList<>())
                    .add(new RegionArea(region.name(), region.displayName(), region.minX(), region.maxX(), region.minZ(), region.maxZ()));
        }

        Map<DimensionOwnerKey, List<ChunkPoint>> chunksByOwner = new LinkedHashMap<>();
        for (FrontlineChunkState state : data.chunks()) {
            FrontlineChunkKey key = state.key();
            String ownerId = state.ownerTeamId();
            DimensionOwnerKey ownerKey = new DimensionOwnerKey(key.dimension(), ownerId == null ? "" : ownerId);
            chunksByOwner.computeIfAbsent(ownerKey, ignored -> new ArrayList<>())
                    .add(new ChunkPoint(key.x(), key.z()));
        }

        Map<String, List<OwnerArea>> ownersByDimension = new LinkedHashMap<>();
        for (Map.Entry<DimensionOwnerKey, List<ChunkPoint>> entry : chunksByOwner.entrySet()) {
            DimensionOwnerKey key = entry.getKey();
            int chunkCount = entry.getValue().size();
            List<ChunkRect> rectangles = mergeIntoRectangles(entry.getValue());
            String ownerName = FrontlineTeams.displayName(server, key.ownerId());
            int ownerColor = FrontlineTeams.color(server, key.ownerId());
            ownersByDimension.computeIfAbsent(key.dimension(), ignored -> new ArrayList<>())
                    .add(new OwnerArea(key.ownerId(), ownerName, ownerColor, chunkCount, rectangles));
        }

        boolean brightPulse = ((System.currentTimeMillis() / 750L) & 1L) == 1L;
        Map<String, List<SectorArea>> sectorsByDimension = new LinkedHashMap<>();
        for (FrontlineMapSyncPacket.SectorEntry sector : FrontlineManager.currentSectorEntries(server, data, regionsData)) {
            sectorsByDimension.computeIfAbsent(sector.dimension(), ignored -> new ArrayList<>())
                    .add(new SectorArea(
                            sector.regionName(), sector.sectorX(), sector.sectorZ(),
                            sector.minX(), sector.maxX(), sector.minZ(), sector.maxZ(),
                            sector.teamName(),
                            sector.contested() ? 0xFFC13D : sector.teamColor(),
                            sector.contested(),
                            sector.progressPercent(),
                            brightPulse));
        }

        Set<String> dimensions = new LinkedHashSet<>();
        dimensions.addAll(regionsByDimension.keySet());
        dimensions.addAll(ownersByDimension.keySet());
        dimensions.addAll(sectorsByDimension.keySet());

        List<DimensionSnapshot> snapshots = new ArrayList<>();
        for (String dimension : dimensions) {
            List<RegionArea> regions = regionsByDimension.getOrDefault(dimension, List.of());
            List<OwnerArea> owners = ownersByDimension.getOrDefault(dimension, List.of());
            List<SectorArea> sectors = sectorsByDimension.getOrDefault(dimension, List.of());
            snapshots.add(new DimensionSnapshot(dimension, List.copyOf(regions), List.copyOf(owners), List.copyOf(sectors)));
        }

        snapshots.sort(Comparator.comparing(DimensionSnapshot::dimension));
        return new Snapshot(List.copyOf(snapshots));
    }

    private static boolean applySnapshot(BlueMapAPI api, Snapshot snapshot) {
        String markerSetId = Config.getFrontlineBlueMapMarkerSetId().trim();
        if (markerSetId.isBlank()) return false;

        Map<String, String> overrides = Config.parseFrontlineBlueMapDimensionWorldOverrides();
        Map<String, String> resolvedMapping = new LinkedHashMap<>();
        Set<String> touchedMapIds = new LinkedHashSet<>();
        Set<String> missingWarnings = new LinkedHashSet<>();

        for (DimensionSnapshot dimensionSnapshot : snapshot.dimensions()) {
            String dimensionId = normalizeDimensionId(dimensionSnapshot.dimension());
            String worldId = resolveWorldId(api, dimensionId, overrides);
            if (worldId == null) {
                if (!warnedMissingDimensions.contains(dimensionId)) {
                    Pjmbasemod.LOGGER.warn("[FRONTLINE][BlueMap] Unable to map dimension '{}' to BlueMap world.", dimensionId);
                }
                missingWarnings.add(dimensionId);
                continue;
            }

            resolvedMapping.put(dimensionId, worldId);
            Collection<BlueMapMap> maps = mapsForWorld(api, worldId);
            if (maps.isEmpty()) {
                if (!warnedMissingDimensions.contains(dimensionId)) {
                    Pjmbasemod.LOGGER.warn("[FRONTLINE][BlueMap] BlueMap world '{}' has no maps for dimension '{}'.", worldId, dimensionId);
                }
                missingWarnings.add(dimensionId);
                continue;
            }

            for (BlueMapMap map : maps) {
                touchedMapIds.add(map.getId());
                MarkerSet markerSet = buildMarkerSet(dimensionSnapshot);
                if (markerSet.getMarkers().isEmpty()) {
                    map.getMarkerSets().remove(markerSetId);
                } else {
                    map.getMarkerSets().put(markerSetId, markerSet);
                }
            }
        }

        for (String oldMapId : lastTouchedMapIds) {
            if (touchedMapIds.contains(oldMapId)) continue;
            api.getMap(oldMapId).ifPresent(map -> map.getMarkerSets().remove(markerSetId));
        }

        warnedMissingDimensions = Set.copyOf(missingWarnings);
        lastTouchedMapIds = Set.copyOf(touchedMapIds);
        lastDimensionMapping = Map.copyOf(resolvedMapping);
        return true;
    }

    private static MarkerSet buildMarkerSet(DimensionSnapshot snapshot) {
        MarkerSet markerSet = MarkerSet.builder()
                .label(Config.getFrontlineBlueMapMarkerSetLabel())
                .toggleable(true)
                .defaultHidden(Config.isFrontlineBlueMapDefaultHidden())
                .build();

        int lineAlpha = Config.getFrontlineBlueMapLineAlpha();
        int fillAlpha = Config.getFrontlineBlueMapFillAlpha();
        int lineWidth = Config.getFrontlineBlueMapLineWidth();
        boolean depthTest = Config.isFrontlineBlueMapDepthTest();
        float baseY = Config.getFrontlineBlueMapMarkerHeight();
        // Слои укладываются снизу вверх: граница региона → территория владельца → активный сектор.
        // Небольшое смещение по Y задаёт порядок отрисовки без видимого зазора в 3D-виде.
        float regionY = baseY;
        float ownerY = baseY + 0.25f;
        float sectorY = baseY + 0.5f;

        int markerIndex = 0;
        for (RegionArea region : snapshot.regions()) {
            Shape shape = chunkRectShape(region.minChunkX(), region.maxChunkX(), region.minChunkZ(), region.maxChunkZ());
            ShapeMarker marker = ShapeMarker.builder()
                    .label("Регион «" + region.displayName() + "»")
                    .detail(regionDetail(region))
                    .shape(shape, regionY)
                    .depthTestEnabled(depthTest)
                    .lineColor(withAlpha(0xFFFFFF, Math.min(lineAlpha, 170)))
                    .fillColor(withAlpha(0x000000, 0))
                    .lineWidth(Math.max(1, lineWidth))
                    .build();
            markerSet.put("region_" + safeId(snapshot.dimension()) + "_" + safeId(region.name()) + "_" + markerIndex++, marker);
        }

        for (OwnerArea owner : snapshot.owners()) {
            boolean grayZone = owner.isGrayZone();
            Color lineColor = withAlpha(grayZone ? 0xE6E6E6 : owner.color(), grayZone ? Math.max(lineAlpha, 235) : lineAlpha);
            Color fillColor = withAlpha(owner.color(), grayZone ? Math.max(fillAlpha, 120) : fillAlpha);
            String ownerLabel = grayZone ? FrontlineTeams.GRAY_ZONE_NAME : "Территория: " + owner.name();
            String ownerDetail = ownerDetail(owner);
            int rectIndex = 0;
            for (ChunkRect rect : owner.rectangles()) {
                Shape shape = chunkRectShape(rect.minChunkX(), rect.maxChunkX(), rect.minChunkZ(), rect.maxChunkZ());
                ShapeMarker marker = ShapeMarker.builder()
                        .label(ownerLabel)
                        .detail(ownerDetail)
                        .shape(shape, ownerY)
                        .depthTestEnabled(depthTest)
                        .lineColor(lineColor)
                        .fillColor(fillColor)
                        .lineWidth(grayZone ? lineWidth + 1 : lineWidth)
                        .build();
                markerSet.put("owner_" + safeId(owner.ownerId()) + "_" + markerIndex + "_" + rectIndex++, marker);
            }
            markerIndex++;
        }

        for (SectorArea sector : snapshot.sectors()) {
            int progress = clampPercent(sector.progressPercent());
            // Заливка тем плотнее, чем ближе захват к завершению; пульс добавляет яркости.
            int fillBase = 45 + (int) Math.round(progress / 100.0 * 150.0); // 45..195
            int fillA = sector.brightPulse() ? Math.min(220, fillBase + 30) : fillBase;
            int lineA = sector.contested()
                    ? (sector.brightPulse() ? 255 : 180)
                    : (sector.brightPulse() ? 235 : 150);
            int lineColorRgb = sector.contested() ? 0xFFC13D : 0xFFFFFF;
            int width = lineWidth + (sector.brightPulse() ? 2 : 1) + (sector.contested() ? 1 : 0);

            Shape shape = chunkRectShape(sector.minChunkX(), sector.maxChunkX(), sector.minChunkZ(), sector.maxChunkZ());
            ShapeMarker marker = ShapeMarker.builder()
                    .label(sectorLabel(sector, progress))
                    .detail(sectorDetail(sector, progress))
                    .shape(shape, sectorY)
                    .depthTestEnabled(depthTest)
                    .lineColor(withAlpha(lineColorRgb, lineA))
                    .fillColor(withAlpha(sector.color(), fillA))
                    .lineWidth(width)
                    .build();
            markerSet.put("sector_" + safeId(snapshot.dimension()) + "_" + safeId(sector.regionName()) + "_" + sector.sectorX() + "_" + sector.sectorZ(), marker);
        }

        return markerSet;
    }

    private static String sectorLabel(SectorArea sector, int progress) {
        if (sector.contested()) return "⚔ Оспаривается — " + progress + "%";
        return "Захват: " + sector.teamName() + " — " + progress + "%";
    }

    private static String regionDetail(RegionArea region) {
        int chunksX = region.maxChunkX() - region.minChunkX() + 1;
        int chunksZ = region.maxChunkZ() - region.minChunkZ() + 1;
        return card("#FFFFFF", esc(region.displayName()), "Регион линии фронта",
                row("Размер", chunksX + " × " + chunksZ + " чанк."));
    }

    private static String ownerDetail(OwnerArea owner) {
        boolean grayZone = owner.isGrayZone();
        String accent = colorHex(grayZone ? 0xE6E6E6 : owner.color());
        String title = grayZone ? esc(FrontlineTeams.GRAY_ZONE_NAME) : esc(owner.name());
        String subtitle = grayZone ? "Ничейная зона" : "Контролируемая территория";
        return card(accent, title, subtitle,
                row("Под контролем", owner.chunkCount() + " чанк."));
    }

    private static String sectorDetail(SectorArea sector, int progress) {
        String accent = colorHex(sector.color());
        String title = sector.contested() ? "Сектор оспаривается" : "Захват сектора";
        String team = sector.contested() ? "—" : esc(sector.teamName());
        String body = row("Команда", team)
                + row("Статус", sector.contested() ? "Оспаривается" : "Идёт захват")
                + progressBar(progress, accent);
        return card(accent, title, "Сектор " + sector.sectorX() + ":" + sector.sectorZ(), body);
    }

    private static String card(String accentHex, String title, String subtitle, String body) {
        return "<div style=\"font-family:sans-serif;min-width:190px;padding:2px 4px;\">"
                + "<div style=\"display:flex;align-items:center;gap:6px;margin-bottom:3px;\">"
                + "<span style=\"display:inline-block;width:10px;height:10px;border-radius:2px;background:" + accentHex + ";\"></span>"
                + "<span style=\"font-weight:700;font-size:14px;\">" + title + "</span></div>"
                + "<div style=\"font-size:11px;opacity:0.65;margin-bottom:6px;\">" + subtitle + "</div>"
                + body + "</div>";
    }

    private static String row(String key, String value) {
        return "<div style=\"display:flex;justify-content:space-between;gap:14px;font-size:12px;padding:1px 0;\">"
                + "<span style=\"opacity:0.7;\">" + esc(key) + "</span>"
                + "<span style=\"font-weight:600;\">" + value + "</span></div>";
    }

    private static String progressBar(int progress, String accentHex) {
        return "<div style=\"margin-top:7px;\">"
                + "<div style=\"display:flex;justify-content:space-between;font-size:11px;margin-bottom:2px;\">"
                + "<span style=\"opacity:0.7;\">Прогресс</span><span style=\"font-weight:700;\">" + progress + "%</span></div>"
                + "<div style=\"height:7px;border-radius:4px;background:rgba(128,128,128,0.3);overflow:hidden;\">"
                + "<div style=\"height:100%;width:" + progress + "%;background:" + accentHex + ";\"></div></div></div>";
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String colorHex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private static String esc(String raw) {
        if (raw == null) return "";
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static Shape chunkRectShape(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        double minX = minChunkX * 16.0;
        double minZ = minChunkZ * 16.0;
        double maxX = (maxChunkX + 1) * 16.0;
        double maxZ = (maxChunkZ + 1) * 16.0;
        return Shape.createRect(minX, minZ, maxX, maxZ);
    }

    private static Color withAlpha(int rgb, int alpha) {
        return new Color(rgb & 0xFFFFFF, Math.max(0, Math.min(255, alpha)));
    }

    private static Collection<BlueMapMap> mapsForWorld(BlueMapAPI api, String worldId) {
        Optional<BlueMapWorld> world = api.getWorld(worldId);
        if (world.isPresent()) return world.get().getMaps();
        return api.getMap(worldId).<Collection<BlueMapMap>>map(List::of).orElse(List.of());
    }

    private static String resolveWorldId(BlueMapAPI api, String dimensionId, Map<String, String> overrides) {
        String override = overrides.get(dimensionId);
        if (override != null && !override.isBlank()) return override;

        Optional<BlueMapWorld> direct = api.getWorld(dimensionId);
        if (direct.isPresent()) return direct.get().getId();

        String fallback = switch (dimensionId) {
            case "minecraft:overworld" -> "world";
            case "minecraft:the_nether" -> "world_nether";
            case "minecraft:the_end" -> "world_the_end";
            default -> null;
        };
        if (fallback == null) return null;

        if (api.getWorld(fallback).isPresent()) return fallback;
        if (api.getMap(fallback).isPresent()) return fallback;
        return null;
    }

    private static List<ChunkRect> mergeIntoRectangles(List<ChunkPoint> points) {
        if (points.isEmpty()) return List.of();

        Set<Long> remaining = new HashSet<>();
        Map<Integer, TreeSet<Integer>> rowToXs = new HashMap<>();
        for (ChunkPoint point : points) {
            long packed = pack(point.x(), point.z());
            if (!remaining.add(packed)) continue;
            rowToXs.computeIfAbsent(point.z(), ignored -> new TreeSet<>()).add(point.x());
        }

        List<ChunkRect> result = new ArrayList<>();
        while (!remaining.isEmpty()) {
            ChunkPoint start = firstPoint(rowToXs);
            if (start == null) break;

            int minX = start.x();
            int minZ = start.z();

            int maxX = minX;
            while (contains(remaining, maxX + 1, minZ)) {
                maxX++;
            }

            int maxZ = minZ;
            boolean expand = true;
            while (expand) {
                int nextZ = maxZ + 1;
                for (int x = minX; x <= maxX; x++) {
                    if (!contains(remaining, x, nextZ)) {
                        expand = false;
                        break;
                    }
                }
                if (expand) maxZ = nextZ;
            }

            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    removePoint(remaining, rowToXs, x, z);
                }
            }

            result.add(new ChunkRect(minX, maxX, minZ, maxZ));
        }

        result.sort(Comparator
                .comparingInt(ChunkRect::minChunkZ)
                .thenComparingInt(ChunkRect::minChunkX)
                .thenComparingInt(ChunkRect::maxChunkZ)
                .thenComparingInt(ChunkRect::maxChunkX));
        return List.copyOf(result);
    }

    private static ChunkPoint firstPoint(Map<Integer, TreeSet<Integer>> rowToXs) {
        Integer z = rowToXs.keySet().stream().min(Integer::compareTo).orElse(null);
        if (z == null) return null;
        TreeSet<Integer> xs = rowToXs.get(z);
        if (xs == null || xs.isEmpty()) {
            rowToXs.remove(z);
            return firstPoint(rowToXs);
        }
        return new ChunkPoint(xs.first(), z);
    }

    private static boolean contains(Set<Long> points, int x, int z) {
        return points.contains(pack(x, z));
    }

    private static void removePoint(Set<Long> points, Map<Integer, TreeSet<Integer>> rowToXs, int x, int z) {
        points.remove(pack(x, z));
        TreeSet<Integer> xs = rowToXs.get(z);
        if (xs == null) return;
        xs.remove(x);
        if (xs.isEmpty()) rowToXs.remove(z);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static String safeId(String raw) {
        if (raw == null || raw.isBlank()) return "neutral";
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private static String normalizeDimensionId(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String safeBlueMapVersion(BlueMapAPI api) {
        try {
            return Objects.requireNonNullElse(api.getBlueMapVersion(), "unknown");
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private record Snapshot(List<DimensionSnapshot> dimensions) {
        boolean hasActiveSectors() {
            for (DimensionSnapshot dimension : dimensions) {
                if (!dimension.sectors().isEmpty()) return true;
            }
            return false;
        }
    }

    private record DimensionSnapshot(String dimension, List<RegionArea> regions, List<OwnerArea> owners, List<SectorArea> sectors) {}

    private record RegionArea(String name, String displayName, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {}

    private record OwnerArea(String ownerId, String name, int color, int chunkCount, List<ChunkRect> rectangles) {
        private boolean isGrayZone() {
            return FrontlineTeams.GRAY_ZONE_ID.equals(ownerId);
        }
    }

    private record SectorArea(String regionName, int sectorX, int sectorZ, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
                              String teamName, int color, boolean contested, int progressPercent, boolean brightPulse) {}

    private record ChunkRect(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {}

    private record ChunkPoint(int x, int z) {}

    private record DimensionOwnerKey(String dimension, String ownerId) {}
}
