package ru.liko.pjmbasemod.client.serverevent;

import ru.liko.pjmbasemod.common.network.packet.SignalHuntHudPacket;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Клиентское зеркало индикации Радио-детектора: последний {@link SignalHuntHudPacket}.
 * Читается {@code SignalHuntHudOverlay} для отрисовки actionbar-HUD. Потокобезопасно
 * (пакеты приходят в netty-потоке, читается в render-потоке).
 */
public final class ClientSignalHuntState {

    private static final AtomicReference<SignalHuntHudPacket> CURRENT = new AtomicReference<>();
    private static long receivedAt = -1;

    private ClientSignalHuntState() {}

    public static void update(SignalHuntHudPacket packet) {
        CURRENT.set(packet);
        receivedAt = System.currentTimeMillis();
    }

    public static SignalHuntView current() {
        SignalHuntHudPacket p = CURRENT.get();
        return new SignalHuntView(p, receivedAt);
    }

    public record SignalHuntView(SignalHuntHudPacket packet, long receivedAt) {
        public boolean valid() { return packet != null; }
    }
}
