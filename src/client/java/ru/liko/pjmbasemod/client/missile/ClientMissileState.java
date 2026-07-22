package ru.liko.pjmbasemod.client.missile;

import ru.liko.pjmbasemod.common.network.packet.MissileCatalogSyncPacket;

/** Последний серверный каталог и доступность ракетного удара для карты. */
public final class ClientMissileState {

    private static MissileCatalogSyncPacket state = empty();

    private ClientMissileState() {}

    public static MissileCatalogSyncPacket state() { return state; }

    public static void update(MissileCatalogSyncPacket packet) {
        state = packet == null ? empty() : packet;
    }

    public static void reset() { state = empty(); }

    private static MissileCatalogSyncPacket empty() {
        return new MissileCatalogSyncPacket(false, false, false, 0L, "", 0, java.util.List.of());
    }
}
