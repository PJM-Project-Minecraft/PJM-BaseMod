package ru.liko.pjmbasemod.common.rank;

import ru.liko.pjmbasemod.Pjmbasemod;

import javax.annotation.Nullable;
import java.util.Locale;

public final class RankDefinition {

    private String id;
    private String displayName;
    private String shortName;
    private int minXp;
    private String icon;
    private String accentColor;
    /** Потолок личной квоты склада для ранга. {@code null} (не задано) или {@code -1} → безлимит. */
    private Integer warehouseBudgetMax;
    /** Регенерация квоты, очков/час. {@code null} (не задано) → равна потолку {@link #warehouseBudgetMax}. */
    private Integer warehouseBudgetRegenPerHour;

    public RankDefinition() {
    }

    public RankDefinition(String id, String displayName, String shortName, int minXp, String icon, String accentColor) {
        this.id = id;
        this.displayName = displayName;
        this.shortName = shortName;
        this.minXp = minXp;
        this.icon = icon;
        this.accentColor = accentColor;
    }

    public String id() {
        return id == null ? "" : id;
    }

    public String displayName() {
        return displayName == null || displayName.isBlank() ? id() : displayName;
    }

    public String shortName() {
        return shortName == null || shortName.isBlank() ? id().toUpperCase(Locale.ROOT) : shortName;
    }

    public int minXp() {
        return Math.max(0, minXp);
    }

    public String icon() {
        String value = icon == null || icon.isBlank() ? "textures/rangs/private.png" : icon.trim();
        return value.contains(":") ? value : Pjmbasemod.MODID + ":" + value;
    }

    public int accentColorRgb() {
        return parseColor(accentColor, 0xD8B15F);
    }

    /** Потолок личной квоты склада. {@code null}/{@code -1} → безлимит (см. {@code WarehouseBudgetLimits}). */
    @Nullable
    public Integer warehouseBudgetMax() { return warehouseBudgetMax; }

    /** Регенерация квоты, очков/час. {@code null} → равна потолку. */
    @Nullable
    public Integer warehouseBudgetRegenPerHour() { return warehouseBudgetRegenPerHour; }

    /** Флюент-сеттер квоты склада (для дефолтного конфига рангов). */
    public RankDefinition budget(@Nullable Integer max, @Nullable Integer regenPerHour) {
        this.warehouseBudgetMax = max;
        this.warehouseBudgetRegenPerHour = regenPerHour;
        return this;
    }

    void normalize() {
        id = sanitizeId(id);
        if (id.isBlank()) id = "private";
        if (displayName == null || displayName.isBlank()) displayName = id;
        if (shortName == null || shortName.isBlank()) shortName = id.toUpperCase(Locale.ROOT);
        minXp = Math.max(0, minXp);
        if (icon == null || icon.isBlank()) icon = "textures/rangs/" + id + ".png";
        if (accentColor == null || accentColor.isBlank()) accentColor = "#d8b15f";
        // Квота: отрицательный потолок нормализуем к -1 (безлимит); зажимаем в допустимый диапазон.
        if (warehouseBudgetMax != null) {
            warehouseBudgetMax = warehouseBudgetMax < 0 ? -1 : Math.min(warehouseBudgetMax, 1_000_000);
        }
        if (warehouseBudgetRegenPerHour != null) {
            warehouseBudgetRegenPerHour = Math.max(0, Math.min(warehouseBudgetRegenPerHour, 1_000_000));
        }
    }

    static String sanitizeId(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private static int parseColor(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("#")) value = value.substring(1);
        if (value.startsWith("0x")) value = value.substring(2);
        try {
            return Integer.parseUnsignedInt(value, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            try {
                return Integer.parseInt(raw.trim()) & 0xFFFFFF;
            } catch (NumberFormatException ignoredAgain) {
                return fallback;
            }
        }
    }
}
