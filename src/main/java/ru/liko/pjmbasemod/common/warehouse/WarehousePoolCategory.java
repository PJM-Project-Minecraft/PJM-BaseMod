package ru.liko.pjmbasemod.common.warehouse;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Пул очков поставки склада. Каждый ящик начисляет очки в один из пулов, а каждый
 * выдаваемый предмет списывает очки из соответствующего пула.
 */
public enum WarehousePoolCategory {
    WEAPON,
    SUPPLY,
    EQUIPMENT,
    RAW,
    SPECIAL;

    /** Ключ пула в нижнем регистре (для JSON, команд, NBT). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Ключ локализации для отображаемого имени пула. */
    public String translationKey() {
        return "gui.pjmbasemod.warehouse.pool." + id();
    }

    /** Разбор по id; null если не распознан. */
    @Nullable
    public static WarehousePoolCategory byId(@Nullable String raw) {
        if (raw == null) return null;
        String value = raw.trim().toUpperCase(Locale.ROOT);
        for (WarehousePoolCategory category : values()) {
            if (category.name().equals(value)) return category;
        }
        return null;
    }

    /** Разбор по id с дефолтом. */
    public static WarehousePoolCategory byIdOrDefault(@Nullable String raw, WarehousePoolCategory fallback) {
        WarehousePoolCategory parsed = byId(raw);
        return parsed == null ? fallback : parsed;
    }
}
