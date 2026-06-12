package ru.liko.pjmbasemod.client.role;

import ru.liko.pjmbasemod.common.network.packet.RoleSyncPacket;
import ru.liko.pjmbasemod.common.role.CombatRole;

import javax.annotation.Nullable;

public final class ClientRoleState {

    private static String currentRole = "";
    private static boolean canAssignRoles;

    private ClientRoleState() {
    }

    public static void update(RoleSyncPacket packet) {
        currentRole = packet.currentRole() == null ? "" : packet.currentRole();
        canAssignRoles = packet.canAssignRoles();
    }

    public static void reset() {
        currentRole = "";
        canAssignRoles = false;
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
}
