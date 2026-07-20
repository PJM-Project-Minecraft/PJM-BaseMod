package ru.liko.pjmbasemod.common.role;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.faction.DeputyPermission;
import ru.liko.pjmbasemod.common.faction.FactionCommanderService;
import ru.liko.pjmbasemod.common.faction.FactionDeputySavedData;
import ru.liko.pjmbasemod.common.teams.Teams;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.RoleSyncPacket;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
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

        return assignRoleTo(actor, target.getServer(), target.getUUID(), target.getName().getString(),
                targetTeam, role);
    }

    /**
     * Назначение роли по UUID — цель может быть оффлайн: роль живёт в {@link RoleSavedData},
     * а живой {@link ServerPlayer} нужен лишь для синхронизации и уведомления. Лимиты считаются
     * по сохранённым записям (см. {@link #roleCount}), поэтому для оффлайн-цели они те же.
     *
     * @param targetTeam команда цели (у оффлайн-игрока берётся из scoreboard по имени)
     */
    public static AssignmentResult assignRoleTo(@Nullable ServerPlayer actor, MinecraftServer server,
                                                UUID targetId, String targetName, String targetTeam,
                                                @Nullable CombatRole role) {
        if (server == null || targetId == null) {
            return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.target_missing"));
        }
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

        ServerPlayer online = server.getPlayerList().getPlayer(targetId);
        RoleSavedData data = RoleSavedData.get(server);
        if (role == null) {
            RoleSavedData.RoleEntry removed = data.clearRole(targetId);
            if (online != null) {
                sync(online);
                if (removed != null) {
                    online.displayClientMessage(Component.translatable("gui.pjmbasemod.role.cleared_target"), true);
                }
            }
            return AssignmentResult.success(Component.translatable("gui.pjmbasemod.role.cleared", targetName));
        }

        // OP/ADMIN выдаёт любую роль в обход лимитов команды; командир/зам — строго в пределах лимита.
        boolean adminBypass = actor != null && RolePermissions.can(actor, RolePermissions.ADMIN);
        if (!adminBypass) {
            AssignmentResult cooldown = validateChangeCooldown(server, targetId, targetTeam, role);
            if (!cooldown.success()) {
                return cooldown;
            }
            AssignmentResult capResult = validateRoleCap(server, targetId, targetTeam, role);
            if (!capResult.success()) {
                return capResult;
            }
        }

        data.setRole(targetId, targetName, targetTeam, role);
        if (online != null) {
            sync(online);
            online.displayClientMessage(Component.translatable("gui.pjmbasemod.role.assigned_target",
                    Component.translatable(role.translationKey())), true);
        }
        return AssignmentResult.success(Component.translatable("gui.pjmbasemod.role.assigned",
                targetName, Component.translatable(role.translationKey())));
    }

    /** Роль по сохранённой записи — для оффлайн-игрока, у которого не спросить живую команду. */
    public static String storedRoleId(MinecraftServer server, UUID playerId, String teamId) {
        if (server == null || playerId == null) return "";
        RoleSavedData.RoleEntry entry = RoleSavedData.get(server).entry(playerId);
        if (entry == null || !Teams.normalize(teamId).equals(entry.teamId())) return "";
        CombatRole role = CombatRole.byIdOrAlias(entry.roleId());
        return role == null ? "" : role.id();
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

    /**
     * Кулдаун смены боевой роли ({@code faction.roleChangeCooldownMinutes}): игрок с уже
     * выданной ролью не может получить другую, пока не пройдёт интервал. Первая выдача,
     * снятие роли и переназначение той же роли ограничением не считаются; OP обходит.
     */
    public static AssignmentResult validateChangeCooldown(MinecraftServer server, UUID targetId,
                                                          String teamId, CombatRole role) {
        int minutes = Config.getRoleChangeCooldownMinutes();
        if (server == null || role == null || minutes <= 0) {
            return AssignmentResult.success(Component.empty());
        }
        RoleSavedData.RoleEntry entry = RoleSavedData.get(server).entry(targetId);
        if (entry == null || !Teams.normalize(teamId).equals(entry.teamId())) {
            return AssignmentResult.success(Component.empty());
        }
        if (role.id().equals(entry.roleId())) {
            return AssignmentResult.success(Component.empty());
        }
        long elapsed = System.currentTimeMillis() - entry.changedAt();
        long cooldownMs = minutes * 60_000L;
        if (elapsed >= cooldownMs) {
            return AssignmentResult.success(Component.empty());
        }
        long leftMinutes = Math.max(1L, (cooldownMs - elapsed + 59_999L) / 60_000L);
        return AssignmentResult.failure(Component.translatable(
                "gui.pjmbasemod.role.change_cooldown", leftMinutes));
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

        int current = roleCount(server, teamId, role, targetId);
        if (current >= limit) {
            return AssignmentResult.failure(Component.translatable(
                    "gui.pjmbasemod.role.limit_full", roleName, teamName, current, limit));
        }
        return AssignmentResult.success(Component.empty());
    }

    public static int roleCount(MinecraftServer server, String teamId, CombatRole role) {
        return roleCount(server, teamId, role, null);
    }

    /**
     * Сколько игроков команды держат роль — по {@link RoleSavedData}, а НЕ по списку онлайна:
     * роль персистентна и переживает выход, поэтому оффлайн-игрок обязан занимать слот лимита.
     * Иначе двое вышедших снайперов освобождают места, на них выдаются новые, и после
     * возвращения первых лимит оказывается превышен вдвое.
     *
     * <p>Команда берётся из самой записи ({@code entry.teamId()}) — у оффлайн-игрока живую
     * команду не спросить. Записи с устаревшей командой чистит {@link #cleanupInvalidFor}
     * при входе и в тике.</p>
     *
     * @param excludedPlayerId игрок, которого не считать (переназначение самому себе)
     */
    public static int roleCount(MinecraftServer server, String teamId, CombatRole role,
                                @Nullable UUID excludedPlayerId) {
        if (server == null || role == null) return 0;
        String team = Teams.normalize(teamId);
        if (team.isBlank()) return 0;
        int count = 0;
        for (Map.Entry<UUID, RoleSavedData.RoleEntry> stored : RoleSavedData.get(server).entries().entrySet()) {
            if (excludedPlayerId != null && excludedPlayerId.equals(stored.getKey())) continue;
            RoleSavedData.RoleEntry entry = stored.getValue();
            if (!team.equals(entry.teamId())) continue;
            if (CombatRole.byIdOrAlias(entry.roleId()) == role) count++;
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
