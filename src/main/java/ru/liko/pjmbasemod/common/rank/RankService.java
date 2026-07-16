package ru.liko.pjmbasemod.common.rank;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.capturepoint.CapturePoint;
import ru.liko.pjmbasemod.common.fleet.VehicleFleetManager;
import ru.liko.pjmbasemod.common.teams.Teams;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.RankSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RankXpPacket;

import java.util.List;

import javax.annotation.Nullable;

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

    /**
     * Удовлетворяет ли игрок требованию минимального ранга {@code minRankId} (этот ранг и выше по XP).
     * Пустой/неизвестный id — ограничения нет (доступно всем).
     */
    public static boolean meetsMinRank(ServerPlayer player, @Nullable String minRankId) {
        if (minRankId == null || minRankId.isBlank()) return true;
        // Командир фракции («генерал») игнорирует ограничения по званию — доступ ко всему складу/гаражу.
        if (ru.liko.pjmbasemod.common.faction.FactionCommanderService.isActiveCommander(player)) return true;
        RankDefinition required = RankRegistry.get().byId(minRankId);
        if (required == null) return true;
        RankDefinition playerRank = RankRegistry.get().rankForXp(xp(player));
        return playerRank.minXp() >= required.minXp();
    }

    /** Сообщение «требуется ранг X» для GUI/чата. Для неизвестного id показывает сам id. */
    public static Component requiredRankMessage(@Nullable String minRankId) {
        RankDefinition required = minRankId == null ? null : RankRegistry.get().byId(minRankId);
        String name = required != null ? required.displayName() : (minRankId == null ? "" : minRankId);
        return Component.translatable("gui.pjmbasemod.rank.required", name);
    }

    /** Отображаемое имя ранга по id (или сам id, если ранг неизвестен; пусто — пустая строка). */
    public static String rankDisplayName(@Nullable String minRankId) {
        if (minRankId == null || minRankId.isBlank()) return "";
        RankDefinition required = RankRegistry.get().byId(minRankId);
        return required != null ? required.displayName() : minRankId;
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

        String killerTeam = Teams.resolvePlayerTeamId(killer);
        String victimTeam = Teams.resolvePlayerTeamId(victim);
        if (killerTeam == null || victimTeam == null || killerTeam.isBlank() || victimTeam.isBlank()) return;

        if (killerTeam.equals(victimTeam)) {
            addXp(killer, config.teamKillXp(), "teamkill");
        } else {
            addXp(killer, config.enemyKillXp(), "kill");
        }
    }

    /** Начисляет XP за уничтожение техники команды-противника. */
    public static void handleVehicleDestroyed(@Nullable Entity attacker, Entity vehicle) {
        RankConfig config = RankRegistry.get().config();
        if (!config.enabled() || config.enemyVehicleDestroyXp() == 0
                || !(attacker instanceof ServerPlayer player) || vehicle == null || player.getServer() == null) return;

        String attackerTeam = Teams.resolvePlayerTeamId(player);
        String vehicleTeam = VehicleFleetManager.teamId(player.getServer(), vehicle);
        if (!Teams.isCombatTeam(attackerTeam) || !Teams.isCombatTeam(vehicleTeam)
                || attackerTeam.equals(vehicleTeam)) return;

        addXp(player, config.enemyVehicleDestroyXp(), "vehicle");
    }

    /**
     * Награда XP игрокам команды, находящимся внутри полигона точки захвата.
     * Вызывается из {@link ru.liko.pjmbasemod.common.capturepoint.CapturePointManager}
     * при завершении захвата точки.
     * @return количество награждённых игроков.
     */
    public static int rewardCapturePoint(MinecraftServer server, String pointId, String dimension,
                                         List<CapturePoint.Vertex> vertices, String teamId) {
        RankConfig config = RankRegistry.get().config();
        if (server == null || !config.enabled() || config.sectorCaptureXp() == 0) return 0;
        if (!Teams.isCombatTeam(teamId)) return 0;

        int rewarded = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) continue;
            if (!teamId.equals(Teams.resolvePlayerTeamId(player))) continue;
            if (!dimension.equals(player.serverLevel().dimension().location().toString())) continue;
            if (!CapturePoint.contains(vertices, player.blockPosition().getX(), player.blockPosition().getZ())) continue;
            addXp(player, config.sectorCaptureXp(), "capture_point");
            rewarded++;
        }
        return rewarded;
    }

    /**
     * Штраф XP всем игрокам команды {@code teamId}, онлайн на сервере, на общую сумму {@code totalXp}
     * (распределяется поровну между участниками). Используется при проигрыше налёта дронов.
     * @return количество оштрафованных игроков.
     */
    public static int penalizeTeam(MinecraftServer server, String teamId, int totalXp, String reason) {
        if (server == null || totalXp <= 0 || !Teams.isCombatTeam(teamId)) return 0;
        RankConfig config = RankRegistry.get().config();
        if (!config.enabled()) return 0;

        java.util.List<ServerPlayer> members = new java.util.ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            if (teamId.equals(Teams.resolvePlayerTeamId(player))) {
                members.add(player);
            }
        }
        if (members.isEmpty()) return 0;

        int perPlayer = totalXp / members.size();
        if (perPlayer <= 0) return 0;
        for (ServerPlayer player : members) {
            addXp(player, -perPlayer, reason);
        }
        return members.size();
    }

    /** ResourceLocation bitmap-шрифта с иконками рангов ({@code assets/pjmbasemod/font/rank_icons.json}). */
    private static final ResourceLocation CHAT_RANK_FONT =
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "rank_icons");

    /**
     * Бейдж ранга для строки чата: иконка (bitmap-шрифт) + {@code [shortName]} цветом ранга + пробел.
     * Возвращает {@code null}, если система рангов выключена или {@code showChatRank == false}.
     */
    @Nullable
    public static Component chatBadge(ServerPlayer sender) {
        if (sender == null || sender.getServer() == null) return null;
        RankConfig config = RankRegistry.get().config();
        if (!config.enabled() || !config.showChatRank()) return null;

        RankDefinition rank = RankRegistry.get().rankForXp(xp(sender));
        int color = rank.accentColorRgb();
        net.minecraft.network.chat.MutableComponent badge = Component.empty();

        String glyph = rank.chatGlyph();
        if (glyph != null && !glyph.isEmpty()) {
            badge.append(Component.literal(glyph)
                    .withStyle(style -> style.withFont(CHAT_RANK_FONT).withColor(TextColor.fromRgb(0xFFFFFF))));
            badge.append(Component.literal(" "));
        }
        badge.append(Component.literal("[" + rank.shortName() + "] ")
                .withStyle(style -> style.withColor(TextColor.fromRgb(color))));
        return badge;
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
