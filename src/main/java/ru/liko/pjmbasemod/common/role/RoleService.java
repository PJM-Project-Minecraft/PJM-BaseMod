package ru.liko.pjmbasemod.common.role;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.common.faction.DeputyPermission;
import ru.liko.pjmbasemod.common.faction.FactionCommanderService;
import ru.liko.pjmbasemod.common.faction.FactionDeputySavedData;
import ru.liko.pjmbasemod.common.teams.Teams;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.RoleSyncPacket;

import javax.annotation.Nullable;
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
        String currentTeam = Teams.resolvePlayerTeamId(player);
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
                || FactionCommanderService.isActiveCommander(actor)
                || isRoleDeputy(actor, Teams.resolvePlayerTeamId(actor));
    }

    public static boolean canAssign(@Nullable ServerPlayer actor, ServerPlayer target) {
        if (actor == null) return true;
        if (RolePermissions.can(actor, RolePermissions.ADMIN)) return true;
        String targetTeam = Teams.resolvePlayerTeamId(target);
        String commanderTeam = FactionCommanderService.activeCommanderTeam(actor);
        if (commanderTeam != null && commanderTeam.equals(targetTeam)) return true;
        return isRoleDeputy(actor, targetTeam);
    }

    /** Заместитель фракции с правом {@link DeputyPermission#ASSIGN_ROLES} на указанной команде. */
    public static boolean isRoleDeputy(@Nullable ServerPlayer actor, @Nullable String teamId) {
        if (actor == null || actor.getServer() == null || teamId == null || teamId.isBlank()) return false;
        int perms = FactionDeputySavedData.get(actor.getServer()).permissions(teamId, actor.getUUID());
        return DeputyPermission.has(perms, DeputyPermission.ASSIGN_ROLES);
    }

    public static AssignmentResult assignRole(@Nullable ServerPlayer actor, ServerPlayer target,
                                              @Nullable CombatRole role, boolean checkDistance) {
        if (target == null || target.getServer() == null) {
            return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.target_missing"));
        }

        if (actor != null && checkDistance && !isNearby(actor, target)) {
            return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.target_too_far"));
        }

        String targetTeam = Teams.resolvePlayerTeamId(target);
        if (targetTeam == null || targetTeam.isBlank()) {
            return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.target_no_team"));
        }

        // Боевую роль назначает только ADMIN | командир целевой фракции | заместитель с ASSIGN_ROLES.
        // Само-выдача роли игроком удалена вместе с донат-ролями (донат теперь — «Доступ», см. common/access).
        if (actor != null && !RolePermissions.can(actor, RolePermissions.ADMIN)) {
            String commanderTeam = FactionCommanderService.activeCommanderTeam(actor);
            boolean commanderOfTarget = commanderTeam != null && commanderTeam.equals(targetTeam);
            // Заместитель с правом ASSIGN_ROLES выдаёт роли своей фракции наравне с командиром.
            boolean deputyOfTarget = isRoleDeputy(actor, targetTeam);
            if (!commanderOfTarget && !deputyOfTarget) {
                // Командир чужой фракции — уточняем причину; иначе прав на выдачу нет вовсе.
                if (commanderTeam != null) {
                    return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.target_wrong_faction"));
                }
                return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.no_assign_permission"));
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
        String currentTeam = Teams.resolvePlayerTeamId(player);
        if (entry.teamId().equals(currentTeam)) return false;
        data.clearRole(player.getUUID());
        player.displayClientMessage(Component.translatable("gui.pjmbasemod.role.cleared_team_changed"), true);
        return true;
    }

    public static void sync(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        PjmNetworking.sendToPlayer(player, new RoleSyncPacket(player.getUUID(),
                currentRoleId(player), canAssignAny(player)));
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

        String teamName = Teams.displayName(server, teamId);
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
        String team = Teams.normalize(teamId);
        if (team.isBlank()) return 0;
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (excludedPlayerId != null && excludedPlayerId.equals(player.getUUID())) continue;
            if (!team.equals(Teams.resolvePlayerTeamId(player))) continue;
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
