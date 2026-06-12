package ru.liko.pjmbasemod.client.faction;

import ru.liko.pjmbasemod.common.network.packet.FactionCommanderSyncPacket;

public final class ClientFactionCommanderState {

    private static State state = State.empty();

    private ClientFactionCommanderState() {
    }

    public static void update(FactionCommanderSyncPacket packet) {
        state = new State(
                packet.active(),
                packet.teamId(),
                packet.teamName(),
                packet.teamColor(),
                packet.roleShortName(),
                packet.roleDisplayName()
        );
    }

    public static void reset() {
        state = State.empty();
    }

    public static State state() {
        return state;
    }

    public record State(
            boolean active,
            String teamId,
            String teamName,
            int teamColor,
            String roleShortName,
            String roleDisplayName
    ) {
        public static State empty() {
            return new State(false, "", "", 0xF0B43A, "КМД", "КОМАНДИР ФРАКЦИИ");
        }
    }
}
