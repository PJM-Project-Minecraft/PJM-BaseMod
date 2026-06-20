package ru.liko.pjmbasemod.common.faction;

import java.util.Set;

/** Права заместителя фракции, упакованные в int-битмаску. */
public enum DeputyPermission {
    ASSIGN_ROLES(1),
    SET_ORDER(2),
    OPEN_GUI(4);

    private final int bit;

    DeputyPermission(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public static int pack(Set<DeputyPermission> perms) {
        int mask = 0;
        for (DeputyPermission p : perms) mask |= p.bit;
        return mask;
    }

    public static boolean has(int mask, DeputyPermission perm) {
        return (mask & perm.bit) != 0;
    }

    public static int all() {
        int mask = 0;
        for (DeputyPermission p : values()) mask |= p.bit;
        return mask;
    }

    /** Отбрасывает биты, не соответствующие ни одному праву. */
    public static int sanitize(int mask) {
        return mask & all();
    }
}
