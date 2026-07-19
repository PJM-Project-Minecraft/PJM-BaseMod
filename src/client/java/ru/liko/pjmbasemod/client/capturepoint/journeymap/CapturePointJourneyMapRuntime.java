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
import ru.liko.pjmbasemod.common.teams.Teams;

import javax.annotation.Nullable;
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
    private static final int SELECTED_STROKE = 0xFFFFFFFF;

    private final IClientAPI api;
    private CapturePointMapEditor editor;
    private boolean mappingActive;
    private int lastPointCount = -1;
    private long lastSignature = -1L;
    private long lastEditorRevision = -1L;
    private final List<PolygonOverlay> activeOverlays = new ArrayList<>();
    private String lastLoggedError = "";

    private CapturePointJourneyMapRuntime(IClientAPI api) {
        this.api = api;
    }

    public static void initialize(IClientAPI api) {
        CapturePointJourneyMapRuntime runtime = new CapturePointJourneyMapRuntime(api);
        CapturePointMapEditor editor = new CapturePointMapEditor(runtime, api);
        runtime.editor = editor;
        editor.registerEvents();
        CapturePointJourneyMapBridge.attach(runtime);
        ClientEventRegistry.MAPPING_EVENT.subscribe(Pjmbasemod.MODID, runtime::onMappingEvent);
    }

    /** Принудительная пересборка оверлеев на следующем тике (вызывается редактором). */
    void forceRebuild() {
        lastSignature = -1L;
        lastEditorRevision = -1L;
        if (mappingActive && Minecraft.getInstance().player != null) {
            applyIfNeeded();
        }
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
        if (editor != null) editor.onLogout();
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
        String myTeam = myTeamId();
        boolean sequential = ClientCapturePointState.sequential();
        long signature = signature(points) * 31 + myTeam.hashCode() * 2 + (sequential ? 1 : 0);
        long editorRevision = editor != null ? editor.revision() : 0L;
        if (signature == lastSignature && points.size() == lastPointCount
                && editorRevision == lastEditorRevision) {
            return;
        }
        lastSignature = signature;
        lastPointCount = points.size();
        lastEditorRevision = editorRevision;

        String selectedId = editor != null ? editor.selectedId() : null;

        try {
            if (!api.playerAccepts(Pjmbasemod.MODID, DisplayType.Polygon)) {
                clearOverlays();
                return;
            }
            clearOverlays();
            int fillAlpha = Config.getCapturePointJourneyMapFillAlpha();
            int borderAlpha = Config.getCapturePointJourneyMapBorderAlpha();
            for (CapturePoint cp : points) {
                List<CapturePoint.Vertex> verts = editor != null
                        ? editor.effectiveVertices(cp.id(), cp.vertices())
                        : cp.vertices();
                if (verts.size() < 3) continue;
                boolean selected = cp.id().equals(selectedId);
                PolygonOverlay overlay = buildOverlay(cp, verts, selected, fillAlpha, borderAlpha,
                        points, myTeam, sequential);
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

    private PolygonOverlay buildOverlay(CapturePoint cp, List<CapturePoint.Vertex> verts,
                                        boolean selected, int fillAlpha, int borderAlpha,
                                        List<CapturePoint> allPoints, String myTeam, boolean sequential) {
        List<BlockPos> points = new ArrayList<>(verts.size());
        for (CapturePoint.Vertex v : verts) {
            points.add(new BlockPos(v.x(), MAP_Y, v.z()));
        }
        MapPolygon polygon = new MapPolygon(points);

        int color = cp.contested() ? CONTESTED_COLOR
                : cp.ownerTeamId().isEmpty() ? Config.getCapturePointJourneyMapNeutralColorRgb()
                : cp.ownerColor();
        ShapeProperties shape = new ShapeProperties()
                .setFillColor(color)
                .setFillOpacity(fillAlpha / 255f)
                .setStrokeColor(selected ? SELECTED_STROKE : color)
                .setStrokeOpacity(borderAlpha / 255f)
                .setStrokeWidth(selected ? 4.0f : 2.5f);

        CapturePoint.Vertex centroid = CapturePoint.centroid(verts);
        String label = cp.displayName();
        if (sequential && cp.order() > 0) {
            label = cp.order() + " · " + label;
        }
        if (cp.contested()) {
            label += " [оспаривается]";
        } else if (!cp.ownerTeamId().isEmpty() && cp.progressPercent() < 100) {
            label += " [" + cp.progressPercent() + "%]";
        } else if (cp.ownerTeamId().isEmpty() && cp.progressPercent() > 0) {
            label += " [захват " + cp.progressPercent() + "%]";
        }
        // Подсказка цепного захвата для команды локального игрока: какая точка
        // сейчас доступна к атаке, а какая закрыта гейтом (зеркало canAttack сервера).
        if (sequential && !myTeam.isEmpty() && !myTeam.equals(cp.ownerTeamId())) {
            Boolean open = chainOpen(allPoints, cp, myTeam);
            if (open != null) {
                label += open ? " [доступна]" : " [закрыта • цепочка]";
            }
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

    /** Команда локального игрока по клиентскому scoreboard ({@code ""} — без команды). */
    private static String myTeamId() {
        var player = Minecraft.getInstance().player;
        var team = player != null ? player.getTeam() : null;
        return team == null ? "" : Teams.normalize(team.getName());
    }

    /**
     * Зеркало серверного {@code CapturePointManager.canAttack} по синхронизированным точкам:
     * {@code null} — гейт к точке не применяется (нет строгих order-соседей),
     * иначе — может ли команда атаковать её сейчас (владеет prev/next по order).
     */
    @Nullable
    private static Boolean chainOpen(List<CapturePoint> all, CapturePoint target, String team) {
        CapturePoint prev = null, next = null;
        for (CapturePoint cp : all) {
            if (cp == target || !cp.dimension().equals(target.dimension())) continue;
            if (cp.order() < target.order() && (prev == null || cp.order() > prev.order())) prev = cp;
            if (cp.order() > target.order() && (next == null || cp.order() < next.order())) next = cp;
        }
        if (prev == null && next == null) return null;
        return (prev != null && team.equals(prev.ownerTeamId()))
                || (next != null && team.equals(next.ownerTeamId()));
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
            sig = sig * 31 + cp.order();
        }
        return sig;
    }
}

