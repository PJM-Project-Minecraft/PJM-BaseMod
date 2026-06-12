package ru.liko.pjmbasemod.common.faction;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
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

import javax.annotation.Nullable;
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
        String team = managementTeam(actor);
        if (team == null) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.no_access"), true);
            return false;
        }
        PjmNetworking.sendToPlayer(actor, new OpenFactionManagementPacket(managementSnapshot(actor, team)));
        return true;
    }

    public static void handleManageRole(ServerPlayer actor, UUID targetId, String roleId) {
        if (actor == null || actor.getServer() == null || targetId == null) return;
        String team = managementTeam(actor);
        if (team == null) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.no_access"), true);
            return;
        }

        ServerPlayer target = actor.getServer().getPlayerList().getPlayer(targetId);
        if (target == null) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.target_missing"), true);
            syncManagement(actor, team);
            return;
        }

        String targetTeam = FrontlineTeams.resolvePlayerTeamId(target);
        if (!team.equals(targetTeam)) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.target_wrong_faction"), true);
            syncManagement(actor, team);
            return;
        }

        CombatRole role = roleId == null || roleId.isBlank() ? null : CombatRole.byIdOrAlias(roleId);
        if (role == null && roleId != null && !roleId.isBlank()) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.unknown", roleId), true);
            syncManagement(actor, team);
            return;
        }

        RoleService.AssignmentResult result = RoleService.assignRole(actor, target, role, false);
        actor.displayClientMessage(result.message(), true);
        syncManagement(actor, team);
    }

    private static void syncManagement(ServerPlayer actor, String team) {
        PjmNetworking.sendToPlayer(actor, new FactionManagementSyncPacket(managementSnapshot(actor, team)));
    }

    private static FactionSelectionSnapshot selectionSnapshot(ServerPlayer player, boolean required) {
        String currentTeam = FrontlineTeams.resolvePlayerTeamId(player);
        return new FactionSelectionSnapshot(teamEntries(player.getServer()),
                currentTeam == null ? "" : currentTeam,
                RoleService.currentRoleId(player),
                required);
    }

    private static FactionManagementSnapshot managementSnapshot(ServerPlayer actor, String team) {
        MinecraftServer server = actor.getServer();
        List<FactionManagementSnapshot.MemberEntry> members = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!team.equals(FrontlineTeams.resolvePlayerTeamId(player))) continue;
            String commanderTeam = FactionCommanderService.activeCommanderTeam(player);
            members.add(new FactionManagementSnapshot.MemberEntry(player.getUUID(),
                    player.getName().getString(), RoleService.currentRoleId(player),
                    team.equals(commanderTeam)));
        }
        members.sort(Comparator.comparing(FactionManagementSnapshot.MemberEntry::commander).reversed()
                .thenComparing(FactionManagementSnapshot.MemberEntry::name, String.CASE_INSENSITIVE_ORDER));

        return new FactionManagementSnapshot(team,
                FrontlineTeams.displayName(server, team),
                FrontlineTeams.color(server, team),
                true,
                List.copyOf(members),
                roleEntries(server, team));
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

    @Nullable
    private static String managementTeam(ServerPlayer actor) {
        if (actor == null || actor.getServer() == null) return null;
        String commanderTeam = FactionCommanderService.activeCommanderTeam(actor);
        if (commanderTeam != null) return commanderTeam;
        if (!RolePermissions.can(actor, RolePermissions.ADMIN)) return null;
        String currentTeam = FrontlineTeams.resolvePlayerTeamId(actor);
        return currentTeam == null || currentTeam.isBlank() ? null : currentTeam;
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
