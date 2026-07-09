package ru.liko.pjmbasemod.client.serverevent.journeymap;

/**
 * Развязка клиентского кода от классов JourneyMap: без мода adapter остаётся null
 * и вызовы — no-op (паттерн bridge/runtime, как у будущих подсистем карты).
 */
public final class EventJourneyMapBridge {

    private static volatile Adapter adapter;

    private EventJourneyMapBridge() {}

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
