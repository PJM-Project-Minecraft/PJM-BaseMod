package ru.liko.pjmbasemod.common.role;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum CombatRole {
    ASSAULT("assault", "Штурмовик", 0xD84C3E,
            "stormtrooper", "shturmovik", "штурмовик"),
    MACHINE_GUNNER("machine_gunner", "Пулеметчик", 0xD88B3E,
            "machinegunner", "mg", "pulemetchik", "пулеметчик", "пулемётчик"),
    SNIPER("sniper", "Снайпер", 0x4C7FD8,
            "снайпер"),
    MARKSMAN("marksman", "Марксман", 0x4CD87A,
            "dmr", "марксман"),
    EW_SPECIALIST("ew_specialist", "Специалист РЭБ", 0xD84CA5,
            "ew", "reb", "рэб", "специалист_рэб", "специалист рэб"),
    CREW("crew", "Экипаж", 0xD8C34C,
            "vehicle_crew", "экипаж");

    private final String id;
    private final String displayName;
    private final int color;
    private final Set<String> aliases;

    CombatRole(String id, String displayName, int color, String... aliases) {
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
        return "role.pjmbasemod." + id;
    }

    public boolean matches(String raw) {
        return aliases.contains(normalize(raw));
    }

    @Nullable
    public static CombatRole byIdOrAlias(@Nullable String raw) {
        String normalized = normalize(raw);
        if (normalized.isBlank()) return null;
        for (CombatRole role : values()) {
            if (role.aliases.contains(normalized)) return role;
        }
        return null;
    }

    public static List<String> normalizeList(@Nullable List<String> rawRoles) {
        if (rawRoles == null || rawRoles.isEmpty()) return List.of();
        Set<String> ids = new LinkedHashSet<>();
        for (String raw : rawRoles) {
            CombatRole role = byIdOrAlias(raw);
            if (role != null) ids.add(role.id());
        }
        return List.copyOf(ids);
    }

    public static String displayNameFor(@Nullable String roleId) {
        CombatRole role = byIdOrAlias(roleId);
        return role == null ? "" : role.displayName();
    }

    public static String displayNamesFor(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (String roleId : roleIds) {
            String name = displayNameFor(roleId);
            if (!name.isBlank()) names.add(name);
        }
        return String.join(", ", names);
    }

    public static String normalize(@Nullable String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}
