package ru.liko.pjmbasemod.client.rank;

import ru.liko.pjmbasemod.common.network.packet.RankSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RankXpPacket;

public final class ClientRankState {

    private static State state = State.empty();

    private ClientRankState() {
    }

    public static void update(RankSyncPacket packet) {
        state = new State(
                packet.xp(),
                packet.rankId(),
                packet.displayName(),
                packet.shortName(),
                packet.icon(),
                packet.accentColor(),
                packet.minXp(),
                packet.nextDisplayName(),
                packet.nextMinXp(),
                packet.enabled(),
                packet.showRankHud(),
                packet.showXpPopups(),
                packet.showTabPrefix()
        );
    }

    public static void update(RankXpPacket packet) {
        state = new State(
                packet.xp(),
                packet.rankId(),
                packet.displayName(),
                packet.shortName(),
                packet.icon(),
                packet.accentColor(),
                packet.minXp(),
                packet.nextDisplayName(),
                packet.nextMinXp(),
                packet.enabled(),
                packet.showRankHud(),
                packet.showXpPopups(),
                packet.showTabPrefix()
        );
    }

    public static State state() {
        return state;
    }

    public record State(
            int xp,
            String rankId,
            String displayName,
            String shortName,
            String icon,
            int accentColor,
            int minXp,
            String nextDisplayName,
            int nextMinXp,
            boolean enabled,
            boolean showRankHud,
            boolean showXpPopups,
            boolean showTabPrefix
    ) {
        public static State empty() {
            return new State(0, "private", "Рядовой", "PVT", "pjmbasemod:textures/rangs/private.png",
                    0xD8B15F, 0, "", -1, false, false, false, false);
        }
    }
}
