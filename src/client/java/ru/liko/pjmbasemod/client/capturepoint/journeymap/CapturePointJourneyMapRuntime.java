package ru.liko.pjmbasemod.client.capturepoint.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.common.event.ClientEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.capturepoint.ClientCapturePointState;
import ru.liko.pjmbasemod.common.capturepoint.CapturePoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Оверлей точек захвата на JourneyMap: один полигон на точку, цвет = владелец
 * (или нейтральный), лейбл = displayName + состояние/процент в центроиде.
 * Оспариваемая точка — оранжевый цвет. Строится синхронно на клиентском тике.
 */
public final class CapturePointJourneyMapRuntime implements CapturePointJourneyMapBridge.Adapter {

    private static final String GROUP_NAME = "PJM Capture Points";
    private static final int DISPLAY_ORDER = 160;
    private static final int MAP_Y = 64;
    private static final int NEUTRAL_COLOR = 0x9B9B9B;
    private static final int CONTESTED_COLOR = 0xFFC13D;

    private final IClientAPI api;
    private boolean mappingActive;
    private int lastPointCount = -1;
    private long lastSignature = -1L;
    private final List<PolygonOverlay> activeOverlays = new ArrayList<>();
    private String lastLoggedError = "";

    private CapturePointJourneyMapRuntime(IClientAPI api) {
        this.api = api;
    }

    public static void initialize(IClientAPI api) {
        CapturePointJourneyMapRuntime runtime = new CapturePointJourneyMapRuntime(api);
        CapturePointJourneyMapBridge.attach(runtime);
        ClientEventRegistry.MAPPING_EVENT.subscribe(Pjmbasemod.MODID, runtime::onMappingEvent);
    }

    @Override
    public void onClientTick() {
        if (!Config.isCapturePointJourneyMapEnabled()) {
            clearOverlays();
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
    }

    private void onMappingEvent(MappingEvent event) {
        mappingActive = event.getStage() == MappingEvent.Stage.MAPPING_STARTED;
        if (mappingActive) {
            applyIfNeeded();
        } else {
            clearOverlays();
        }
    }

    private void applyIfNeeded() {
        List<CapturePoint> points = ClientCapturePointState.points();
        long signature = signature(points);
        if (signature == lastSignature && points.size() == lastPointCount) return;
        lastSignature = signature;
        lastPointCount = points.size();

        try {
            if (!api.playerAccepts(Pjmbasemod.MODID, DisplayType.Polygon)) {
                clearOverlays();
                return;
            }
            clearOverlays();
            int fillAlpha = Config.getCapturePointJourneyMapFillAlpha();
            int borderAlpha = Config.getCapturePointJourneyMapBorderAlpha();
            for (CapturePoint cp : points) {
                if (cp.vertices().size() < 3) continue;
                PolygonOverlay overlay = buildOverlay(cp, fillAlpha, borderAlpha);
                if (overlay != null) {
                    api.show(overlay);
                    activeOverlays.add(overlay);
                }
            }
        } catch (Throwable t) {
            String marker = t.getClass().getSimpleName();
            if (!marker.equals(lastLoggedError)) {
                lastLoggedError = marker;
                Pjmbasemod.LOGGER.warn("[CAPTUREPOINTS][JourneyMap] Runtime error: {}", marker, t);
            }
        }
    }

    private PolygonOverlay buildOverlay(CapturePoint cp, int fillAlpha, int borderAlpha) {
        List<BlockPos> points = new ArrayList<>(cp.vertices().size());
        for (CapturePoint.Vertex v : cp.vertices()) {
            points.add(new BlockPos(v.x(), MAP_Y, v.z()));
        }
        MapPolygon polygon = new MapPolygon(points);

        int color = cp.contested() ? CONTESTED_COLOR
                : cp.ownerTeamId().isEmpty() ? Config.getCapturePointJourneyMapNeutralColorRgb()
                : cp.ownerColor();
        ShapeProperties shape = new ShapeProperties()
                .setFillColor(color)
                .setFillOpacity(fillAlpha / 255f)
                .setStrokeColor(color)
                .setStrokeOpacity(borderAlpha / 255f)
                .setStrokeWidth(2.5f);

        CapturePoint.Vertex centroid = CapturePoint.centroid(cp.vertices());
        String label = cp.displayName();
        if (cp.contested()) {
            label += " [оспаривается]";
        } else if (!cp.ownerTeamId().isEmpty() && cp.progressPercent() < 100) {
            label += " [" + cp.progressPercent() + "%]";
        } else if (cp.ownerTeamId().isEmpty() && cp.progressPercent() > 0) {
            label += " [захват " + cp.progressPercent() + "%]";
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(cp.dimension()));
        PolygonOverlay overlay = new PolygonOverlay(Pjmbasemod.MODID, dimension, shape, polygon);
        overlay.setOverlayGroupName(GROUP_NAME)
                .setTitle(label)
                .setLabel(label)
                .setTextProperties(textProperties(color))
                .setDisplayOrder(DISPLAY_ORDER);
        return overlay;
    }

    private static TextProperties textProperties(int color) {
        return new TextProperties()
                .setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap)
                .setColor(color)
                .setBackgroundColor(0x20000000)
                .setBackgroundOpacity(0.7f)
                .setOpacity(1.0f)
                .setScale(1.2f)
                .setMinZoom(0)
                .setFontShadow(true);
    }

    private void clearOverlays() {
        try {
            for (PolygonOverlay overlay : activeOverlays) {
                api.remove(overlay);
            }
        } catch (Throwable ignored) {
        }
        activeOverlays.clear();
    }

    /** Сигнатура состояния для дедупликации пересборки. */
    private static long signature(List<CapturePoint> points) {
        long sig = 0;
        for (CapturePoint cp : points) {
            sig = sig * 31 + cp.id().hashCode();
            sig = sig * 31 + cp.ownerTeamId().hashCode();
            sig = sig * 31 + cp.captureTeamId().hashCode();
            sig = sig * 31 + cp.progressPercent();
            sig = sig * 31 + (cp.contested() ? 1 : 0);
            sig = sig * 31 + cp.vertices().size();
        }
        return sig;
    }
}

