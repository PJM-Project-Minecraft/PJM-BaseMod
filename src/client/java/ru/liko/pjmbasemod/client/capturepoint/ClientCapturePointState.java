package ru.liko.pjmbasemod.client.capturepoint;

import ru.liko.pjmbasemod.common.capturepoint.CapturePoint;
import ru.liko.pjmbasemod.common.network.packet.CapturePointHudPacket;
import ru.liko.pjmbasemod.common.network.packet.CapturePointMapSyncPacket;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Клиентское зеркало точек захвата: список точек (для карты/HUD) + HUD-данные
 * точки, в которой стоит локальный игрок. Карты пересобираются атомарно под
 * volatile-ссылкой (как ClientFrontlineState ранее).
 */
public final class ClientCapturePointState {

    private static volatile List<CapturePoint> points = List.of();
    private static volatile boolean sequential;
    private static volatile @Nullable CapturePointHudPacket hud = null;

    private ClientCapturePointState() {}

    public static List<CapturePoint> points() {
        return points;
    }

    /** Серверный флаг последовательного (цепного) захвата — из последнего sync-пакета. */
    public static boolean sequential() {
        return sequential;
    }

    public static void updateMap(CapturePointMapSyncPacket packet) {
        points = List.copyOf(packet.points());
        sequential = packet.sequential();
    }

    public static @Nullable CapturePointHudPacket hud() {
        return hud;
    }

    public static void updateHud(CapturePointHudPacket packet) {
        hud = packet.pointId().isEmpty() ? null : packet;
    }

    public static void reset() {
        points = List.of();
        sequential = false;
        hud = null;
    }
}
