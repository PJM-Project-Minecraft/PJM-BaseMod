package ru.liko.pjmbasemod.client.frontline.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import ru.liko.pjmbasemod.Pjmbasemod;

@JourneyMapPlugin(apiVersion = "2.0.0")
public final class FrontlineJourneyMapPlugin implements IClientPlugin {

    @Override
    public void initialize(IClientAPI jmClientApi) {
        FrontlineJourneyMapRuntime.initialize(jmClientApi);
        Pjmbasemod.LOGGER.info("[FRONTLINE][JourneyMap] Client plugin initialized.");
    }

    @Override
    public String getModId() {
        return Pjmbasemod.MODID;
    }
}
