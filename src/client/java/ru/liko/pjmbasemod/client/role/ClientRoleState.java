package ru.liko.pjmbasemod.client.role;

import ru.liko.pjmbasemod.common.network.packet.RoleAccessSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RoleSyncPacket;
import ru.liko.pjmbasemod.common.role.CombatRole;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public final class ClientRoleState {

    private static String currentRole = "";
    private static boolean canAssignRoles;
    private static Set<String> selfAssignableRoles = new HashSet<>();

    private ClientRoleState() {
    }

    public static void update(RoleSyncPacket packet) {
        currentRole = packet.currentRole() == null ? "" : packet.currentRole();
        canAssignRoles = packet.canAssignRoles();
    }

    public static void updateAccess(RoleAccessSyncPacket packet) {
        selfAssignableRoles = packet.selfAssignableRoles() == null
                ? new HashSet<>()
                : new HashSet<>(packet.selfAssignableRoles());
    }

    public static void reset() {
        currentRole = "";
        canAssignRoles = false;
        selfAssignableRoles = new HashSet<>();
    }

    public static String currentRole() {
        return currentRole;
    }

    @Nullable
    public static CombatRole currentRoleEnum() {
        return CombatRole.byIdOrAlias(currentRole);
    }

    public static boolean canAssignRoles() {
        return canAssignRoles;
    }

    /** Может ли игрок назначить себе эту (донатную) роль — владеет ли он ею. */
    public static boolean isSelfAssignable(CombatRole role) {
        return role != null && selfAssignableRoles.contains(role.id());
    }

    public static boolean hasSelfAssignable() {
        return !selfAssignableRoles.isEmpty();
    }
}
