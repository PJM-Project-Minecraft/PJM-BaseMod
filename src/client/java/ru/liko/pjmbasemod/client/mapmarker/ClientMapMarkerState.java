package ru.liko.pjmbasemod.client.mapmarker;

import java.util.List;

import ru.liko.pjmbasemod.common.network.packet.MapMarkerSyncPacket;

/** Клиентское зеркало тактических меток своей команды — для карты. */
public final class ClientMapMarkerState {

    private static volatile List<MapMarkerSyncPacket.Entry> markers = List.of();

    private ClientMapMarkerState() {}

    public static List<MapMarkerSyncPacket.Entry> markers() {
        return markers;
    }

    public static void update(MapMarkerSyncPacket packet) {
        markers = List.copyOf(packet.markers());
    }

    public static void reset() {
        markers = List.of();
    }
}
