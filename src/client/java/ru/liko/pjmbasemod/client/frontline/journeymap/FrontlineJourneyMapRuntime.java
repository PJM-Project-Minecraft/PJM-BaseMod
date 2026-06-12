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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FrontlineJourneyMapRuntime implements FrontlineJourneyMapBridge.Adapter {

    private static final int MAP_Y = 64;
    private static final int NEUTRAL_DISPLAY_ORDER = 100;
    private static final int OWNER_DISPLAY_ORDER = 110;
    private static final int GRAY_ZONE_DISPLAY_ORDER = 115;
    private static final int REGION_BORDER_DISPLAY_ORDER = 120;
    private static final int ACTIVE_SECTOR_DISPLAY_ORDER = 180;

    private final IClientAPI api;
    private boolean mappingActive;
    private boolean overlaysVisible;
    private final List<PolygonOverlay> activeOverlays = new ArrayList<>();
    private final Map<String, PolygonOverlay> activeSectorOverlays = new LinkedHashMap<>();
    private long lastAppliedRevision = -1L;
    private int lastConfigHash;
    private int lastGeometryHash;
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
            lastAppliedRevision = -1L;
            return;
        }
        if (Minecraft.getInstance().player == null) {
            mappingActive = false;
            clearOverlays();
            return;
        }
        if (!mappingActive) return;
        applyIfNeeded();
    }

    @Override
    public void onLogout() {
        mappingActive = false;
        clearOverlays();
        lastAppliedRevision = -1L;
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
            lastAppliedRevision = -1L;
            applyIfNeeded();
        } else {
            clearOverlays();
        }
    }

    private void applyIfNeeded() {
        try {
            if (!api.playerAccepts(Pjmbasemod.MODID, DisplayType.Polygon)) {
                clearOverlays();
                lastAppliedRevision = -1L;
                setNoError();
                return;
            }

            long revision = combinedMapRevision();
            int configHash = configHash();
            if (revision == lastAppliedRevision && configHash == lastConfigHash) return;

            int geometryHash = geometryHash();
            if (configHash == lastConfigHash && geometryHash == lastGeometryHash && updateActiveSectorLabels()) {
                overlayCount = activeOverlays.size();
                overlaysVisible = overlayCount > 0;
                lastAppliedRevision = revision;
                setNoError();
                return;
            }

            overlayCount = replaceOverlays();
            overlaysVisible = overlayCount > 0;
            lastAppliedRevision = revision;
            lastConfigHash = configHash;
            lastGeometryHash = geometryHash;
            setNoError();
        } catch (Throwable t) {
            setError(t);
        }
    }

    private int replaceOverlays() throws Exception {
        List<PolygonOverlay> previous = List.copyOf(activeOverlays);
        activeOverlays.clear();
        activeSectorOverlays.clear();
        int created = createOverlays();
        for (PolygonOverlay overlay : previous) {
            api.remove(overlay);
        }
        return created;
    }

    private boolean updateActiveSectorLabels() throws Exception {
        for (FrontlineMapSyncPacket.SectorEntry sector : ClientFrontlineState.sectors()) {
            PolygonOverlay overlay = activeSectorOverlays.get(activeSectorKey(sector));
            if (overlay == null) return false;

            String label = activeSectorLabel(sector);
            overlay.setTitle(activeSectorTitle(sector))
                    .setLabel(label)
                    .setTextProperties(activeTextProperties(sector.contested()));
            overlay.setShapeProperties(activeSectorShape(sector));
            overlay.flagForRerender();
            api.show(overlay);
        }
        return activeSectorOverlays.size() == ClientFrontlineState.sectors().size();
    }

    private int createOverlays() throws Exception {
        int created = 0;
        created += createNeutralRegionOverlays();
        created += createOwnerOverlays();
        created += createRegionBorderOverlays();
        created += createActiveSectorOverlays();
        return created;
    }

    private int createNeutralRegionOverlays() throws Exception {
        int created = 0;
        ShapeProperties shape = new ShapeProperties()
                .setFillColor(Config.getFrontlineJourneyMapNeutralColorRgb())
                .setFillOpacity(alpha(Config.getFrontlineJourneyMapFillAlpha()))
                .setStrokeColor(Config.getFrontlineJourneyMapRegionBorderColorRgb())
                .setStrokeOpacity(alpha(Config.getFrontlineJourneyMapBorderAlpha()))
                .setStrokeWidth(1.5f);

        for (RegionMapSyncPacket.RegionEntry region : ClientRegionState.frontlineRegions()) {
            Set<ChunkPos> chunks = neutralChunks(region);
            if (chunks.isEmpty()) continue;
            ResourceKey<Level> dimension = dimensionKey(region.dimension());
            for (MapPolygonWithHoles polygon : PolygonHelper.createChunksPolygon(chunks, MAP_Y)) {
                PolygonOverlay overlay = new PolygonOverlay(Pjmbasemod.MODID, dimension, shape, polygon);
                overlay.setOverlayGroupName("PJM Frontline")
                        .setTitle(null)
                        .setLabel(null)
                        .setDisplayOrder(NEUTRAL_DISPLAY_ORDER);
                api.show(overlay);
                activeOverlays.add(overlay);
                created++;
            }
        }
        return created;
    }

    private int createOwnerOverlays() throws Exception {
        Map<OwnerKey, Set<ChunkPos>> chunksByOwner = new LinkedHashMap<>();
        for (FrontlineMapSyncPacket.ChunkEntry chunk : ClientFrontlineState.chunks()) {
            OwnerKey key = new OwnerKey(chunk.dimension(), chunk.ownerId(), chunk.ownerName(), chunk.ownerColor());
            chunksByOwner.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(new ChunkPos(chunk.x(), chunk.z()));
        }

        int created = 0;
        for (Map.Entry<OwnerKey, Set<ChunkPos>> entry : chunksByOwner.entrySet()) {
            OwnerKey owner = entry.getKey();
            boolean grayZone = owner.isGrayZone();
            ShapeProperties shape = new ShapeProperties()
                    .setFillColor(owner.color())
                    .setFillOpacity(grayZone ? Math.max(0.42f, alpha(Config.getFrontlineJourneyMapFillAlpha())) : alpha(Config.getFrontlineJourneyMapFillAlpha()))
                    .setStrokeColor(grayZone ? 0xE6E6E6 : owner.color())
                    .setStrokeOpacity(grayZone ? 0.95f : alpha(Config.getFrontlineJourneyMapBorderAlpha()))
                    .setStrokeWidth(grayZone ? 2.6f : 2.0f);

            ResourceKey<Level> dimension = dimensionKey(owner.dimension());
            for (MapPolygonWithHoles polygon : PolygonHelper.createChunksPolygon(entry.getValue(), MAP_Y)) {
                PolygonOverlay overlay = new PolygonOverlay(Pjmbasemod.MODID, dimension, shape, polygon);
                overlay.setOverlayGroupName("PJM Frontline")
                        .setTitle(grayZone ? FrontlineTeams.GRAY_ZONE_NAME : "Территория: " + owner.name())
                        .setLabel(owner.name())
                        .setTextProperties(grayZone ? grayZoneTextProperties() : textProperties())
                        .setDisplayOrder(grayZone ? GRAY_ZONE_DISPLAY_ORDER : OWNER_DISPLAY_ORDER);
                api.show(overlay);
                activeOverlays.add(overlay);
                created++;
            }
        }
        return created;
    }

    private int createRegionBorderOverlays() throws Exception {
        int created = 0;
        ShapeProperties shape = new ShapeProperties()
                .setFillColor(0x000000)
                .setFillOpacity(0.0f)
                .setStrokeColor(Config.getFrontlineJourneyMapRegionBorderColorRgb())
                .setStrokeOpacity(alpha(Config.getFrontlineJourneyMapBorderAlpha()))
                .setStrokeWidth(2.5f);

        for (RegionMapSyncPacket.RegionEntry region : ClientRegionState.frontlineRegions()) {
            MapPolygon polygon = PolygonHelper.createBlockRect(
                    new BlockPos(region.minX() << 4, MAP_Y, region.minZ() << 4),
                    new BlockPos((region.maxX() + 1) << 4, MAP_Y, (region.maxZ() + 1) << 4));
            PolygonOverlay overlay = new PolygonOverlay(Pjmbasemod.MODID, dimensionKey(region.dimension()), shape, polygon);
            overlay.setOverlayGroupName("PJM Frontline")
                    .setTitle(null)
                    .setLabel(null)
                    .setDisplayOrder(REGION_BORDER_DISPLAY_ORDER);
            api.show(overlay);
            activeOverlays.add(overlay);
            created++;
        }
        return created;
    }

    private int createActiveSectorOverlays() throws Exception {
        int created = 0;

        for (FrontlineMapSyncPacket.SectorEntry sector : ClientFrontlineState.sectors()) {
            MapPolygon polygon = PolygonHelper.createBlockRect(
                    new BlockPos(sector.minX() << 4, MAP_Y, sector.minZ() << 4),
                    new BlockPos((sector.maxX() + 1) << 4, MAP_Y, (sector.maxZ() + 1) << 4));
            String label = activeSectorLabel(sector);
            ResourceKey<Level> dimension = dimensionKey(sector.dimension());
            PolygonOverlay overlay = new PolygonOverlay(Pjmbasemod.MODID, dimension, activeSectorShape(sector), polygon);
            overlay.setOverlayGroupName("PJM Frontline")
                    .setTitle(activeSectorTitle(sector))
                    .setLabel(label)
                    .setTextProperties(activeTextProperties(sector.contested()))
                    .setDisplayOrder(ACTIVE_SECTOR_DISPLAY_ORDER);
            api.show(overlay);
            activeOverlays.add(overlay);
            activeSectorOverlays.put(activeSectorKey(sector), overlay);
            created++;
        }
        return created;
    }

    private Set<ChunkPos> neutralChunks(RegionMapSyncPacket.RegionEntry region) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (int z = region.minZ(); z <= region.maxZ(); z++) {
            for (int x = region.minX(); x <= region.maxX(); x++) {
                if (ClientFrontlineState.chunk(region.dimension(), x, z) == null) {
                    chunks.add(new ChunkPos(x, z));
                }
            }
        }
        return chunks;
    }

    private void clearOverlays() {
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

    private static ShapeProperties activeSectorShape(FrontlineMapSyncPacket.SectorEntry sector) {
        int lineColor = sector.contested() ? 0xFFC13D : 0xFFFFFF;
        int fillColor = sector.contested() ? 0xFFC13D : sector.teamColor();
        float baseFill = alpha(Config.getFrontlineJourneyMapFillAlpha());
        float fillOpacity = Math.min(0.56f, Math.max(0.32f, baseFill * 1.1f));
        return new ShapeProperties()
                .setFillColor(fillColor)
                .setFillOpacity(fillOpacity)
                .setStrokeColor(lineColor)
                .setStrokeOpacity(Math.min(1.0f, Math.max(0.72f, alpha(Config.getFrontlineJourneyMapBorderAlpha()))))
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
}
