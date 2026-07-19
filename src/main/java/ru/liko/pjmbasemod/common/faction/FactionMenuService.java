package ru.liko.pjmbasemod.common.faction;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.dimension.LobbyService;
import ru.liko.pjmbasemod.common.teams.Teams;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.FactionManagementSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenFactionManagementPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenFactionSelectionPacket;
import ru.liko.pjmbasemod.common.role.CombatRole;
import ru.liko.pjmbasemod.common.role.RoleLimitRegistry;
import ru.liko.pjmbasemod.common.role.RolePermissions;
import ru.liko.pjmbasemod.common.role.RoleService;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FactionMenuService {

    private static final int REOPEN_INTERVAL_TICKS = 80;

    private FactionMenuService() {
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (needsFirstJoinSelection(player)) {
            openSelection(player);
        }
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        if (player.serverLevel().getGameTime() % 40L == 0L) {
            FactionDeputySavedData data = FactionDeputySavedData.get(player.getServer());
            String deputyTeam = data.deputyTeamOf(player.getUUID());
            if (deputyTeam != null && !deputyTeam.equals(Teams.resolvePlayerTeamId(player))) {
                data.removeDeputy(deputyTeam, player.getUUID());
            }
        }
        if (!needsFirstJoinSelection(player)) return;
        if (player.serverLevel().getGameTime() % REOPEN_INTERVAL_TICKS == 20L) {
            openSelection(player);
        }
    }

    public static boolean needsFirstJoinSelection(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        return !FactionSelectionSavedData.get(player.getServer()).isComplete(player.getUUID());
    }

    public static void openSelection(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        PjmNetworking.sendToPlayer(player, new OpenFactionSelectionPacket(selectionSnapshot(player, true)));
    }

    /** Debug: принудительно открыть экран выбора фракции у цели (закрываемый, required=false). */
    public static void debugOpenSelection(ServerPlayer target) {
        if (target == null || target.getServer() == null) return;
        PjmNetworking.sendToPlayer(target, new OpenFactionSelectionPacket(selectionSnapshot(target, false)));
    }

    /**
     * Debug: открыть экран управления фракцией у цели, минуя проверку прав.
     * Команда резолвится напрямую по scoreboard цели.
     *
     * @return {@code false}, если у цели нет боевой команды.
     */
    public static boolean debugOpenManagement(ServerPlayer target) {
        if (target == null || target.getServer() == null) return false;
        String team = Teams.resolvePlayerTeamId(target);
        if (team == null || team.isBlank()) return false;
        // Debug всегда открывает с полными правами, минуя проверки прав.
        Authority full = new Authority(team, true, true, true, true, true);
        PjmNetworking.sendToPlayer(target, new OpenFactionManagementPacket(managementSnapshot(target, full)));
        return true;
    }

    public static void handleSelection(ServerPlayer player, String rawTeamId, String rawRoleId) {
        if (player == null || player.getServer() == null) return;
        MinecraftServer server = player.getServer();

        String team = Teams.resolveAlias(rawTeamId);
        if (team == null || !Teams.isCombatTeam(team)) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.selection.invalid_team"), true);
            openSelection(player);
            return;
        }

        if (lockedFor(player, team)) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.selection.invite_required",
                    Teams.displayName(server, team)), true);
            openSelection(player);
            return;
        }

        TeamBalanceService.Decision balance = TeamBalanceService.check(server, player, team);
        if (!balance.allowed()) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.balance.blocked",
                    Teams.displayName(server, balance.suggestedTeamId())), true);
            openSelection(player);
            return;
        }

        CombatRole role = CombatRole.byIdOrAlias(rawRoleId);
        if (role == null) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.selection.invalid_role"), true);
            openSelection(player);
            return;
        }

        RoleService.AssignmentResult capResult = RoleService.validateRoleCap(server, player.getUUID(), team, role);
        if (!capResult.success()) {
            player.displayClientMessage(capResult.message(), true);
            openSelection(player);
            return;
        }

        ensureScoreboardTeam(server, team);
        server.getScoreboard().addPlayerToTeam(player.getScoreboardName(), server.getScoreboard().getPlayerTeam(team));

        RoleService.AssignmentResult roleResult = RoleService.assignRole(null, player, role, false);
        if (!roleResult.success()) {
            player.displayClientMessage(roleResult.message(), true);
            openSelection(player);
            return;
        }

        FactionInviteSavedData.get(server).consume(team, player.getScoreboardName());
        ru.liko.pjmbasemod.common.logging.PjmActionLogger.instance().logSubsystem(
                ru.liko.pjmbasemod.common.logging.LogCategory.FACTION,
                String.format("%s вступил во фракцию %s (роль %s)",
                        player.getName().getString(), team, role.id()));
        FactionSelectionSavedData.get(server).markComplete(player.getUUID(), player.getName().getString(), team, role);
        FactionCommanderService.sync(player);
        FactionCommanderService.refreshTabName(player);
        player.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.selection.complete",
                Teams.displayName(server, team), Component.translatable(role.translationKey())), true);
        LobbyService.returnToOverworld(player);
        FactionJoinActions.run(player, team);
    }

    public static boolean openManagement(ServerPlayer actor) {
        if (actor == null || actor.getServer() == null) return false;
        Authority authority = authority(actor);
        if (!authority.valid() || !authority.canOpen()) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.no_access"), true);
            return false;
        }
        PjmNetworking.sendToPlayer(actor, new OpenFactionManagementPacket(managementSnapshot(actor, authority)));
        return true;
    }

    public static void handleManageRole(ServerPlayer actor, UUID targetId, String roleId) {
        if (actor == null || actor.getServer() == null || targetId == null) return;
        Authority authority = authority(actor);
        if (!authority.valid() || !authority.canAssignRoles()) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.no_access"), true);
            return;
        }
        String team = authority.teamId();

        ManagedTarget target = resolveTarget(actor.getServer(), targetId);
        if (target == null) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.target_missing"), true);
            resync(actor);
            return;
        }

        if (!team.equals(target.teamId())) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.target_wrong_faction"), true);
            resync(actor);
            return;
        }

        CombatRole role = roleId == null || roleId.isBlank() ? null : CombatRole.byIdOrAlias(roleId);
        if (role == null && roleId != null && !roleId.isBlank()) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.unknown", roleId), true);
            resync(actor);
            return;
        }

        RoleService.AssignmentResult result = RoleService.assignRoleTo(actor, actor.getServer(),
                target.id(), target.name(), target.teamId(), role);
        actor.displayClientMessage(result.message(), true);
        resync(actor);
    }

    public static void handleManageDeputy(ServerPlayer actor, UUID targetId, boolean deputy, int perms) {
        if (actor == null || actor.getServer() == null || targetId == null) return;
        Authority authority = authority(actor);
        if (!authority.valid() || !authority.canManageDeputies()) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.no_access"), true);
            return;
        }
        String team = authority.teamId();
        MinecraftServer server = actor.getServer();

        ManagedTarget target = resolveTarget(server, targetId);
        if (target == null) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.target_missing"), true);
            resync(actor);
            return;
        }
        if (!team.equals(target.teamId())) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.target_wrong_faction"), true);
            resync(actor);
            return;
        }

        FactionDeputySavedData data = FactionDeputySavedData.get(server);
        if (deputy) {
            boolean already = data.isDeputy(team, targetId);
            if (!already && data.deputyCount(team) >= Config.getFactionMaxDeputies()) {
                actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.deputy.limit_reached"), true);
                resync(actor);
                return;
            }
            data.setDeputy(team, targetId, DeputyPermission.sanitize(perms));
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.deputy.added",
                    target.name()), true);
        } else {
            data.removeDeputy(team, targetId);
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.deputy.removed",
                    target.name()), true);
        }
        // Оффлайн-цели рассылать нечего: статус зама лежит в SavedData, а тег в TAB, право на
        // радиалку и дерево команд она получит при входе (FactionCommanderService.onPlayerLogin).
        if (target.online() != null) {
            FactionCommanderService.refreshTabName(target.online());
            // Синхронизируем цели её право открыть управление (кнопка радиалки) и обновляем
            // дерево команд, чтобы /pjm faction manage стал доступен/недоступен без релога.
            FactionCommanderService.sync(target.online());
            server.getCommands().sendCommands(target.online());
        }
        resync(actor);
    }

    public static void resync(ServerPlayer actor) {
        if (actor == null || actor.getServer() == null) return;
        Authority authority = authority(actor);
        if (!authority.valid() || !authority.canOpen()) return;
        PjmNetworking.sendToPlayer(actor, new FactionManagementSyncPacket(managementSnapshot(actor, authority)));
    }

    /** Что текущий игрок вправе делать в управлении своей фракцией. */
    public static Authority authority(ServerPlayer actor) {
        if (actor == null || actor.getServer() == null) return Authority.NONE;
        String team = Teams.resolvePlayerTeamId(actor);
        if (team == null || team.isBlank()) return Authority.NONE;

        boolean admin = RolePermissions.can(actor, RolePermissions.ADMIN);
        boolean commander = team.equals(FactionCommanderService.activeCommanderTeam(actor));
        boolean full = admin || commander;
        int perms = FactionDeputySavedData.get(actor.getServer()).permissions(team, actor.getUUID());

        boolean open = full || DeputyPermission.has(perms, DeputyPermission.OPEN_GUI);
        boolean roles = full || DeputyPermission.has(perms, DeputyPermission.ASSIGN_ROLES);
        boolean order = full || DeputyPermission.has(perms, DeputyPermission.SET_ORDER);
        boolean invite = full || DeputyPermission.has(perms, DeputyPermission.INVITE);
        return new Authority(team, open, roles, order, full, invite);
    }

    public record Authority(String teamId, boolean canOpen, boolean canAssignRoles,
                            boolean canSetOrder, boolean canManageDeputies, boolean canInvite) {
        public static final Authority NONE = new Authority("", false, false, false, false, false);

        public boolean valid() {
            return teamId != null && !teamId.isBlank();
        }
    }

    private static FactionSelectionSnapshot selectionSnapshot(ServerPlayer player, boolean required) {
        String currentTeam = Teams.resolvePlayerTeamId(player);
        return new FactionSelectionSnapshot(teamEntries(player),
                currentTeam == null ? "" : currentTeam,
                RoleService.currentRoleId(player),
                required);
    }

    /** Фракция закрыта для игрока: «по приглашению», игрок не её член, не в whitelist и приглашения нет. */
    private static boolean lockedFor(ServerPlayer player, String teamId) {
        if (!Config.isTeamInviteOnly(teamId)) return false;
        if (teamId.equals(Teams.resolvePlayerTeamId(player))) return false;
        if (RolePermissions.can(player, RolePermissions.ADMIN)) return false;
        if (Config.isTeamWhitelisted(teamId, player.getScoreboardName())) return false;
        return !FactionInviteSavedData.get(player.getServer()).isInvited(teamId, player.getScoreboardName());
    }

    private static FactionManagementSnapshot managementSnapshot(ServerPlayer actor, Authority authority) {
        MinecraftServer server = actor.getServer();
        String team = authority.teamId();
        FactionDeputySavedData deputies = FactionDeputySavedData.get(server);

        List<FactionManagementSnapshot.MemberEntry> members = memberEntries(server, team, deputies);

        FactionOrderSavedData.OrderEntry order = FactionOrderSavedData.get(server).order(team);
        long now = server.overworld().getGameTime();
        String orderText = "";
        String orderAuthor = "";
        int orderSeconds = 0;
        if (order != null && !(order.expiresAtGameTime() >= 0 && now >= order.expiresAtGameTime())) {
            orderText = order.text();
            orderAuthor = order.author();
            orderSeconds = order.expiresAtGameTime() < 0 ? -1
                    : (int) Math.max(1, (order.expiresAtGameTime() - now) / 20);
        }

        boolean inviteOnly = Config.isTeamInviteOnly(team);
        List<FactionManagementSnapshot.InviteEntry> invites = new ArrayList<>();
        if (inviteOnly && authority.canInvite()) {
            long nowMs = System.currentTimeMillis();
            for (Map.Entry<String, Long> invite : FactionInviteSavedData.get(server).invites(team).entrySet()) {
                int minutes = invite.getValue() == 0L ? -1
                        : (int) Math.max(1, (invite.getValue() - nowMs) / 60_000L);
                invites.add(new FactionManagementSnapshot.InviteEntry(invite.getKey(), minutes));
            }
            invites.sort(Comparator.comparing(FactionManagementSnapshot.InviteEntry::name));
        }

        return new FactionManagementSnapshot(team,
                Teams.displayName(server, team),
                Teams.color(server, team),
                true,
                List.copyOf(members),
                roleEntries(server, team),
                authority.canAssignRoles(),
                authority.canManageDeputies(),
                authority.canSetOrder(),
                Config.getFactionMaxDeputies(),
                deputies.deputyCount(team),
                orderText, orderAuthor, orderSeconds,
                inviteOnly, authority.canInvite(), List.copyOf(invites));
    }

    /** Выдать/отозвать приглашение в закрытую фракцию. Доступно командиру, заму с правом INVITE и админу. */
    public static void handleManageInvite(ServerPlayer actor, String playerName, boolean invite) {
        if (actor == null || actor.getServer() == null) return;
        String name = playerName == null ? "" : playerName.trim();
        if (name.isBlank() || name.length() > 16) return;
        Authority authority = authority(actor);
        if (!authority.valid() || !authority.canInvite()) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.no_access"), true);
            return;
        }
        applyInvite(actor, authority.teamId(), name, invite);
        resync(actor);
    }

    /** Общий низ выдачи приглашения — используется и GUI, и командой (в т.ч. админской с явной фракцией). */
    public static void applyInvite(ServerPlayer actor, String teamId, String playerName, boolean invite) {
        MinecraftServer server = actor.getServer();
        if (!Config.isTeamInviteOnly(teamId)) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.invite.not_invite_only",
                    Teams.displayName(server, teamId)), false);
            return;
        }
        FactionInviteSavedData data = FactionInviteSavedData.get(server);
        if (invite) {
            data.invite(teamId, playerName, Config.getFactionInviteTtlMinutes());
            ru.liko.pjmbasemod.common.logging.PjmActionLogger.instance().logSubsystem(
                    ru.liko.pjmbasemod.common.logging.LogCategory.FACTION,
                    String.format("%s пригласил %s во фракцию %s",
                            actor.getName().getString(), playerName, teamId));
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.invite.sent",
                    playerName, Teams.displayName(server, teamId)), false);
            ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
            if (target != null) {
                target.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.invite.received",
                        Teams.displayName(server, teamId)), false);
            }
        } else {
            if (data.revoke(teamId, playerName)) {
                actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.invite.revoked",
                        playerName), false);
            }
        }
    }

    /**
     * Члены фракции по scoreboard-команде — включая оффлайн: членство хранится по имени и
     * переживает выход. UUID берём у живого игрока, иначе из кэша профилей; запись без
     * профиля (в команду можно добавить и не-игрока) пропускаем.
     */
    private static List<FactionManagementSnapshot.MemberEntry> memberEntries(MinecraftServer server, String team,
                                                                            FactionDeputySavedData deputies) {
        FactionCommanderSavedData.CommanderEntry commander = FactionCommanderSavedData.get(server).commander(team);
        List<FactionManagementSnapshot.MemberEntry> members = new ArrayList<>();
        for (String name : Teams.memberNames(server, team)) {
            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
            UUID id = online != null ? online.getUUID() : profileId(server, name);
            if (id == null) continue;
            String roleId = online != null ? RoleService.currentRoleId(online)
                    : RoleService.storedRoleId(server, id, team);
            members.add(new FactionManagementSnapshot.MemberEntry(id, name, roleId,
                    commander != null && commander.playerId().equals(id),
                    deputies.isDeputy(team, id), deputies.permissions(team, id),
                    online != null));
        }
        // Командир → замы → онлайн выше оффлайна → по имени.
        members.sort(Comparator.comparing(FactionManagementSnapshot.MemberEntry::commander).reversed()
                .thenComparing(FactionManagementSnapshot.MemberEntry::deputy, Comparator.reverseOrder())
                .thenComparing(FactionManagementSnapshot.MemberEntry::online, Comparator.reverseOrder())
                .thenComparing(FactionManagementSnapshot.MemberEntry::name, String.CASE_INSENSITIVE_ORDER));
        return members;
    }

    @Nullable
    private static UUID profileId(MinecraftServer server, String name) {
        GameProfileCache cache = server.getProfileCache();
        return cache == null ? null : cache.get(name).map(GameProfile::getId).orElse(null);
    }

    /** Цель действия управления: работает и для оффлайн-игрока — имя и команда берутся из scoreboard. */
    @Nullable
    private static ManagedTarget resolveTarget(MinecraftServer server, UUID targetId) {
        ServerPlayer online = server.getPlayerList().getPlayer(targetId);
        if (online != null) {
            return new ManagedTarget(targetId, online.getName().getString(),
                    Teams.resolvePlayerTeamId(online), online);
        }
        GameProfileCache cache = server.getProfileCache();
        if (cache == null) return null;
        return cache.get(targetId)
                .map(profile -> new ManagedTarget(targetId, profile.getName(),
                        Teams.resolveTeamIdByName(server, profile.getName()), null))
                .orElse(null);
    }

    private record ManagedTarget(UUID id, String name, @Nullable String teamId, @Nullable ServerPlayer online) {
    }

    private static List<FactionSelectionSnapshot.TeamEntry> teamEntries(ServerPlayer viewer) {
        MinecraftServer server = viewer.getServer();
        List<FactionSelectionSnapshot.TeamEntry> teams = new ArrayList<>();
        for (var team : Teams.all()) {
            teams.add(new FactionSelectionSnapshot.TeamEntry(team.id(),
                    Teams.displayName(server, team.id()),
                    Teams.color(server, team.id()),
                    lockedFor(viewer, team.id()),
                    roleEntries(server, team.id())));
        }
        return List.copyOf(teams);
    }

    private static List<FactionSelectionSnapshot.RoleEntry> roleEntries(MinecraftServer server, String teamId) {
        List<FactionSelectionSnapshot.RoleEntry> roles = new ArrayList<>();
        for (CombatRole role : CombatRole.values()) {
            roles.add(new FactionSelectionSnapshot.RoleEntry(role.id(), role.displayName(), role.color(),
                    RoleLimitRegistry.get().limitFor(teamId, role),
                    RoleService.roleCount(server, teamId, role)));
        }
        return List.copyOf(roles);
    }

    private static void ensureScoreboardTeam(MinecraftServer server, String teamId) {
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(teamId);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamId);
            team.setDisplayName(Component.literal(teamId));
        }
    }
}
