package ru.liko.pjmbasemod.client.frontline.journeymap;

import ru.liko.pjmbasemod.Config;

public final class FrontlineJourneyMapBridge {

    private static volatile Adapter adapter;

    private FrontlineJourneyMapBridge() {}

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

    public static StatusSnapshot status() {
        Adapter current = adapter;
        if (current == null) return StatusSnapshot.unavailable();
        return current.status();
    }

    interface Adapter {
        void onClientTick();

        void onLogout();

        StatusSnapshot status();
    }

    public record StatusSnapshot(
            boolean enabledByConfig,
            boolean pluginInitialized,
            boolean mappingActive,
            boolean overlaysVisible,
            long lastAppliedRevision,
            int overlayCount,
            String lastError
    ) {
        public static StatusSnapshot unavailable() {
            return new StatusSnapshot(Config.isFrontlineJourneyMapEnabled(), false, false, false, -1L, 0, "unavailable");
        }
    }
}
