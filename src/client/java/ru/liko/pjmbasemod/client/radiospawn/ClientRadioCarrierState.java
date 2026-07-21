package ru.liko.pjmbasemod.client.radiospawn;

import java.util.List;

import ru.liko.pjmbasemod.common.network.packet.RadioCarrierSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RadioSpawnListPacket;

/** Клиентское зеркало носителей рации своей команды (в текущем измерении) — для карты. */
public final class ClientRadioCarrierState {

    private static volatile List<RadioSpawnListPacket.Entry> carriers = List.of();

    private ClientRadioCarrierState() {}

    public static List<RadioSpawnListPacket.Entry> carriers() {
        return carriers;
    }

    public static void update(RadioCarrierSyncPacket packet) {
        carriers = List.copyOf(packet.carriers());
    }

    public static void reset() {
        carriers = List.of();
    }
}
