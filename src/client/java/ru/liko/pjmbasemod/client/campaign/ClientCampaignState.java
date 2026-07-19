package ru.liko.pjmbasemod.client.campaign;

import ru.liko.pjmbasemod.common.network.packet.CampaignSyncPacket;

import javax.annotation.Nullable;
import java.util.List;

/** Клиентское зеркало счёта кампании. Остаток до вайпа досчитывается локально от момента получения. */
public final class ClientCampaignState {

    private static volatile CampaignSyncPacket state = CampaignSyncPacket.empty();
    private static volatile long receivedAtMs;

    private ClientCampaignState() {}

    public static void update(CampaignSyncPacket packet) {
        state = packet;
        receivedAtMs = System.currentTimeMillis();
    }

    public static void reset() {
        state = CampaignSyncPacket.empty();
        receivedAtMs = 0L;
    }

    /** Текущий счёт или null, если кампания неактивна. */
    @Nullable
    public static List<CampaignSyncPacket.TeamScore> scores() {
        return state.active() ? state.scores() : null;
    }

    public static long secondsRemaining() {
        long elapsed = (System.currentTimeMillis() - receivedAtMs) / 1000L;
        return Math.max(0, state.secondsToEnd() - elapsed);
    }
}
