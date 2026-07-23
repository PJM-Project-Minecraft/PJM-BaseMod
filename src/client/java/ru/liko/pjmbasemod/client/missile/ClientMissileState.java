package ru.liko.pjmbasemod.client.missile;

import net.minecraft.Util;
import ru.liko.pjmbasemod.common.network.packet.MissileAlertPacket;
import ru.liko.pjmbasemod.common.network.packet.MissileCatalogSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.MissileImpactPacket;

import java.util.ArrayList;
import java.util.List;

/** Последний серверный каталог, доступность ракетного удара и история поражений для карты. */
public final class ClientMissileState {

    /** Сколько отметка поражения живёт на карте. */
    public static final long IMPACT_TTL_MS = 15 * 60_000L;
    /** Сколько висит зона предупреждения о летящей ракете. */
    public static final long ALERT_TTL_MS = 25_000L;
    private static final int MAX_IMPACTS = 64;

    public record Impact(String dimension, double x, double z, float radius,
                         boolean shotDown, long timeMs) {}

    public record StrikeAlert(String dimension, double x, double z, float radius,
                              String missileName, boolean ownTeam, long timeMs) {}

    private static MissileCatalogSyncPacket state = empty();
    private static final List<Impact> IMPACTS = new ArrayList<>();
    private static final List<StrikeAlert> ALERTS = new ArrayList<>();

    private ClientMissileState() {}

    public static MissileCatalogSyncPacket state() { return state; }

    public static void update(MissileCatalogSyncPacket packet) {
        state = packet == null ? empty() : packet;
    }

    public static void addImpact(MissileImpactPacket packet) {
        if (packet == null) return;
        IMPACTS.add(new Impact(packet.dimension(), packet.x(), packet.z(),
                packet.radius(), packet.shotDown(), Util.getMillis()));
        if (IMPACTS.size() > MAX_IMPACTS) IMPACTS.remove(0);
    }

    public static void addAlert(MissileAlertPacket packet) {
        if (packet == null) return;
        ALERTS.add(new StrikeAlert(packet.dimension(), packet.x(), packet.z(),
                packet.radius(), packet.missileName(), packet.ownTeam(), Util.getMillis()));
    }

    /** Живые зоны предупреждения (протухшие отсеиваются на месте). */
    public static List<StrikeAlert> alerts() {
        long now = Util.getMillis();
        ALERTS.removeIf(alert -> now - alert.timeMs() > ALERT_TTL_MS);
        return ALERTS;
    }

    /** Живые отметки (протухшие отсеиваются на месте). */
    public static List<Impact> impacts() {
        long now = Util.getMillis();
        IMPACTS.removeIf(impact -> now - impact.timeMs() > IMPACT_TTL_MS);
        return IMPACTS;
    }

    public static void reset() {
        state = empty();
        IMPACTS.clear();
        ALERTS.clear();
    }

    private static MissileCatalogSyncPacket empty() {
        return new MissileCatalogSyncPacket(false, false, false, "", 0, java.util.List.of());
    }
}
