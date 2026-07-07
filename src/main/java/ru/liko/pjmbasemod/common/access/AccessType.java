package ru.liko.pjmbasemod.common.access;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Донат-«Доступ» — постоянная именованная привилегия, независимая от боевой роли
 * ({@link ru.liko.pjmbasemod.common.role.CombatRole}). Выдаётся permission-нодой
 * {@code pjmbasemod.access.<id>} (донат-плагин / LuckPerms) и гейтит предметы склада
 * (см. {@link AccessPermissions}). Игрок может владеть любым числом Доступов одновременно
 * с одной боевой ролью.
 */
public enum AccessType {
    UAV("uav", "Доступ к БПЛА", 0x4CC4D8,
            "drone", "drone_operator", "bpla", "бпла", "оператор бпла", "uav_operator"),
    SSO("sso", "Доступ ССО", 0x8D4CD8,
            "sof", "special_forces", "ссо"),
    EW("ew", "Доступ РЭБ", 0xD84CA5,
            "reb", "рэб", "ew_specialist", "специалист рэб");

    private final String id;
    private final String displayName;
    private final int color;
    private final Set<String> aliases;

    AccessType(String id, String displayName, int color, String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.aliases = new LinkedHashSet<>();
        this.aliases.add(normalize(id));
        this.aliases.add(normalize(displayName));
        for (String alias : aliases) {
            this.aliases.add(normalize(alias));
        }
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int color() {
        return color;
    }

    public String translationKey() {
        return "access.pjmbasemod." + id;
    }

    @Nullable
    public static AccessType byIdOrAlias(@Nullable String raw) {
        String normalized = normalize(raw);
        if (normalized.isBlank()) return null;
        for (AccessType access : values()) {
            if (access.aliases.contains(normalized)) return access;
        }
        return null;
    }

    public static List<String> ids() {
        List<String> ids = new java.util.ArrayList<>(values().length);
        for (AccessType access : values()) ids.add(access.id);
        return List.copyOf(ids);
    }

    public static String normalize(@Nullable String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}
