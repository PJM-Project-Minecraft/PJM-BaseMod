package ru.liko.pjmbasemod.common.inventory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Конфиг ограничения слотов инвентаря. Грузится из JSON
 * {@code config/pjmbasemod/inventory/slots.json} через {@link InventoryLimitRegistry}.
 *
 * <p>Нумерация слотов совпадает с контейнером {@code Player.getInventory()}:
 * {@code 0–8} — хотбар, {@code 9–35} — основной инвентарь, {@code 36–39} — броня,
 * {@code 40} — offhand. Блокировка реализована миксином в {@code Slot}, поэтому
 * любой слот контейнера инвентаря игрока можно заблокировать безопасно.</p>
 */
public final class InventoryLimitConfig {

    /** Минимальный валидный индекс слота инвентаря игрока. */
    public static final int MIN_SLOT = 0;
    /** Максимальный валидный индекс слота инвентаря игрока (offhand). */
    public static final int MAX_SLOT = 40;

    private boolean enabled = true;
    private List<Integer> lockedSlots = new ArrayList<>();
    private boolean dropExcessToGround = true;
    private int enforceEveryTicks = 5;
    private boolean cancelClicks = true;

    public InventoryLimitConfig() {
    }

    public static InventoryLimitConfig defaults() {
        InventoryLimitConfig config = new InventoryLimitConfig();
        // По умолчанию блокируем нижний-правый угол основного инвентаря.
        config.lockedSlots = new ArrayList<>(List.of(33, 34, 35));
        config.normalize();
        return config;
    }

    /** Отсев дублей и невалидных индексов (вне 0..40), сортировка; защита от битого конфига. */
    void normalize() {
        if (lockedSlots == null) lockedSlots = new ArrayList<>();
        LinkedHashSet<Integer> cleaned = new LinkedHashSet<>();
        for (Integer slot : lockedSlots) {
            if (slot == null) continue;
            if (slot < MIN_SLOT || slot > MAX_SLOT) continue;
            cleaned.add(slot);
        }
        List<Integer> sorted = new ArrayList<>(cleaned);
        sorted.sort(Integer::compareTo);
        lockedSlots = sorted;
        if (enforceEveryTicks < 1) enforceEveryTicks = 1;
    }

    public boolean enabled() {
        return enabled;
    }

    public List<Integer> lockedSlots() {
        return List.copyOf(lockedSlots);
    }

    public boolean dropExcessToGround() {
        return dropExcessToGround;
    }

    public int enforceEveryTicks() {
        return enforceEveryTicks;
    }

    public boolean cancelClicks() {
        return cancelClicks;
    }
}
