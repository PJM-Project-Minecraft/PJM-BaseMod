package ru.liko.pjmbasemod.client.capturepoint.journeymap;

/**
 * Развязка клиентского кода от классов JourneyMap: без мода adapter остаётся null
 * и вызовы — no-op. Паттерн bridge/runtime.
 */
public final class CapturePointJourneyMapBridge {

    private static volatile Adapter adapter;

    private CapturePointJourneyMapBridge() {}

    static void attach(Adapter next) {
        adapter = next;
    }

    public static void onClientTick() {
        Adapter current = adapter;
        if (current != null) current.onClientTick();
    }

    public static void onLogout() {
        Adapter current = adapter;
        if (current != null) current.onLogout();
    }

    interface Adapter {
        void onClientTick();
        void onLogout();
    }
}
