package ru.liko.pjmbasemod.client.role;

import ru.liko.pjmbasemod.common.network.packet.RoleAccessSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RoleSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.TargetRoleAccessPacket;
import ru.liko.pjmbasemod.common.role.CombatRole;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ClientRoleState {

    private static String currentRole = "";
    private static boolean canAssignRoles;
    private static Set<String> selfAssignableRoles = new HashSet<>();

    // Владение ролями выбранной командиром цели (для гашения недоступных в радиальном меню).
    @Nullable
    private static UUID targetAccessId;
    private static Set<String> targetAssignableRoles = new HashSet<>();

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

    public static void updateTargetAccess(TargetRoleAccessPacket packet) {
        targetAccessId = packet.targetId();
        targetAssignableRoles = packet.assignableRoles() == null
                ? new HashSet<>()
                : new HashSet<>(packet.assignableRoles());
    }

    /** Сбрасывает кэш владения цели перед запросом новой цели. */
    public static void clearTargetAccess() {
        targetAccessId = null;
        targetAssignableRoles = new HashSet<>();
    }

    public static void reset() {
        currentRole = "";
        canAssignRoles = false;
        selfAssignableRoles = new HashSet<>();
        clearTargetAccess();
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

    /**
     * Может ли командир назначить эту роль указанной цели. Пока ответ сервера по данной цели не получен —
     * оптимистично true (сервер всё равно валидирует), чтобы не гасить бесплатные роли в момент открытия меню.
     */
    public static boolean isTargetAssignable(@Nullable UUID targetId, CombatRole role) {
        if (role == null) return false;
        if (targetId == null || !targetId.equals(targetAccessId)) return true;
        return targetAssignableRoles.contains(role.id());
    }
}
