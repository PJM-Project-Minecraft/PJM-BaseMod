package ru.liko.pjmbasemod.common.faction;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.dimension.LobbyService;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.FactionManagementSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenFactionManagementPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenFactionSelectionPacket;
import ru.liko.pjmbasemod.common.role.CombatRole;
import ru.liko.pjmbasemod.common.role.RoleLimitRegistry;
import ru.liko.pjmbasemod.common.role.RolePermissions;
import ru.liko.pjmbasemod.common.role.RoleService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
            if (deputyTeam != null && !deputyTeam.equals(FrontlineTeams.resolvePlayerTeamId(player))) {
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
        String team = FrontlineTeams.resolvePlayerTeamId(target);
        if (team == null || team.isBlank()) return false;
        // Debug всегда открывает с полными правами, минуя проверки прав.
        Authority full = new Authority(team, true, true, true, true);
        PjmNetworking.sendToPlayer(target, new OpenFactionManagementPacket(managementSnapshot(target, full)));
        return true;
    }

    public static void handleSelection(ServerPlayer player, String rawTeamId, String rawRoleId) {
        if (player == null || player.getServer() == null) return;
        MinecraftServer server = player.getServer();

        String team = FrontlineTeams.resolveAlias(rawTeamId);
        if (team == null || !FrontlineTeams.isCombatTeam(team)) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.selection.invalid_team"), true);
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

        FactionSelectionSavedData.get(server).markComplete(player.getUUID(), player.getName().getString(), team, role);
        FactionCommanderService.sync(player);
        FactionCommanderService.refreshTabName(player);
        player.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.selection.complete",
                FrontlineTeams.displayName(server, team), Component.translatable(role.translationKey())), true);
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

        ServerPlayer target = actor.getServer().getPlayerList().getPlayer(targetId);
        if (target == null) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.target_missing"), true);
            resync(actor);
            return;
        }

        String targetTeam = FrontlineTeams.resolvePlayerTeamId(target);
        if (!team.equals(targetTeam)) {
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

        RoleService.AssignmentResult result = RoleService.assignRole(actor, target, role, false);
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

        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.target_missing"), true);
            resync(actor);
            return;
        }
        if (!team.equals(FrontlineTeams.resolvePlayerTeamId(target))) {
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
                    target.getName().getString()), true);
        } else {
            data.removeDeputy(team, targetId);
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.deputy.removed",
                    target.getName().getString()), true);
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
        String team = FrontlineTeams.resolvePlayerTeamId(actor);
        if (team == null || team.isBlank()) return Authority.NONE;

        boolean admin = RolePermissions.can(actor, RolePermissions.ADMIN);
        boolean commander = team.equals(FactionCommanderService.activeCommanderTeam(actor));
        boolean full = admin || commander;
        int perms = FactionDeputySavedData.get(actor.getServer()).permissions(team, actor.getUUID());

        boolean open = full || DeputyPermission.has(perms, DeputyPermission.OPEN_GUI);
        boolean roles = full || DeputyPermission.has(perms, DeputyPermission.ASSIGN_ROLES);
        boolean order = full || DeputyPermission.has(perms, DeputyPermission.SET_ORDER);
        return new Authority(team, open, roles, order, full);
    }

    public record Authority(String teamId, boolean canOpen, boolean canAssignRoles,
                            boolean canSetOrder, boolean canManageDeputies) {
        public static final Authority NONE = new Authority("", false, false, false, false);

        public boolean valid() {
            return teamId != null && !teamId.isBlank();
        }
    }

    private static FactionSelectionSnapshot selectionSnapshot(ServerPlayer player, boolean required) {
        String currentTeam = FrontlineTeams.resolvePlayerTeamId(player);
        return new FactionSelectionSnapshot(teamEntries(player.getServer()),
                currentTeam == null ? "" : currentTeam,
                RoleService.currentRoleId(player),
                required);
    }

    private static FactionManagementSnapshot managementSnapshot(ServerPlayer actor, Authority authority) {
        MinecraftServer server = actor.getServer();
        String team = authority.teamId();
        FactionDeputySavedData deputies = FactionDeputySavedData.get(server);

        List<FactionManagementSnapshot.MemberEntry> members = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!team.equals(FrontlineTeams.resolvePlayerTeamId(player))) continue;
            String commanderTeam = FactionCommanderService.activeCommanderTeam(player);
            int perms = deputies.permissions(team, player.getUUID());
            members.add(new FactionManagementSnapshot.MemberEntry(player.getUUID(),
                    player.getName().getString(), RoleService.currentRoleId(player),
                    team.equals(commanderTeam),
                    deputies.isDeputy(team, player.getUUID()), perms));
        }
        members.sort(Comparator.comparing(FactionManagementSnapshot.MemberEntry::commander).reversed()
                .thenComparing(FactionManagementSnapshot.MemberEntry::deputy, Comparator.reverseOrder())
                .thenComparing(FactionManagementSnapshot.MemberEntry::name, String.CASE_INSENSITIVE_ORDER));

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

        return new FactionManagementSnapshot(team,
                FrontlineTeams.displayName(server, team),
                FrontlineTeams.color(server, team),
                true,
                List.copyOf(members),
                roleEntries(server, team),
                authority.canAssignRoles(),
                authority.canManageDeputies(),
                authority.canSetOrder(),
                Config.getFactionMaxDeputies(),
                deputies.deputyCount(team),
                orderText, orderAuthor, orderSeconds);
    }

    private static List<FactionSelectionSnapshot.TeamEntry> teamEntries(MinecraftServer server) {
        List<FactionSelectionSnapshot.TeamEntry> teams = new ArrayList<>();
        for (var team : FrontlineTeams.all()) {
            teams.add(new FactionSelectionSnapshot.TeamEntry(team.id(),
                    FrontlineTeams.displayName(server, team.id()),
                    FrontlineTeams.color(server, team.id()),
                    roleEntries(server, team.id())));
        }
        return List.copyOf(teams);
    }

    private static List<FactionSelectionSnapshot.RoleEntry> roleEntries(MinecraftServer server, String teamId) {
        List<FactionSelectionSnapshot.RoleEntry> roles = new ArrayList<>();
        for (CombatRole role : CombatRole.values()) {
            roles.add(new FactionSelectionSnapshot.RoleEntry(role.id(), role.displayName(), role.color(),
                    RoleLimitRegistry.get().limitFor(teamId, role),
                    RoleService.onlineRoleCount(server, teamId, role)));
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
