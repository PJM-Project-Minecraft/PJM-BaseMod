package ru.liko.pjmbasemod.client.basezone;

import java.util.List;

import ru.liko.pjmbasemod.common.basezone.BaseZoneView;
import ru.liko.pjmbasemod.common.network.packet.BaseZoneMapSyncPacket;

/** Клиентское зеркало зон базы (для карты). Обновляется из BaseZoneMapSyncPacket. */
public final class ClientBaseZoneState {

    private static volatile List<BaseZoneView> zones = List.of();

    private ClientBaseZoneState() {}

    public static List<BaseZoneView> zones() {
        return zones;
    }

    public static void update(BaseZoneMapSyncPacket packet) {
        zones = List.copyOf(packet.zones());
    }

    public static void reset() {
        zones = List.of();
    }
}
