package ru.liko.pjmbasemod.common.faction;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.FactionCommanderSyncPacket;
import ru.liko.pjmbasemod.common.rank.RankService;
import ru.liko.pjmbasemod.common.rank.RankSnapshot;
import ru.liko.pjmbasemod.common.role.RoleService;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FactionCommanderService {

    public static final String ROLE_SHORT_NAME = "КМД";
    public static final String ROLE_DISPLAY_NAME = "КОМАНДИР ФРАКЦИИ";
    public static final int ROLE_COLOR = 0xF0B43A;
    public static final String DEPUTY_SHORT_NAME = "ЗАМ";

    private FactionCommanderService() {
    }

    public static AssignmentResult setCommander(MinecraftServer server, String teamId, ServerPlayer target) {
        FactionCommanderSavedData data = FactionCommanderSavedData.get(server);
        String team = FrontlineTeams.normalize(teamId);
        Map<String, FactionCommanderSavedData.CommanderEntry> removedForTarget = data.clearPlayer(target.getUUID());
        FactionCommanderSavedData.CommanderEntry previous = data.setCommander(team, target.getUUID(), target.getName().getString());

        Set<UUID> affected = new LinkedHashSet<>();
        for (FactionCommanderSavedData.CommanderEntry removed : removedForTarget.values()) {
            affected.add(removed.playerId());
        }
        if (previous != null) affected.add(previous.playerId());
        affected.add(target.getUUID());
        syncAndRefresh(server, affected);
        return new AssignmentResult(team, previous, removedForTarget);
    }

    @Nullable
    public static FactionCommanderSavedData.CommanderEntry clearCommander(MinecraftServer server, String teamId) {
        FactionCommanderSavedData.CommanderEntry removed = FactionCommanderSavedData.get(server).clearCommander(teamId);
        if (removed != null) {
            syncAndRefresh(server, Set.of(removed.playerId()));
        }
        return removed;
    }

    public static void onPlayerLogin(ServerPlayer player) {
        cleanupInvalidFor(player);
        sync(player);
        refreshTabName(player);
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        if (player.serverLevel().getGameTime() % 40L != 0L) return;
        if (cleanupInvalidFor(player)) {
            sync(player);
            refreshTabName(player);
        }
    }

    public static void validateOnlineAssignments(MinecraftServer server) {
        if (server == null) return;
        Set<UUID> affected = new LinkedHashSet<>();
        FactionCommanderSavedData data = FactionCommanderSavedData.get(server);
        for (Map.Entry<String, FactionCommanderSavedData.CommanderEntry> entry : data.commanders().entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getValue().playerId());
            if (player == null) continue;
            if (!entry.getKey().equals(FrontlineTeams.resolvePlayerTeamId(player))) {
                data.clearCommander(entry.getKey());
                affected.add(player.getUUID());
            }
        }
        syncAndRefresh(server, affected);
    }

    public static boolean isActiveCommander(ServerPlayer player) {
        return activeCommanderTeam(player) != null;
    }

    @Nullable
    public static String activeCommanderTeam(ServerPlayer player) {
        if (player == null || player.getServer() == null) return null;
        FactionCommanderSavedData data = FactionCommanderSavedData.get(player.getServer());
        String assignedTeam = data.teamOf(player.getUUID());
        if (assignedTeam == null) return null;
        String currentTeam = FrontlineTeams.resolvePlayerTeamId(player);
        return assignedTeam.equals(currentTeam) ? assignedTeam : null;
    }

    @Nullable
    public static Component tabListName(ServerPlayer player) {
        String commanderTeam = activeCommanderTeam(player);
        boolean commander = commanderTeam != null;
        String playerTeam = FrontlineTeams.resolvePlayerTeamId(player);
        boolean deputy = !commander && playerTeam != null && player.getServer() != null
                && FactionDeputySavedData.get(player.getServer()).isDeputy(playerTeam, player.getUUID());
        boolean rank = RankService.shouldShowTabPrefix();
        if (!commander && !deputy && !rank) return null;

        MutableComponent name = Component.empty();
        if (commander) {
            int color = FrontlineTeams.color(player.getServer(), commanderTeam);
            name.append(Component.literal("[" + ROLE_SHORT_NAME + "] ")
                    .withStyle(style -> style.withColor(TextColor.fromRgb(color))));
        } else if (deputy) {
            int color = FrontlineTeams.color(player.getServer(), playerTeam);
            name.append(Component.literal("[" + DEPUTY_SHORT_NAME + "] ")
                    .withStyle(style -> style.withColor(TextColor.fromRgb(color))));
        }
        if (rank) {
            RankSnapshot snapshot = RankService.snapshot(player);
            name.append(Component.literal("[" + snapshot.shortName() + "] ")
                    .withStyle(style -> style.withColor(TextColor.fromRgb(snapshot.accentColor()))));
        }
        return name.append(player.getName());
    }

    public static void sync(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        String team = activeCommanderTeam(player);
        boolean canManage = FactionMenuService.authority(player).canOpen();
        PjmNetworking.sendToPlayer(player, FactionCommanderSyncPacket.from(player, team, canManage));
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
            refreshTabName(player);
        }
    }

    public static void refreshTabName(ServerPlayer player) {
        if (player != null) player.refreshTabListName();
    }

    private static boolean cleanupInvalidFor(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        FactionCommanderSavedData data = FactionCommanderSavedData.get(player.getServer());
        String assignedTeam = data.teamOf(player.getUUID());
        if (assignedTeam == null) return false;
        String currentTeam = FrontlineTeams.resolvePlayerTeamId(player);
        if (assignedTeam.equals(currentTeam)) return false;
        data.clearCommander(assignedTeam);
        return true;
    }

    private static void syncAndRefresh(MinecraftServer server, Set<UUID> playerIds) {
        if (server == null || playerIds.isEmpty()) return;
        for (UUID playerId : playerIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            sync(player);
            RoleService.sync(player);
            refreshTabName(player);
        }
    }

    public record AssignmentResult(
            String teamId,
            @Nullable FactionCommanderSavedData.CommanderEntry previous,
            Map<String, FactionCommanderSavedData.CommanderEntry> removedForTarget
    ) {
    }
}
