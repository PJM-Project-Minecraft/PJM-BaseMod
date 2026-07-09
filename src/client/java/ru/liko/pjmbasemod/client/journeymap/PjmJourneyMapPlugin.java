package ru.liko.pjmbasemod.client.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.capturepoint.journeymap.CapturePointJourneyMapRuntime;
import ru.liko.pjmbasemod.client.serverevent.journeymap.EventJourneyMapRuntime;

/**
 * Единая точка входа JourneyMap для мода (один @JourneyMapPlugin на modid).
 * Инициализирует все JM-runtime: точки захвата + серверные события.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class PjmJourneyMapPlugin implements IClientPlugin {

    @Override
    public void initialize(IClientAPI jmClientApi) {
        CapturePointJourneyMapRuntime.initialize(jmClientApi);
        EventJourneyMapRuntime.initialize(jmClientApi);
        Pjmbasemod.LOGGER.info("[PJM][JourneyMap] Client plugin initialized (capturepoints + events).");
    }

    @Override
    public String getModId() {
        return Pjmbasemod.MODID;
    }
}
