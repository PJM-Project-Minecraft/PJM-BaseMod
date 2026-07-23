package ru.liko.pjmbasemod.client.missile;

import net.minecraft.Util;
import ru.liko.pjmbasemod.common.network.packet.MissileCatalogSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.MissileImpactPacket;

import java.util.ArrayList;
import java.util.List;

/** Последний серверный каталог, доступность ракетного удара и история поражений для карты. */
public final class ClientMissileState {

    /** Сколько отметка поражения живёт на карте. */
    public static final long IMPACT_TTL_MS = 15 * 60_000L;
    private static final int MAX_IMPACTS = 64;

    public record Impact(String dimension, double x, double z, float radius,
                         boolean shotDown, long timeMs) {}

    private static MissileCatalogSyncPacket state = empty();
    private static final List<Impact> IMPACTS = new ArrayList<>();

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

    /** Живые отметки (протухшие отсеиваются на месте). */
    public static List<Impact> impacts() {
        long now = Util.getMillis();
        IMPACTS.removeIf(impact -> now - impact.timeMs() > IMPACT_TTL_MS);
        return IMPACTS;
    }

    public static void reset() {
        state = empty();
        IMPACTS.clear();
    }

    private static MissileCatalogSyncPacket empty() {
        return new MissileCatalogSyncPacket(false, false, false, 0L, "", 0, java.util.List.of());
    }
}
