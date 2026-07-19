package ru.liko.pjmbasemod.common.campaign;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.scores.PlayerTeam;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.capturepoint.CapturePointManager;
import ru.liko.pjmbasemod.common.capturepoint.CapturePointSavedData;
import ru.liko.pjmbasemod.common.compat.SbwVehicleClassifier;
import ru.liko.pjmbasemod.common.logging.LogCategory;
import ru.liko.pjmbasemod.common.logging.PjmActionLogger;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.CampaignSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;
import ru.liko.pjmbasemod.common.rank.RankSavedData;
import ru.liko.pjmbasemod.common.rank.RankService;
import ru.liko.pjmbasemod.common.teams.Teams;
import ru.liko.pjmbasemod.common.wipe.WipeService;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Недельная кампания: удерживаемые точки захвата тикают очки победы (VP) фракции
 * раз в {@code campaign.vpIntervalMinutes} реального времени (и вне окон захвата —
 * пока точки удерживаются). По истечении {@code campaign.durationDays} объявляется
 * победитель (максимум VP, строго больше второго; иначе — без победителя) и
 * выполняется полный вайп сезона: прогресс игроков ({@link WipeService#wipeAll}),
 * точки в нейтраль (кроме базовых), scoreboard-команды, техника и предметы на карте.
 * XP-бонус победителям начисляется ПОСЛЕ вайпа — стартовая фора нового сезона.
 */
public final class CampaignManager {

    private static int tickCounter;

    private CampaignManager() {}

    public static void onServerTick(MinecraftServer server) {
        if (server == null || !Config.isCampaignEnabled()) return;
        if (++tickCounter < 20) return; // проверки раз в секунду, точность — реальное время
        tickCounter = 0;

        CampaignSavedData data = CampaignSavedData.get(server);
        long now = System.currentTimeMillis();
        if (data.startEpochMs() <= 0) {
            data.restart(now);
            syncAll(server);
            return;
        }

        if (now >= data.startEpochMs() + durationMs()) {
            finish(server, "по истечении недели");
            return;
        }

        if (now >= data.lastVpGrantEpochMs() + Config.getCampaignVpIntervalMinutes() * 60_000L) {
            grantVp(server, data);
            data.markVpGrant(now);
            syncAll(server);
        }
    }

    /** VP за удерживаемые точки: {@code vpPerPoint} за каждую точку команды. */
    private static void grantVp(MinecraftServer server, CampaignSavedData data) {
        int perPoint = Config.getCampaignVpPerPoint();
        for (CapturePointSavedData.Entry entry : CapturePointSavedData.get(server).entries()) {
            if (!entry.ownerTeamId.isEmpty() && Teams.isCombatTeam(entry.ownerTeamId)) {
                data.addVp(entry.ownerTeamId, perPoint);
            }
        }
    }

    /** Принудительное или плановое завершение кампании: победитель → вайп → новый раунд. */
    public static void finish(MinecraftServer server, String reason) {
        CampaignSavedData data = CampaignSavedData.get(server);
        Map<String, Long> scores = data.vpByTeam();
        String winner = winner(scores);
        String scoreLine = scoreLine(server, scores);

        // UUID победителей резолвим ДО вайпа — он снимает игроков со scoreboard-команд.
        Set<UUID> winnerMembers = winner == null ? Set.of()
                : WipeService.resolveTeamMemberUuids(server, winner);

        if (winner != null) {
            String winnerName = Teams.displayName(server, winner);
            int color = Teams.color(server, winner);
            PjmNetworking.sendToAll(server, new NotificationPacket(
                    Component.literal("Кампания окончена"),
                    Component.literal("Победа: " + winnerName), color, 8000));
            broadcast(server, "Кампания окончена (" + reason + "). Победитель: " + winnerName
                    + ". Итог: " + scoreLine);
        } else {
            PjmNetworking.sendToAll(server, new NotificationPacket(
                    Component.literal("Кампания окончена"),
                    Component.literal("Без победителя"), 0x9B9B9B, 8000));
            broadcast(server, "Кампания окончена (" + reason + ") без победителя. Итог: " + scoreLine);
        }
        PjmActionLogger.instance().logSubsystem(LogCategory.CAPTURE,
                "кампания окончена (" + reason + "), победитель: " + (winner == null ? "нет" : winner)
                        + ", счёт: " + scoreLine);

        wipeSeason(server);

        // Фора победителям в новом сезоне (ранги уже вайпнуты — setXp с чистого листа).
        int bonus = Config.getCampaignWinnerXpBonus();
        if (winner != null && bonus > 0 && !winnerMembers.isEmpty()) {
            RankSavedData ranks = RankSavedData.get(server);
            for (UUID member : winnerMembers) ranks.setXp(member, bonus);
            RankService.syncAll(server);
        }

        data.restart(System.currentTimeMillis());
        syncAll(server);
        broadcast(server, "Началась новая кампания: неделя, весь прогресс сброшен. Выбирайте фракцию!");
    }

    /**
     * Вайп сезона поверх {@link WipeService#wipeAll}: точки в нейтраль (базы остаются),
     * все игроки снимаются со scoreboard-команд, с карты убираются техника и предметы.
     */
    private static void wipeSeason(MinecraftServer server) {
        WipeService.wipeAll(server);

        CapturePointSavedData points = CapturePointSavedData.get(server);
        points.resetForNewSeason();
        CapturePointManager.broadcastMapSync(server, points, "campaign_wipe");

        for (var team : Teams.all()) {
            PlayerTeam scoreboardTeam = server.getScoreboard().getPlayerTeam(team.id());
            if (scoreboardTeam == null) continue;
            for (String name : List.copyOf(scoreboardTeam.getPlayers())) {
                server.getScoreboard().removePlayerFromTeam(name, scoreboardTeam);
            }
        }

        if (Config.isCampaignWipeClearEntities()) {
            int removed = clearBattlefieldEntities(server);
            Pjmbasemod.LOGGER.info("Campaign: вайп удалил {} entity (техника/предметы).", removed);
        }
    }

    /** Удаляет технику (SBW) и лежащие предметы во всех измерениях. */
    private static int clearBattlefieldEntities(MinecraftServer server) {
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> toRemove = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity || SbwVehicleClassifier.isVehicleEntity(entity)) {
                    toRemove.add(entity);
                }
            }
            for (Entity entity : toRemove) entity.discard();
            removed += toRemove.size();
        }
        return removed;
    }

    /** Команда с максимумом VP, строго больше второго места; иначе null (ничья/пусто). */
    @Nullable
    private static String winner(Map<String, Long> scores) {
        String best = null;
        long bestVp = 0, secondVp = 0;
        for (Map.Entry<String, Long> e : scores.entrySet()) {
            if (e.getValue() > bestVp) {
                secondVp = bestVp;
                bestVp = e.getValue();
                best = e.getKey();
            } else if (e.getValue() > secondVp) {
                secondVp = e.getValue();
            }
        }
        return bestVp > secondVp ? best : null;
    }

    private static String scoreLine(MinecraftServer server, Map<String, Long> scores) {
        if (scores.isEmpty()) return "0:0";
        StringBuilder sb = new StringBuilder();
        scores.forEach((team, vp) -> {
            if (!sb.isEmpty()) sb.append(" · ");
            sb.append(Teams.displayName(server, team)).append(' ').append(vp);
        });
        return sb.toString();
    }

    // ---------------------------------------------------------------- синхронизация

    public static void sendInitialSync(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        PjmNetworking.sendToPlayer(player, buildSync(player.getServer()));
    }

    public static void syncAll(MinecraftServer server) {
        PjmNetworking.sendToAll(server, buildSync(server));
    }

    public static CampaignSyncPacket buildSync(MinecraftServer server) {
        if (!Config.isCampaignEnabled()) return CampaignSyncPacket.empty();
        CampaignSavedData data = CampaignSavedData.get(server);
        if (data.startEpochMs() <= 0) return CampaignSyncPacket.empty();

        // Счёт всегда по всем боевым командам (и с нулём), в порядке объявления в конфиге.
        Map<String, Long> scores = data.vpByTeam();
        List<CampaignSyncPacket.TeamScore> entries = new ArrayList<>();
        Map<String, Long> ordered = new LinkedHashMap<>();
        for (var team : Teams.all()) ordered.put(team.id(), scores.getOrDefault(team.id(), 0L));
        ordered.forEach((teamId, vp) -> entries.add(new CampaignSyncPacket.TeamScore(
                Teams.displayName(server, teamId), Teams.color(server, teamId), vp)));

        long secondsToEnd = Math.max(0, (data.startEpochMs() + durationMs() - System.currentTimeMillis()) / 1000L);
        return new CampaignSyncPacket(true, (int) Math.min(Integer.MAX_VALUE, secondsToEnd), List.copyOf(entries));
    }

    private static long durationMs() {
        return Config.getCampaignDurationDays() * 86_400_000L;
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }
}
