package ru.liko.pjmbasemod.common.garage;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Тип гаража: разделяет каталог техники на наземку и авиацию.
 * Каждый ноутбук-терминал привязан к одному типу и показывает только свою технику.
 */
public enum GarageType {
    GROUND("ground"),
    AVIATION("aviation");

    private final String id;

    GarageType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Ключ перевода названия типа гаража для GUI. */
    public String translationKey() {
        return "gui.pjmbasemod.garage.type." + id;
    }

    /**
     * Разбирает строковый id типа гаража. Принимает синонимы ("air", "plane" → авиация).
     * Пусто/неизвестно — {@link #GROUND} (наземка по умолчанию).
     */
    public static GarageType fromString(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return GROUND;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "aviation", "air", "plane", "heli", "helicopter", "jet", "aircraft" -> AVIATION;
            default -> GROUND;
        };
    }
}
