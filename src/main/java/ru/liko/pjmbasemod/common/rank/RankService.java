package ru.liko.pjmbasemod.common.rank;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.frontline.FrontlineSectorKey;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.RankSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RankXpPacket;
import ru.liko.pjmbasemod.common.region.Region;

public final class RankService {

    private RankService() {
    }

    public static void onServerStarted(MinecraftServer server) {
        RankRegistry.get().reload();
        syncAll(server);
    }

    public static void sync(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        int xp = RankSavedData.get(player.getServer()).xp(player.getUUID());
        PjmNetworking.sendToPlayer(player, RankSyncPacket.from(RankSnapshot.of(xp)));
        refreshTabName(player);
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    public static int xp(ServerPlayer player) {
        if (player == null || player.getServer() == null) return 0;
        return RankSavedData.get(player.getServer()).xp(player.getUUID());
    }

    public static RankSnapshot snapshot(ServerPlayer player) {
        return RankSnapshot.of(xp(player));
    }

    public static void addXp(ServerPlayer player, int delta, String reason) {
        if (player == null || player.getServer() == null) return;
        RankConfig config = RankRegistry.get().config();
        if (!config.enabled() || delta == 0) return;

        RankSavedData data = RankSavedData.get(player.getServer());
        int oldXp = data.xp(player.getUUID());
        RankDefinition oldRank = RankRegistry.get().rankForXp(oldXp);
        int newXp = oldXp + delta;
        if (delta < 0 && !config.allowDemotion()) {
            newXp = Math.max(oldRank.minXp(), newXp);
        }
        setXpInternal(player, data, oldXp, Math.max(0, newXp), delta, reason, true);
    }

    public static void setXp(ServerPlayer player, int xp, String reason) {
        if (player == null || player.getServer() == null) return;
        RankSavedData data = RankSavedData.get(player.getServer());
        int oldXp = data.xp(player.getUUID());
        setXpInternal(player, data, oldXp, Math.max(0, xp), Math.max(0, xp) - oldXp, reason, true);
    }

    public static void reset(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        setXp(player, 0, "reset");
    }

    public static void handlePlayerKill(ServerPlayer killer, ServerPlayer victim) {
        RankConfig config = RankRegistry.get().config();
        if (!config.enabled() || killer == null || victim == null || killer == victim) return;

        String killerTeam = FrontlineTeams.resolvePlayerTeamId(killer);
        String victimTeam = FrontlineTeams.resolvePlayerTeamId(victim);
        if (killerTeam == null || victimTeam == null || killerTeam.isBlank() || victimTeam.isBlank()) return;

        if (killerTeam.equals(victimTeam)) {
            addXp(killer, config.teamKillXp(), "teamkill");
        } else {
            addXp(killer, config.enemyKillXp(), "kill");
        }
    }

    public static int rewardSectorCapture(MinecraftServer server, Region region, FrontlineSectorKey sector, String teamId) {
        RankConfig config = RankRegistry.get().config();
        if (server == null || region == null || sector == null || !config.enabled() || config.sectorCaptureXp() == 0) return 0;
        if (!FrontlineTeams.isCombatTeam(teamId)) return 0;

        int rewarded = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) continue;
            if (!teamId.equals(FrontlineTeams.resolvePlayerTeamId(player))) continue;
            if (!region.dimension().equals(player.serverLevel().dimension().location().toString())) continue;

            ChunkPos pos = player.chunkPosition();
            if (!region.contains(region.dimension(), pos.x, pos.z)) continue;
            if (!sector.equals(FrontlineSectorKey.of(region, pos))) continue;

            addXp(player, config.sectorCaptureXp(), "sector");
            rewarded++;
        }
        return rewarded;
    }

    public static Component tabListName(ServerPlayer player) {
        RankSnapshot snapshot = snapshot(player);
        Component prefix = Component.literal("[" + snapshot.shortName() + "] ")
                .withStyle(style -> style.withColor(TextColor.fromRgb(snapshot.accentColor())));
        return prefix.copy().append(player.getName());
    }

    public static boolean shouldShowTabPrefix() {
        RankConfig config = RankRegistry.get().config();
        return config.enabled() && config.showTabPrefix();
    }

    public static void refreshTabName(ServerPlayer player) {
        if (player != null) player.refreshTabListName();
    }

    private static void setXpInternal(ServerPlayer player, RankSavedData data, int oldXp, int newXp, int delta, String reason, boolean sendDelta) {
        RankRegistry registry = RankRegistry.get();
        RankDefinition oldRank = registry.rankForXp(oldXp);
        data.setXp(player.getUUID(), newXp);
        RankDefinition newRank = registry.rankForXp(newXp);
        RankDefinition nextRank = registry.nextRankAfter(newRank);
        RankSnapshot snapshot = RankSnapshot.of(newXp, registry.config(), newRank, nextRank);
        boolean rankChanged = !oldRank.id().equals(newRank.id());
        boolean promoted = newRank.minXp() > oldRank.minXp();

        if (sendDelta) {
            PjmNetworking.sendToPlayer(player, RankXpPacket.from(delta, reason, rankChanged, promoted, snapshot));
        } else {
            PjmNetworking.sendToPlayer(player, RankSyncPacket.from(snapshot));
        }
        if (rankChanged || RankRegistry.get().config().showTabPrefix()) {
            refreshTabName(player);
        }

        Pjmbasemod.LOGGER.debug("Rank XP: {} {} -> {} ({}, delta={})",
                player.getGameProfile().getName(), oldXp, newXp, reason, delta);
    }
}
