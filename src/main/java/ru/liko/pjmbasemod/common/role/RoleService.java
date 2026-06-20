package ru.liko.pjmbasemod.common.role;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.common.faction.FactionCommanderService;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.RoleAccessSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RoleSyncPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RoleService {

    public static final double RADIAL_ASSIGN_DISTANCE = 12.0D;

    private RoleService() {
    }

    @Nullable
    public static CombatRole currentRole(ServerPlayer player) {
        if (player == null || player.getServer() == null) return null;
        RoleSavedData.RoleEntry entry = RoleSavedData.get(player.getServer()).entry(player.getUUID());
        if (entry == null) return null;
        String currentTeam = FrontlineTeams.resolvePlayerTeamId(player);
        if (!entry.teamId().equals(currentTeam)) return null;
        return CombatRole.byIdOrAlias(entry.roleId());
    }

    public static String currentRoleId(ServerPlayer player) {
        CombatRole role = currentRole(player);
        return role == null ? "" : role.id();
    }

    public static boolean hasAllowedRole(ServerPlayer player, List<String> allowedRoles) {
        if (allowedRoles == null || allowedRoles.isEmpty()) return true;
        CombatRole role = currentRole(player);
        return role != null && allowedRoles.contains(role.id());
    }

    public static Component requiredRoleMessage(List<String> allowedRoles) {
        return Component.translatable("gui.pjmbasemod.role.required", CombatRole.displayNamesFor(allowedRoles));
    }

    public static boolean canAssignAny(ServerPlayer actor) {
        return RolePermissions.can(actor, RolePermissions.ADMIN)
                || FactionCommanderService.isActiveCommander(actor);
    }

    public static boolean canAssign(@Nullable ServerPlayer actor, ServerPlayer target) {
        if (actor == null) return true;
        if (RolePermissions.can(actor, RolePermissions.ADMIN)) return true;
        String commanderTeam = FactionCommanderService.activeCommanderTeam(actor);
        if (commanderTeam == null) return false;
        String targetTeam = FrontlineTeams.resolvePlayerTeamId(target);
        return commanderTeam.equals(targetTeam);
    }

    public static AssignmentResult assignRole(@Nullable ServerPlayer actor, ServerPlayer target,
                                              @Nullable CombatRole role, boolean checkDistance) {
        if (target == null || target.getServer() == null) {
            return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.target_missing"));
        }

        if (actor != null && checkDistance && !isNearby(actor, target)) {
            return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.target_too_far"));
        }

        String targetTeam = FrontlineTeams.resolvePlayerTeamId(target);
        if (targetTeam == null || targetTeam.isBlank()) {
            return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.target_no_team"));
        }

        boolean selfAssignPaid = actor != null && actor == target && role != null
                && RoleAccessRegistry.get().isPaid(role)
                && RolePermissions.canUseRole(target, role);

        if (actor != null && !selfAssignPaid && !RolePermissions.can(actor, RolePermissions.ADMIN)) {
            String commanderTeam = FactionCommanderService.activeCommanderTeam(actor);
            if (commanderTeam == null) {
                return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.no_assign_permission"));
            }
            if (!commanderTeam.equals(targetTeam)) {
                return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.target_wrong_faction"));
            }
        }

        RoleSavedData data = RoleSavedData.get(target.getServer());
        if (role == null) {
            RoleSavedData.RoleEntry removed = data.clearRole(target.getUUID());
            sync(target);
            if (removed != null) {
                target.displayClientMessage(Component.translatable("gui.pjmbasemod.role.cleared_target"), true);
            }
            return AssignmentResult.success(Component.translatable("gui.pjmbasemod.role.cleared",
                    target.getName().getString()));
        }

        if (!RolePermissions.canUseRole(target, role)) {
            return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.not_unlocked",
                    Component.translatable(role.translationKey())));
        }

        AssignmentResult capResult = validateRoleCap(target.getServer(), target.getUUID(), targetTeam, role);
        if (!capResult.success()) {
            return capResult;
        }

        data.setRole(target.getUUID(), target.getName().getString(), targetTeam, role);
        sync(target);
        target.displayClientMessage(Component.translatable("gui.pjmbasemod.role.assigned_target",
                Component.translatable(role.translationKey())), true);
        return AssignmentResult.success(Component.translatable("gui.pjmbasemod.role.assigned",
                target.getName().getString(), Component.translatable(role.translationKey())));
    }

    public static AssignmentResult assignRoleById(ServerPlayer actor, UUID targetId, String roleId) {
        if (actor == null || actor.getServer() == null || targetId == null) {
            return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.target_missing"));
        }
        ServerPlayer target = actor.getServer().getPlayerList().getPlayer(targetId);
        if (target == null) {
            return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.target_missing"));
        }
        CombatRole role = roleId == null || roleId.isBlank() ? null : CombatRole.byIdOrAlias(roleId);
        if (role == null && roleId != null && !roleId.isBlank()) {
            return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.unknown", roleId));
        }
        return assignRole(actor, target, role, true);
    }

    public static void onPlayerLogin(ServerPlayer player) {
        cleanupInvalidFor(player);
        sync(player);
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        if (player.serverLevel().getGameTime() % 40L != 0L) return;
        if (cleanupInvalidFor(player)) {
            sync(player);
        }
    }

    public static boolean cleanupInvalidFor(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        RoleSavedData data = RoleSavedData.get(player.getServer());
        RoleSavedData.RoleEntry entry = data.entry(player.getUUID());
        if (entry == null) return false;
        String currentTeam = FrontlineTeams.resolvePlayerTeamId(player);
        if (entry.teamId().equals(currentTeam)) return false;
        data.clearRole(player.getUUID());
        player.displayClientMessage(Component.translatable("gui.pjmbasemod.role.cleared_team_changed"), true);
        return true;
    }

    public static void sync(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        PjmNetworking.sendToPlayer(player, new RoleSyncPacket(player.getUUID(),
                currentRoleId(player), canAssignAny(player)));
        PjmNetworking.sendToPlayer(player, new RoleAccessSyncPacket(selfAssignableRoleIds(player)));
    }

    /** id донат-ролей, которыми игрок владеет (может назначить себе сам). */
    private static List<String> selfAssignableRoleIds(ServerPlayer player) {
        List<String> ids = new ArrayList<>();
        for (CombatRole role : CombatRole.values()) {
            if (RoleAccessRegistry.get().isPaid(role) && RolePermissions.canUseRole(player, role)) {
                ids.add(role.id());
            }
        }
        return ids;
    }

    /**
     * id ролей, которые можно назначить указанной цели: бесплатные (всегда) + платные, которыми цель владеет.
     * Зеркалит серверный гейт {@link RolePermissions#canUseRole} в {@link #assignRole}, чтобы клиент-командир
     * мог погасить недоступные роли в меню.
     */
    public static List<String> assignableRoleIdsFor(ServerPlayer target) {
        List<String> ids = new ArrayList<>();
        if (target == null) return ids;
        for (CombatRole role : CombatRole.values()) {
            if (RolePermissions.canUseRole(target, role)) {
                ids.add(role.id());
            }
        }
        return ids;
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    public static AssignmentResult validateRoleCap(MinecraftServer server, UUID targetId,
                                                   String teamId, CombatRole role) {
        if (server == null || role == null) {
            return AssignmentResult.success(Component.empty());
        }
        int limit = RoleLimitRegistry.get().limitFor(teamId, role);
        if (limit < 0) {
            return AssignmentResult.success(Component.empty());
        }

        String teamName = FrontlineTeams.displayName(server, teamId);
        Component roleName = Component.translatable(role.translationKey());
        if (limit == 0) {
            return AssignmentResult.failure(Component.translatable(
                    "gui.pjmbasemod.role.limit_disabled", roleName, teamName));
        }

        int current = onlineRoleCount(server, teamId, role, targetId);
        if (current >= limit) {
            return AssignmentResult.failure(Component.translatable(
                    "gui.pjmbasemod.role.limit_full", roleName, teamName, current, limit));
        }
        return AssignmentResult.success(Component.empty());
    }

    public static int onlineRoleCount(MinecraftServer server, String teamId, CombatRole role) {
        return onlineRoleCount(server, teamId, role, null);
    }

    public static int onlineRoleCount(MinecraftServer server, String teamId, CombatRole role,
                                      @Nullable UUID excludedPlayerId) {
        if (server == null || role == null) return 0;
        String team = FrontlineTeams.normalize(teamId);
        if (team.isBlank()) return 0;
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (excludedPlayerId != null && excludedPlayerId.equals(player.getUUID())) continue;
            if (!team.equals(FrontlineTeams.resolvePlayerTeamId(player))) continue;
            CombatRole current = currentRole(player);
            if (current == role) count++;
        }
        return count;
    }

    private static boolean isNearby(ServerPlayer actor, ServerPlayer target) {
        if (actor == target) return true;
        if (actor.serverLevel() != target.serverLevel()) return false;
        return actor.distanceToSqr(target) <= RADIAL_ASSIGN_DISTANCE * RADIAL_ASSIGN_DISTANCE;
    }

    public record AssignmentResult(boolean success, Component message) {
        public static AssignmentResult success(Component message) {
            return new AssignmentResult(true, message);
        }

        public static AssignmentResult failure(Component message) {
            return new AssignmentResult(false, message);
        }
    }
}
