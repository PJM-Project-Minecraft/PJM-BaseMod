package ru.liko.pjmbasemod.client.frontline.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.MapPolygonWithHoles;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.client.util.PolygonHelper;
import journeymap.api.v2.common.event.ClientEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.frontline.ClientFrontlineState;
import ru.liko.pjmbasemod.client.region.ClientRegionState;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.network.packet.FrontlineMapSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RegionMapSyncPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FrontlineJourneyMapRuntime implements FrontlineJourneyMapBridge.Adapter {

    private static final int MAP_Y = 64;
    private static final int NEUTRAL_DISPLAY_ORDER = 100;
    private static final int OWNER_DISPLAY_ORDER = 110;
    private static final int GRAY_ZONE_DISPLAY_ORDER = 115;
    private static final int REGION_BORDER_DISPLAY_ORDER = 120;
    private static final int FRONT_LINE_DISPLAY_ORDER = 170;
    private static final int ACTIVE_SECTOR_DISPLAY_ORDER = 180;

    // Ширина полосы линии фронта в блоках (рисуется внутрь своей территории).
    private static final int FRONT_LINE_WIDTH_BLOCKS = 3;

    // Полная (тяжёлая) пересборка оверлеев не чаще одного раза в этот интервал.
    // Частые sync-пакеты во время боя коалесцируются в одну пересборку.
    private static final long REBUILD_THROTTLE_MS = 500L;

    private final IClientAPI api;
    // Единственный фоновый поток: считает геометрию (java.awt.geom.Area) и строит
    // PolygonOverlay без обращений к JourneyMap API. Все api.show/remove — на клиентском тике.
    private final ExecutorService buildExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "PJM-Frontline-JMBuild");
        thread.setDaemon(true);
        return thread;
    });

    private boolean mappingActive;
    private boolean overlaysVisible;
    private final List<PolygonOverlay> activeOverlays = new ArrayList<>();
    private final Map<String, PolygonOverlay> activeSectorOverlays = new LinkedHashMap<>();

    // Что сейчас РЕАЛЬНО показано на карте.
    private long lastAppliedRevision = -1L;
    private int lastAppliedConfigHash;
    private int lastAppliedGeometryHash;

    // Для какого состояния уже запущена/поставлена в очередь фоновая сборка (дедуп).
    private long scheduledRevision = Long.MIN_VALUE;
    private int scheduledConfigHash;

    private volatile boolean building;
    private volatile long lastBuildStartMillis;
    // Поколение: инкрементируется при clear/logout/disable, чтобы отбросить результат
    // фоновой сборки, начатой до сброса.
    private volatile int buildGeneration;
    private volatile BuildResult pendingResult;

    private int overlayCount;
    private String lastError = "none";
    private String lastLoggedError = "";

    private FrontlineJourneyMapRuntime(IClientAPI api) {
        this.api = api;
    }

    static void initialize(IClientAPI api) {
        FrontlineJourneyMapRuntime runtime = new FrontlineJourneyMapRuntime(api);
        FrontlineJourneyMapBridge.attach(runtime);
        ClientEventRegistry.MAPPING_EVENT.subscribe(Pjmbasemod.MODID, runtime::onMappingEvent);
    }

    @Override
    public void onClientTick() {
        if (!Config.isFrontlineJourneyMapEnabled()) {
            clearOverlays();
            resetTracking();
            return;
        }
        if (Minecraft.getInstance().player == null) {
            mappingActive = false;
            clearOverlays();
            resetTracking();
            return;
        }
        if (!mappingActive) return;
        applyIfNeeded();
    }

    @Override
    public void onLogout() {
        mappingActive = false;
        clearOverlays();
        resetTracking();
        overlayCount = 0;
        lastError = "none";
        lastLoggedError = "";
    }

    @Override
    public FrontlineJourneyMapBridge.StatusSnapshot status() {
        return new FrontlineJourneyMapBridge.StatusSnapshot(
                Config.isFrontlineJourneyMapEnabled(),
                true,
                mappingActive,
                overlaysVisible,
                lastAppliedRevision,
                overlayCount,
                lastError
        );
    }

    private void onMappingEvent(MappingEvent event) {
        mappingActive = event.getStage() == MappingEvent.Stage.MAPPING_STARTED;
        if (mappingActive) {
            resetTracking();
            applyIfNeeded();
        } else {
            clearOverlays();
        }
    }

    private void applyIfNeeded() {
        try {
            // 1. Сначала применяем готовый результат фоновой сборки (только тут дёргаем api).
            drainPendingResult();

            if (!api.playerAccepts(Pjmbasemod.MODID, DisplayType.Polygon)) {
                clearOverlays();
                resetTracking();
                setNoError();
                return;
            }

            long revision = combinedMapRevision();
            int configHash = configHash();

            // Уже показано актуальное состояние — ничего не делаем.
            if (revision == lastAppliedRevision && configHash == lastAppliedConfigHash) {
                setNoError();
                return;
            }
            // Для этого состояния сборка уже запущена/в очереди — ждём результата.
            if (revision == scheduledRevision && configHash == scheduledConfigHash) {
                return;
            }

            int geometryHash = geometryHash();
            // Быстрый путь: изменились только подписи секторов — правим на месте (дёшево).
            if (configHash == lastAppliedConfigHash
                    && geometryHash == lastAppliedGeometryHash
                    && updateActiveSectorLabels()) {
                overlayCount = activeOverlays.size();
                overlaysVisible = overlayCount > 0;
                lastAppliedRevision = revision;
                setNoError();
                return;
            }

            // Полная пересборка — асинхронно, с троттлингом.
            scheduleBuild(revision, configHash, geometryHash);
            setNoError();
        } catch (Throwable t) {
            setError(t);
        }
    }

    private void scheduleBuild(long revision, int configHash, int geometryHash) {
        if (building) return;
        long now = System.currentTimeMillis();
        if (now - lastBuildStartMillis < REBUILD_THROTTLE_MS) return;

        BuildInputs inputs = snapshotInputs(revision, configHash, geometryHash, buildGeneration);

        building = true;
        lastBuildStartMillis = now;
        scheduledRevision = revision;
        scheduledConfigHash = configHash;

        buildExecutor.submit(() -> {
            BuildResult result;
            try {
                result = buildOverlays(inputs);
            } catch (Throwable t) {
                result = BuildResult.failed(inputs, t.getClass().getSimpleName(), t);
            } finally {
                building = false;
            }
            pendingResult = result;
        });
    }

    // Применяет готовый результат фоновой сборки на клиентском тике: показывает новые
    // оверлеи, затем удаляет старые (минимум мерцания).
    private void drainPendingResult() throws Exception {
        BuildResult result = pendingResult;
        if (result == null) return;
        pendingResult = null;

        // Результат устарел (был сброс/логаут после запуска сборки) — игнорируем.
        if (result.generation != buildGeneration) return;

        if (result.failed) {
            if (result.error != null) {
                setError(result.error);
            }
            // Позволим пересобрать позже.
            scheduledRevision = Long.MIN_VALUE;
            return;
        }

        List<PolygonOverlay> previous = List.copyOf(activeOverlays);
        activeOverlays.clear();
        activeSectorOverlays.clear();

        for (PolygonOverlay overlay : result.overlays) {
            api.show(overlay);
            activeOverlays.add(overlay);
        }
        activeSectorOverlays.putAll(result.sectorOverlays);

        for (PolygonOverlay overlay : previous) {
            api.remove(overlay);
        }

        overlayCount = activeOverlays.size();
        overlaysVisible = overlayCount > 0;
        lastAppliedRevision = result.revision;
        lastAppliedConfigHash = result.configHash;
        lastAppliedGeometryHash = result.geometryHash;
        setNoError();
    }

    // ==== Фоновое построение (без обращений к JourneyMap API) ====

    private BuildResult buildOverlays(BuildInputs in) {
        List<PolygonOverlay> overlays = new ArrayList<>();
        Map<String, PolygonOverlay> sectorOverlays = new LinkedHashMap<>();

        buildNeutralRegionOverlays(in, overlays);
        buildOwnerOverlays(in, overlays);
        buildRegionBorderOverlays(in, overlays);
        buildFrontLineOverlays(in, overlays);
        buildActiveSectorOverlays(in, overlays, sectorOverlays);

        return BuildResult.ok(in, overlays, sectorOverlays);
    }

    private void buildNeutralRegionOverlays(BuildInputs in, List<PolygonOverlay> out) {
        ShapeProperties shape = new ShapeProperties()
                .setFillColor(in.neutralColor)
                .setFillOpacity(alpha(in.fillAlpha))
                .setStrokeColor(in.regionBorderColor)
                .setStrokeOpacity(alpha(in.borderAlpha))
                .setStrokeWidth(1.5f);

        for (RegionMapSyncPacket.RegionEntry region : in.regions) {
            Set<ChunkPos> chunks = neutralChunks(in, region);
            if (chunks.isEmpty()) continue;
            ResourceKey<Level> dimension = dimensionKey(region.dimension());
            for (MapPolygonWithHoles polygon : PolygonHelper.createChunksPolygon(chunks, MAP_Y)) {
                PolygonOverlay overlay = new PolygonOverlay(Pjmbasemod.MODID, dimension, shape, polygon);
                overlay.setOverlayGroupName("PJM Frontline")
                        .setTitle(null)
                        .setLabel(null)
                        .setDisplayOrder(NEUTRAL_DISPLAY_ORDER);
                out.add(overlay);
            }
        }
    }

    private void buildOwnerOverlays(BuildInputs in, List<PolygonOverlay> out) {
        Map<OwnerKey, Set<ChunkPos>> chunksByOwner = new LinkedHashMap<>();
        for (FrontlineMapSyncPacket.ChunkEntry chunk : in.chunks) {
            OwnerKey key = new OwnerKey(chunk.dimension(), chunk.ownerId(), chunk.ownerName(), chunk.ownerColor());
            chunksByOwner.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(new ChunkPos(chunk.x(), chunk.z()));
        }

        for (Map.Entry<OwnerKey, Set<ChunkPos>> entry : chunksByOwner.entrySet()) {
            OwnerKey owner = entry.getKey();
            boolean grayZone = owner.isGrayZone();
            // Рамки территорий приглушены: границу соприкосновения рисует линия фронта.
            ShapeProperties shape = new ShapeProperties()
                    .setFillColor(owner.color())
                    .setFillOpacity(grayZone ? Math.max(0.42f, alpha(in.fillAlpha)) : alpha(in.fillAlpha))
                    .setStrokeColor(grayZone ? 0xE6E6E6 : owner.color())
                    .setStrokeOpacity(grayZone ? 0.55f : alpha(in.borderAlpha) * 0.5f)
                    .setStrokeWidth(grayZone ? 1.4f : 1.0f);

            ResourceKey<Level> dimension = dimensionKey(owner.dimension());
            for (MapPolygonWithHoles polygon : PolygonHelper.createChunksPolygon(entry.getValue(), MAP_Y)) {
                PolygonOverlay overlay = new PolygonOverlay(Pjmbasemod.MODID, dimension, shape, polygon);
                overlay.setOverlayGroupName("PJM Frontline")
                        .setTitle(grayZone ? FrontlineTeams.GRAY_ZONE_NAME : "Территория: " + owner.name())
                        .setLabel(owner.name())
                        .setTextProperties(grayZone ? grayZoneTextProperties() : textProperties())
                        .setDisplayOrder(grayZone ? GRAY_ZONE_DISPLAY_ORDER : OWNER_DISPLAY_ORDER);
                out.add(overlay);
            }
        }
    }

    private void buildRegionBorderOverlays(BuildInputs in, List<PolygonOverlay> out) {
        ShapeProperties shape = new ShapeProperties()
                .setFillColor(0x000000)
                .setFillOpacity(0.0f)
                .setStrokeColor(in.regionBorderColor)
                .setStrokeOpacity(alpha(in.borderAlpha))
                .setStrokeWidth(2.5f);

        for (RegionMapSyncPacket.RegionEntry region : in.regions) {
            MapPolygon polygon = PolygonHelper.createBlockRect(
                    new BlockPos(region.minX() << 4, MAP_Y, region.minZ() << 4),
                    new BlockPos((region.maxX() + 1) << 4, MAP_Y, (region.maxZ() + 1) << 4));
            PolygonOverlay overlay = new PolygonOverlay(Pjmbasemod.MODID, dimensionKey(region.dimension()), shape, polygon);
            overlay.setOverlayGroupName("PJM Frontline")
                    .setTitle(null)
                    .setLabel(null)
                    .setDisplayOrder(REGION_BORDER_DISPLAY_ORDER);
            out.add(overlay);
        }
    }

    // Чёткая линия фронта: жирные полосы по рёбрам соприкосновения территории команды
    // с чужой территорией или серой зоной (границы с нейтралью и краем региона — не фронт).
    private void buildFrontLineOverlays(BuildInputs in, List<PolygonOverlay> out) {
        Map<String, Map<Long, String>> ownersByDim = new HashMap<>();
        for (FrontlineMapSyncPacket.ChunkEntry chunk : in.chunks) {
            if (chunk.ownerId().isBlank()) continue;
            ownersByDim.computeIfAbsent(chunk.dimension(), ignored -> new HashMap<>())
                    .put(packChunk(chunk.x(), chunk.z()), chunk.ownerId());
        }

        // Рёбра группируются по (измерение, цвет, сторона, фиксированная координата) для склейки.
        Map<FrontRunKey, List<Integer>> runs = new LinkedHashMap<>();
        for (FrontlineMapSyncPacket.ChunkEntry chunk : in.chunks) {
            if (chunk.ownerId().isBlank() || FrontlineTeams.GRAY_ZONE_ID.equals(chunk.ownerId())) continue;
            Map<Long, String> owners = ownersByDim.get(chunk.dimension());
            for (FrontSide side : FrontSide.values()) {
                String neighborOwner = owners.get(packChunk(chunk.x() + side.dx, chunk.z() + side.dz));
                if (neighborOwner == null || neighborOwner.isBlank() || neighborOwner.equals(chunk.ownerId())) continue;
                boolean horizontal = side.dz != 0;
                int fixed = horizontal ? chunk.z() : chunk.x();
                int varying = horizontal ? chunk.x() : chunk.z();
                runs.computeIfAbsent(new FrontRunKey(chunk.dimension(), chunk.ownerColor(), side, fixed), ignored -> new ArrayList<>())
                        .add(varying);
            }
        }

        for (Map.Entry<FrontRunKey, List<Integer>> entry : runs.entrySet()) {
            FrontRunKey key = entry.getKey();
            List<Integer> coords = entry.getValue();
            coords.sort(null);

            ShapeProperties shape = new ShapeProperties()
                    .setFillColor(key.color())
                    .setFillOpacity(0.9f)
                    .setStrokeColor(key.color())
                    .setStrokeOpacity(0.95f)
                    .setStrokeWidth(1.0f);
            ResourceKey<Level> dimension = dimensionKey(key.dimension());

            int start = coords.getFirst();
            int prev = start;
            for (int i = 1; i <= coords.size(); i++) {
                int current = i < coords.size() ? coords.get(i) : Integer.MIN_VALUE;
                if (i < coords.size() && current == prev + 1) {
                    prev = current;
                    continue;
                }
                out.add(frontLineOverlay(dimension, shape, key, start, prev));
                start = current;
                prev = current;
            }
        }
    }

    private PolygonOverlay frontLineOverlay(ResourceKey<Level> dimension, ShapeProperties shape, FrontRunKey key, int startChunk, int endChunk) {
        int minX, maxX, minZ, maxZ;
        switch (key.side()) {
            case NORTH -> {
                minX = startChunk << 4;
                maxX = (endChunk + 1) << 4;
                minZ = key.fixed() << 4;
                maxZ = minZ + FRONT_LINE_WIDTH_BLOCKS;
            }
            case SOUTH -> {
                minX = startChunk << 4;
                maxX = (endChunk + 1) << 4;
                maxZ = (key.fixed() + 1) << 4;
                minZ = maxZ - FRONT_LINE_WIDTH_BLOCKS;
            }
            case WEST -> {
                minZ = startChunk << 4;
                maxZ = (endChunk + 1) << 4;
                minX = key.fixed() << 4;
                maxX = minX + FRONT_LINE_WIDTH_BLOCKS;
            }
            default -> {
                minZ = startChunk << 4;
                maxZ = (endChunk + 1) << 4;
                maxX = (key.fixed() + 1) << 4;
                minX = maxX - FRONT_LINE_WIDTH_BLOCKS;
            }
        }
        MapPolygon polygon = PolygonHelper.createBlockRect(
                new BlockPos(minX, MAP_Y, minZ),
                new BlockPos(maxX, MAP_Y, maxZ));
        PolygonOverlay overlay = new PolygonOverlay(Pjmbasemod.MODID, dimension, shape, polygon);
        overlay.setOverlayGroupName("PJM Frontline")
                .setTitle(null)
                .setLabel(null)
                .setDisplayOrder(FRONT_LINE_DISPLAY_ORDER);
        return overlay;
    }

    private void buildActiveSectorOverlays(BuildInputs in, List<PolygonOverlay> out, Map<String, PolygonOverlay> sectorOut) {
        for (FrontlineMapSyncPacket.SectorEntry sector : in.sectors) {
            MapPolygon polygon = PolygonHelper.createBlockRect(
                    new BlockPos(sector.minX() << 4, MAP_Y, sector.minZ() << 4),
                    new BlockPos((sector.maxX() + 1) << 4, MAP_Y, (sector.maxZ() + 1) << 4));
            String label = activeSectorLabel(sector);
            ResourceKey<Level> dimension = dimensionKey(sector.dimension());
            PolygonOverlay overlay = new PolygonOverlay(Pjmbasemod.MODID, dimension, activeSectorShape(sector, in.fillAlpha, in.borderAlpha), polygon);
            overlay.setOverlayGroupName("PJM Frontline")
                    .setTitle(activeSectorTitle(sector))
                    .setLabel(label)
                    .setTextProperties(activeTextProperties(sector.contested()))
                    .setDisplayOrder(ACTIVE_SECTOR_DISPLAY_ORDER);
            out.add(overlay);
            sectorOut.put(activeSectorKey(sector), overlay);
        }
    }

    // Быстрый inline-путь (клиентский поток): только обновление подписей секторов.
    private boolean updateActiveSectorLabels() throws Exception {
        for (FrontlineMapSyncPacket.SectorEntry sector : ClientFrontlineState.sectors()) {
            PolygonOverlay overlay = activeSectorOverlays.get(activeSectorKey(sector));
            if (overlay == null) return false;

            String label = activeSectorLabel(sector);
            overlay.setTitle(activeSectorTitle(sector))
                    .setLabel(label)
                    .setTextProperties(activeTextProperties(sector.contested()));
            overlay.setShapeProperties(activeSectorShape(sector,
                    Config.getFrontlineJourneyMapFillAlpha(), Config.getFrontlineJourneyMapBorderAlpha()));
            overlay.flagForRerender();
            api.show(overlay);
        }
        return activeSectorOverlays.size() == ClientFrontlineState.sectors().size();
    }

    // Снимок состояния на клиентском потоке: замороженные данные + разрешённые значения
    // конфига, чтобы фоновая сборка была самодостаточной и согласованной с configHash.
    private BuildInputs snapshotInputs(long revision, int configHash, int geometryHash, int generation) {
        List<RegionMapSyncPacket.RegionEntry> regions = new ArrayList<>(ClientRegionState.frontlineRegions());
        List<FrontlineMapSyncPacket.ChunkEntry> chunks = ClientFrontlineState.chunks();
        List<FrontlineMapSyncPacket.SectorEntry> sectors = new ArrayList<>(ClientFrontlineState.sectors());

        // Множество занятых чанков по измерению — чтобы neutralChunks не читал живое состояние.
        Map<String, Set<Long>> occupiedByDim = new HashMap<>();
        for (FrontlineMapSyncPacket.ChunkEntry chunk : chunks) {
            occupiedByDim
                    .computeIfAbsent(chunk.dimension(), ignored -> new HashSet<>())
                    .add(packChunk(chunk.x(), chunk.z()));
        }

        return new BuildInputs(
                revision, configHash, geometryHash, generation,
                regions, chunks, sectors, occupiedByDim,
                Config.getFrontlineJourneyMapNeutralColorRgb(),
                Config.getFrontlineJourneyMapRegionBorderColorRgb(),
                Config.getFrontlineJourneyMapFillAlpha(),
                Config.getFrontlineJourneyMapBorderAlpha());
    }

    private Set<ChunkPos> neutralChunks(BuildInputs in, RegionMapSyncPacket.RegionEntry region) {
        Set<Long> occupied = in.occupiedByDim.get(region.dimension());
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (int z = region.minZ(); z <= region.maxZ(); z++) {
            for (int x = region.minX(); x <= region.maxX(); x++) {
                if (occupied == null || !occupied.contains(packChunk(x, z))) {
                    chunks.add(new ChunkPos(x, z));
                }
            }
        }
        return chunks;
    }

    private void clearOverlays() {
        // Отбрасываем возможный in-flight/pending результат: он относится к прошлому состоянию.
        buildGeneration++;
        pendingResult = null;
        scheduledRevision = Long.MIN_VALUE;
        try {
            for (PolygonOverlay overlay : activeOverlays) {
                api.remove(overlay);
            }
            activeOverlays.clear();
            activeSectorOverlays.clear();
            api.removeAll(Pjmbasemod.MODID, DisplayType.Polygon);
        } catch (Throwable t) {
            setError(t);
            return;
        }
        overlaysVisible = false;
        overlayCount = 0;
    }

    private void resetTracking() {
        lastAppliedRevision = -1L;
        scheduledRevision = Long.MIN_VALUE;
    }

    private void setNoError() {
        lastError = "none";
    }

    private void setError(Throwable t) {
        String marker = t.getClass().getSimpleName();
        lastError = marker;
        if (marker.equals(lastLoggedError)) return;
        lastLoggedError = marker;
        Pjmbasemod.LOGGER.warn("[FRONTLINE][JourneyMap] Runtime error: {}", marker, t);
    }

    private static ResourceKey<Level> dimensionKey(String dimension) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
    }

    private static TextProperties textProperties() {
        return new TextProperties()
                .setActiveUIs(Context.UI.Fullscreen)
                .setColor(0xFFFFFF)
                .setBackgroundColor(0x111111)
                .setBackgroundOpacity(0.65f)
                .setOpacity(1.0f)
                .setMinZoom(2)
                .setFontShadow(true);
    }

    private static TextProperties activeTextProperties(boolean contested) {
        return new TextProperties()
                .setActiveUIs(Context.UI.Fullscreen)
                .setColor(contested ? 0xFFE08A : 0xFFFFFF)
                .setBackgroundColor(0x080808)
                .setBackgroundOpacity(0.82f)
                .setOpacity(1.0f)
                .setScale(2.0f)
                .setMinZoom(1)
                .setFontShadow(true);
    }

    private static ShapeProperties activeSectorShape(FrontlineMapSyncPacket.SectorEntry sector, int fillAlpha, int borderAlpha) {
        int lineColor = sector.contested() ? 0xFFC13D : 0xFFFFFF;
        int fillColor = sector.contested() ? 0xFFC13D : sector.teamColor();
        float baseFill = alpha(fillAlpha);
        float fillOpacity = Math.min(0.56f, Math.max(0.32f, baseFill * 1.1f));
        return new ShapeProperties()
                .setFillColor(fillColor)
                .setFillOpacity(fillOpacity)
                .setStrokeColor(lineColor)
                .setStrokeOpacity(Math.min(1.0f, Math.max(0.72f, alpha(borderAlpha))))
                .setStrokeWidth(sector.contested() ? 3.6f : 3.2f);
    }

    private static TextProperties grayZoneTextProperties() {
        return new TextProperties()
                .setActiveUIs(Context.UI.Fullscreen)
                .setColor(0xE6E6E6)
                .setBackgroundColor(0x101010)
                .setBackgroundOpacity(0.72f)
                .setOpacity(1.0f)
                .setMinZoom(2)
                .setFontShadow(true);
    }

    private static String activeSectorLabel(FrontlineMapSyncPacket.SectorEntry sector) {
        if (sector.contested()) return "Оспаривается";
        return "Захват: " + sector.teamName();
    }

    private static String activeSectorTitle(FrontlineMapSyncPacket.SectorEntry sector) {
        String status = sector.contested() ? "Оспаривается" : "Захват: " + sector.teamName();
        return status + " | Сектор №" + sector.sectorX() + ", " + sector.sectorZ();
    }

    private static String activeSectorKey(FrontlineMapSyncPacket.SectorEntry sector) {
        return sector.dimension() + "|" + sector.regionName() + "|" + sector.sectorX() + "|" + sector.sectorZ();
    }

    private static long packChunk(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static float alpha(int alpha) {
        return Math.max(0, Math.min(255, alpha)) / 255.0f;
    }

    private static int configHash() {
        int hash = Config.getFrontlineJourneyMapFillAlpha();
        hash = 31 * hash + Config.getFrontlineJourneyMapBorderAlpha();
        hash = 31 * hash + Config.getFrontlineJourneyMapNeutralColorRgb();
        hash = 31 * hash + Config.getFrontlineJourneyMapRegionBorderColorRgb();
        hash = 31 * hash + (Config.isFrontlineJourneyMapEnabled() ? 1 : 0);
        return hash;
    }

    private static long combinedMapRevision() {
        return ClientFrontlineState.mapRevision() * 31L + ClientRegionState.mapRevision();
    }

    private static int geometryHash() {
        int hash = 1;
        for (RegionMapSyncPacket.RegionEntry region : ClientRegionState.frontlineRegions()) {
            hash = 31 * hash + region.dimension().hashCode();
            hash = 31 * hash + region.name().hashCode();
            hash = 31 * hash + region.displayName().hashCode();
            hash = 31 * hash + region.minX();
            hash = 31 * hash + region.minZ();
            hash = 31 * hash + region.maxX();
            hash = 31 * hash + region.maxZ();
        }
        for (FrontlineMapSyncPacket.ChunkEntry chunk : ClientFrontlineState.chunks()) {
            hash = 31 * hash + chunk.dimension().hashCode();
            hash = 31 * hash + chunk.x();
            hash = 31 * hash + chunk.z();
            hash = 31 * hash + chunk.ownerId().hashCode();
            hash = 31 * hash + chunk.ownerName().hashCode();
            hash = 31 * hash + chunk.ownerColor();
        }
        for (FrontlineMapSyncPacket.SectorEntry sector : ClientFrontlineState.sectors()) {
            hash = 31 * hash + sector.dimension().hashCode();
            hash = 31 * hash + sector.regionName().hashCode();
            hash = 31 * hash + sector.sectorX();
            hash = 31 * hash + sector.sectorZ();
            hash = 31 * hash + sector.minX();
            hash = 31 * hash + sector.minZ();
            hash = 31 * hash + sector.maxX();
            hash = 31 * hash + sector.maxZ();
            hash = 31 * hash + sector.teamName().hashCode();
            hash = 31 * hash + sector.teamColor();
            hash = 31 * hash + (sector.contested() ? 1 : 0);
        }
        return hash;
    }

    private record OwnerKey(String dimension, String id, String name, int color) {
        private boolean isGrayZone() {
            return FrontlineTeams.GRAY_ZONE_ID.equals(id);
        }
    }

    private enum FrontSide {
        NORTH(0, -1), SOUTH(0, 1), WEST(-1, 0), EAST(1, 0);

        final int dx;
        final int dz;

        FrontSide(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }

    private record FrontRunKey(String dimension, int color, FrontSide side, int fixed) {}

    // Замороженный на клиентском потоке вход для фоновой сборки.
    private record BuildInputs(
            long revision,
            int configHash,
            int geometryHash,
            int generation,
            List<RegionMapSyncPacket.RegionEntry> regions,
            List<FrontlineMapSyncPacket.ChunkEntry> chunks,
            List<FrontlineMapSyncPacket.SectorEntry> sectors,
            Map<String, Set<Long>> occupiedByDim,
            int neutralColor,
            int regionBorderColor,
            int fillAlpha,
            int borderAlpha) {
    }

    // Результат фоновой сборки; применяется на клиентском тике в drainPendingResult().
    private static final class BuildResult {
        final long revision;
        final int configHash;
        final int geometryHash;
        final int generation;
        final List<PolygonOverlay> overlays;
        final Map<String, PolygonOverlay> sectorOverlays;
        final boolean failed;
        final String errorMarker;
        final Throwable error;

        private BuildResult(long revision, int configHash, int geometryHash, int generation,
                            List<PolygonOverlay> overlays, Map<String, PolygonOverlay> sectorOverlays,
                            boolean failed, String errorMarker, Throwable error) {
            this.revision = revision;
            this.configHash = configHash;
            this.geometryHash = geometryHash;
            this.generation = generation;
            this.overlays = overlays;
            this.sectorOverlays = sectorOverlays;
            this.failed = failed;
            this.errorMarker = errorMarker;
            this.error = error;
        }

        static BuildResult ok(BuildInputs in, List<PolygonOverlay> overlays, Map<String, PolygonOverlay> sectorOverlays) {
            return new BuildResult(in.revision, in.configHash, in.geometryHash, in.generation,
                    overlays, sectorOverlays, false, null, null);
        }

        static BuildResult failed(BuildInputs in, String marker, Throwable error) {
            return new BuildResult(in.revision, in.configHash, in.geometryHash, in.generation,
                    List.of(), Map.of(), true, marker, error);
        }
    }
}
