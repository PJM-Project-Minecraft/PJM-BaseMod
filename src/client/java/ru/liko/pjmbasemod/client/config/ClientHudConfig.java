package ru.liko.pjmbasemod.client.config;

import ru.liko.pjmbasemod.common.network.packet.HudConfigPacket;

/** Клиентское зеркало серверных HUD-флагов (скрытие полосок голода/брони). */
public final class ClientHudConfig {

    private static volatile boolean disableHunger;
    private static volatile boolean hideArmorBar;

    private ClientHudConfig() {
    }

    public static void update(HudConfigPacket packet) {
        disableHunger = packet.disableHunger();
        hideArmorBar = packet.hideArmorBar();
    }

    public static boolean disableHunger() {
        return disableHunger;
    }

    public static boolean hideArmorBar() {
        return hideArmorBar;
    }
}
