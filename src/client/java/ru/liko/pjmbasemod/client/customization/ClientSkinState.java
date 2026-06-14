package ru.liko.pjmbasemod.client.customization;

import ru.liko.pjmbasemod.common.network.packet.SkinSelectionSyncPacket;

import java.util.List;

/** Клиентское зеркало: пул разрешённых скинов и текущий выбор локального игрока (для меню). */
public final class ClientSkinState {

    private static List<String> allowed = List.of();
    private static String current = "";

    private ClientSkinState() {
    }

    public static void update(SkinSelectionSyncPacket packet) {
        allowed = List.copyOf(packet.allowedSkins());
        current = packet.currentSkin() == null ? "" : packet.currentSkin();
    }

    public static List<String> allowed() {
        return allowed;
    }

    public static String current() {
        return current;
    }
}
