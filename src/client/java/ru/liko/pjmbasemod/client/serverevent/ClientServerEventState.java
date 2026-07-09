package ru.liko.pjmbasemod.client.serverevent;

import ru.liko.pjmbasemod.common.network.packet.EventMapSyncPacket;

import javax.annotation.Nullable;

/**
 * Клиентское зеркало активного серверного события (зона для карты).
 * Обновляется из ClientPacketHandlersImpl; revision растёт с каждым sync —
 * по нему JourneyMap-runtime понимает, что оверлей надо перестроить.
 */
public final class ClientServerEventState {

    @Nullable
    private static volatile EventMapSyncPacket current;
    private static volatile long revision;

    private ClientServerEventState() {}

    public static void update(EventMapSyncPacket packet) {
        current = packet.active() ? packet : null;
        revision++;
    }

    public static void clear() {
        current = null;
        revision++;
    }

    @Nullable
    public static EventMapSyncPacket current() {
        return current;
    }

    public static long revision() {
        return revision;
    }
}
