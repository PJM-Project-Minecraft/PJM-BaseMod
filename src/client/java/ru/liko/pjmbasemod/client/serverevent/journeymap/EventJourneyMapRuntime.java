package ru.liko.pjmbasemod.client.serverevent.journeymap;

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
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.serverevent.ClientServerEventState;
import ru.liko.pjmbasemod.common.network.packet.EventMapSyncPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Оверлей зоны серверного события на JourneyMap: красный круг + подпись.
 * Один полигон — строится синхронно на клиентском тике, без фонового потока.
 * Инициализируется из EventJourneyMapPlugin (один @JourneyMapPlugin на modid).
 */
public final class EventJourneyMapRuntime implements EventJourneyMapBridge.Adapter {

    private static final String GROUP_NAME = "PJM Events";
    /** Выше всех слоёв фронтлайна (ACTIVE_SECTOR = 180). */
    private static final int DISPLAY_ORDER = 200;
    private static final int CIRCLE_POINTS = 36;
    private static final int MAP_Y = 64;
    private static final int ZONE_COLOR = 0xE03030;

    private final IClientAPI api;

    private boolean mappingActive;
    private long lastAppliedRevision = -1L;
    private final List<PolygonOverlay> activeOverlays = new ArrayList<>();
    private String lastLoggedError = "";

    private EventJourneyMapRuntime(IClientAPI api) {
        this.api = api;
    }

    public static void initialize(IClientAPI api) {
        EventJourneyMapRuntime runtime = new EventJourneyMapRuntime(api);
        EventJourneyMapBridge.attach(runtime);
        ClientEventRegistry.MAPPING_EVENT.subscribe(Pjmbasemod.MODID, runtime::onMappingEvent);
    }

    @Override
    public void onClientTick() {
        if (Minecraft.getInstance().player == null) {
            mappingActive = false;
            clearOverlays();
            lastAppliedRevision = -1L;
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
        long revision = ClientServerEventState.revision();
        if (revision == lastAppliedRevision) return;

        try {
            if (!api.playerAccepts(Pjmbasemod.MODID, DisplayType.Polygon)) {
                clearOverlays();
                return;
            }

            clearOverlays();
            EventMapSyncPacket event = ClientServerEventState.current();
            if (event != null) {
                PolygonOverlay overlay = buildZoneOverlay(event);
                api.show(overlay);
                activeOverlays.add(overlay);
            }
            lastAppliedRevision = revision;
        } catch (Throwable t) {
            String marker = t.getClass().getSimpleName();
            if (!marker.equals(lastLoggedError)) {
                lastLoggedError = marker;
                Pjmbasemod.LOGGER.warn("[EVENTS][JourneyMap] Runtime error: {}", marker, t);
            }
        }
    }

    private PolygonOverlay buildZoneOverlay(EventMapSyncPacket event) {
        List<BlockPos> points = new ArrayList<>(CIRCLE_POINTS);
        for (int i = 0; i < CIRCLE_POINTS; i++) {
            double angle = 2 * Math.PI * i / CIRCLE_POINTS;
            points.add(new BlockPos(
                    event.centerX() + (int) Math.round(Math.cos(angle) * event.radius()),
                    MAP_Y,
                    event.centerZ() + (int) Math.round(Math.sin(angle) * event.radius())));
        }
        MapPolygon polygon = new MapPolygon(points);

        ShapeProperties shape = new ShapeProperties()
                .setFillColor(ZONE_COLOR)
                .setFillOpacity(0.28f)
                .setStrokeColor(ZONE_COLOR)
                .setStrokeOpacity(0.9f)
                .setStrokeWidth(3.0f);

        String label = I18n.get("event.pjmbasemod.drone_raid.zone", event.pointName());
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(event.dimension()));
        PolygonOverlay overlay = new PolygonOverlay(Pjmbasemod.MODID, dimension, shape, polygon);
        overlay.setOverlayGroupName(GROUP_NAME)
                .setTitle(label)
                .setLabel(label)
                .setTextProperties(textProperties())
                .setDisplayOrder(DISPLAY_ORDER);
        return overlay;
    }

    private static TextProperties textProperties() {
        return new TextProperties()
                .setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap)
                .setColor(0xFFD0D0)
                .setBackgroundColor(0x200000)
                .setBackgroundOpacity(0.8f)
                .setOpacity(1.0f)
                .setScale(1.5f)
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
}
