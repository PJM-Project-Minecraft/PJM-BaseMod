package ru.liko.pjmbasemod.client.faction;

import ru.liko.pjmbasemod.common.network.packet.FactionOrderSyncPacket;

import javax.annotation.Nullable;

/** Клиентское зеркало текущего приказа фракции. */
public final class ClientFactionOrderState {

    private static volatile boolean active;
    private static volatile State state = State.empty();
    private static volatile long receivedAtMs;

    private ClientFactionOrderState() {
    }

    public static void update(FactionOrderSyncPacket packet) {
        active = packet.active();
        state = new State(packet.text(), packet.author(), packet.teamColor(), packet.secondsRemaining());
        receivedAtMs = System.currentTimeMillis();
    }

    public static void reset() {
        active = false;
        state = State.empty();
        receivedAtMs = 0L;
    }

    /** Текущий приказ или null, если его нет/истёк локально. */
    @Nullable
    public static State current() {
        if (!active) return null;
        if (state.secondsRemaining() >= 0 && remainingSeconds() <= 0) return null;
        return state;
    }

    /** Остаток в секундах: -1 = бессрочно, 0 = истёк. */
    public static int remainingSeconds() {
        State s = state;
        if (s.secondsRemaining() < 0) return -1;
        long elapsed = (System.currentTimeMillis() - receivedAtMs) / 1000L;
        return (int) Math.max(0, s.secondsRemaining() - elapsed);
    }

    public record State(String text, String author, int color, int secondsRemaining) {
        public static State empty() {
            return new State("", "", 0xFFFFFF, 0);
        }
    }
}
