package ru.liko.pjmbasemod.client.inventory;

import ru.liko.pjmbasemod.common.network.packet.LockedSlotsPacket;

import java.util.HashSet;
import java.util.Set;

/**
 * Клиентское состояние заблокированных слотов инвентаря. Заполняется из
 * {@link LockedSlotsPacket}; читается оверлеем и обработчиком кликов.
 */
public final class LockedSlotsClientState {

    private static volatile boolean enabled = false;
    private static volatile boolean cancelClicks = true;
    private static volatile Set<Integer> lockedSlots = new HashSet<>();

    private LockedSlotsClientState() {
    }

    public static void apply(LockedSlotsPacket packet) {
        enabled = packet.enabled();
        cancelClicks = packet.cancelClicks();
        lockedSlots = new HashSet<>(packet.lockedSlots());
    }

    public static void reset() {
        enabled = false;
        cancelClicks = true;
        lockedSlots = new HashSet<>();
    }

    public static boolean isActive() {
        return enabled && !lockedSlots.isEmpty();
    }

    public static boolean cancelClicks() {
        return cancelClicks;
    }

    /** {@code true}, если слот контейнера инвентаря игрока (индекс 0..40) заблокирован. */
    public static boolean isLocked(int containerSlot) {
        return enabled && lockedSlots.contains(containerSlot);
    }
}
