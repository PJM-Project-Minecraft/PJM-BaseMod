package ru.liko.pjmbasemod.common.serverevent;

import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.common.init.PjmItems;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.SignalHuntHudPacket;

/**
 * Серверная логика индикации Радио-детектора: раз в N тиков для каждого игрока с
 * детектором в руке считает сигнал ближайшего маяка активной радиоразведки и шлёт
 * {@link SignalHuntHudPacket}. Вызывается из {@code PjmServerEvents.onPlayerTick}.
 */
public final class SignalHuntService {

    /** Интервал обновления HUD (тики). 5 тиков = 4 раза/сек — баланс плавности и трафика. */
    private static final int UPDATE_INTERVAL = 5;

    private SignalHuntService() {}

    public static void onPlayerTick(ServerPlayer player) {
        if (player.tickCount % UPDATE_INTERVAL != 0) return;
        if (!isHoldingDetector(player)) return;

        ServerEvent active = ServerEventManager.activeEvent();
        SignalHuntHudPacket packet;
        if (active instanceof SignalHuntEvent hunt) {
            packet = hunt.hudFor(player);
            if (packet == null) packet = SignalHuntHudPacket.inactive();
        } else {
            packet = SignalHuntHudPacket.inactive();
        }
        PjmNetworking.sendToPlayer(player, packet);
    }

    private static boolean isHoldingDetector(ServerPlayer player) {
        return player.getMainHandItem().is(PjmItems.RADIO_DETECTOR.get())
                || player.getOffhandItem().is(PjmItems.RADIO_DETECTOR.get());
    }
}
