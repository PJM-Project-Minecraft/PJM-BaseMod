package ru.liko.pjmbasemod.common.warehouse;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Пул очков поставки склада. Каждый ящик начисляет очки в один из пулов, а каждый
 * выдаваемый предмет списывает очки из соответствующего пула.
 *
 * <p>Пулов два: {@link #SUPPLY} — всё снабжение (оружие, снаряжение, спец), пополняется
 * ящиками и пассивным доходом с захваченных точек; {@link #RAW} — сырьё, только ящики/сдача.</p>
 *
 * <p>Исторические пулы {@code weapon}/{@code equipment}/{@code special} схлопнуты в
 * {@link #SUPPLY} и продолжают распознаваться {@link #byId} как алиасы — старые
 * {@code items.json}/{@code crates/} читаются без правок.</p>
 */
public enum WarehousePoolCategory {
    SUPPLY,
    RAW;

    /** Ключ пула в нижнем регистре (для JSON, команд, NBT). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Ключ локализации для отображаемого имени пула. */
    public String translationKey() {
        return "gui.pjmbasemod.warehouse.pool." + id();
    }

    /** Разбор по id (включая устаревшие алиасы); null если не распознан. */
    @Nullable
    public static WarehousePoolCategory byId(@Nullable String raw) {
        if (raw == null) return null;
        String value = raw.trim().toUpperCase(Locale.ROOT);
        for (WarehousePoolCategory category : values()) {
            if (category.name().equals(value)) return category;
        }
        return switch (value) {
            case "WEAPON", "EQUIPMENT", "SPECIAL" -> SUPPLY;
            default -> null;
        };
    }

    /** Разбор по id с дефолтом. */
    public static WarehousePoolCategory byIdOrDefault(@Nullable String raw, WarehousePoolCategory fallback) {
        WarehousePoolCategory parsed = byId(raw);
        return parsed == null ? fallback : parsed;
    }
}
