package ru.liko.pjmbasemod.common.capturepoint;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.CapturePointHudPacket;
import ru.liko.pjmbasemod.common.network.packet.CapturePointMapSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.CapturePointEditorActionPacket;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;
import ru.liko.pjmbasemod.common.rank.RankService;
import ru.liko.pjmbasemod.common.teams.Teams;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Серверная тик-логика захвата точек. Двухфазный KoTH:
 * 1. NEUTRALIZE — если точка имеет владельца и враг получает перевес,
 *    прогресс владельца снисится 100→0.
 * 2. CAPTURE — когда прогресс достигает 0, захватывающая команда набирает 0→100.
 * При 100% точка переходит к новой команде (SECURED). Владелец внутри точки
 * поддерживает полный контроль. Ничья — заморозка (contestedFreeze) или спад.
 */
public final class CapturePointManager {

    private static int tickCounter;
    private static volatile long mapSyncRevision;
    private static volatile long lastMapSyncAtMs;
    private static volatile String lastMapSyncReason = "startup";

    private CapturePointManager() {}

    public static void onServerTick(MinecraftServer server) {
        if (server == null || !Config.isCapturePointsEnabled()) return;
        int interval = Math.max(1, Config.getCapturePointTickIntervalTicks());
        tickCounter++;
        if (tickCounter % interval != 0) return;

        CapturePointSavedData data = CapturePointSavedData.get(server);
        if (data.entries().isEmpty()) return;

        int requiredTicks = requiredCaptureTicks();
        int minAdvantage = Math.max(1, Config.getCapturePointMinAdvantage());
        boolean contestedFreeze = Config.isCapturePointContestedFreeze();
        int decayTicks = Math.max(1, requiredTicks / Math.max(1, Config.getCapturePointDecayTimeSeconds() * 20 / interval));

        Map<String, Integer> contestedFlags = new LinkedHashMap<>();
        boolean anyChanged = false;

        for (CapturePointSavedData.Entry entry : data.entries()) {
            Map<String, Integer> teamCounts = countPlayersInside(server, entry);
            int teamsInside = teamCounts.size();
            contestedFlags.put(entry.id, teamsInside);

            String leader = leader(teamCounts, minAdvantage);
            boolean changed = applyTick(entry, leader, teamsInside >= 2, contestedFreeze, interval, decayTicks, requiredTicks);
            if (changed) {
                anyChanged = true;
                handleCaptureComplete(server, entry, requiredTicks);
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendHud(server, player, data, requiredTicks);
        }

        if (anyChanged) {
            data.setDirty();
            broadcastMapSync(server, data, "capturepoint_data_changed");
        }
    }

    public static void sendInitialSync(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        if (!Config.isCapturePointsEnabled()) return;
        CapturePointSavedData data = CapturePointSavedData.get(player.getServer());
        broadcastMapSync(player.getServer(), data, "player_login");
    }

    public static void broadcastMapSync(MinecraftServer server, CapturePointSavedData data) {
        broadcastMapSync(server, data, "manual");
    }

    public static void broadcastMapSync(MinecraftServer server, CapturePointSavedData data, String reason) {
        int requiredTicks = requiredCaptureTicks();
        List<CapturePoint> points = data.snapshots(requiredTicks, null);
        PjmNetworking.sendToAll(server, new CapturePointMapSyncPacket(points));
        mapSyncRevision++;
        lastMapSyncAtMs = System.currentTimeMillis();
        lastMapSyncReason = reason;
    }

    private static int requiredCaptureTicks() {
        return Math.max(1, Config.getCapturePointCaptureTimeSeconds() * 20);
    }

    private static Map<String, Integer> countPlayersInside(MinecraftServer server, CapturePointSavedData.Entry entry) {
        Map<String, Integer> counts = new HashMap<>();
        if (entry.vertices.size() < 3) return counts;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            if (!entry.dimension.equals(player.serverLevel().dimension().location().toString())) continue;
            String teamId = Teams.resolvePlayerTeamId(player);
            if (teamId == null || teamId.isBlank()) continue;
            Vec3 pos = player.position();
            if (CapturePoint.contains(entry.vertices, (int) Math.floor(pos.x), (int) Math.floor(pos.z))) {
                counts.merge(teamId, 1, Integer::sum);
            }
        }
        return counts;
    }

    @Nullable
    private static String leader(Map<String, Integer> counts, int minAdvantage) {
        if (counts.isEmpty()) return null;
        String bestTeam = null;
        int bestCount = 0;
        int secondCount = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                secondCount = bestCount;
                bestCount = e.getValue();
                bestTeam = e.getKey();
            } else if (e.getValue() > secondCount) {
                secondCount = e.getValue();
            }
        }
        if (bestTeam == null || bestCount == 0) return null;
        if (bestCount - secondCount < minAdvantage) return null;
        return bestTeam;
    }

    private static boolean applyTick(CapturePointSavedData.Entry entry, String leader, boolean contested,
                                     boolean contestedFreeze, int interval, int decayTicks, int requiredTicks) {
        boolean ownerPresent = leader != null && leader.equals(entry.ownerTeamId);

        if (leader == null) {
            if (contested && contestedFreeze) return false;
            if (entry.progressTicks > 0) {
                entry.progressTicks = Math.max(0, entry.progressTicks - decayTicks);
                if (entry.progressTicks == 0) entry.captureTeamId = "";
                return true;
            }
            if (!entry.captureTeamId.isEmpty()) {
                entry.captureTeamId = "";
                return true;
            }
            return false;
        }

        if (ownerPresent) {
            if (entry.progressTicks != requiredTicks || !entry.captureTeamId.isEmpty()) {
                entry.progressTicks = requiredTicks;
                entry.captureTeamId = "";
                return true;
            }
            return false;
        }

        entry.captureTeamId = leader;

        if (entry.ownerTeamId.isEmpty()) {
            entry.progressTicks = Math.min(requiredTicks, entry.progressTicks + interval);
            return true;
        }

        entry.progressTicks = Math.max(0, entry.progressTicks - interval);
        if (entry.progressTicks == 0) {
            entry.ownerTeamId = "";
            entry.progressTicks = Math.min(requiredTicks, entry.progressTicks + interval);
        }
        return true;
    }

    private static void handleCaptureComplete(MinecraftServer server, CapturePointSavedData.Entry entry, int requiredTicks) {
        if (entry.ownerTeamId.isEmpty() && entry.progressTicks >= requiredTicks && !entry.captureTeamId.isEmpty()) {
            entry.ownerTeamId = entry.captureTeamId;
            entry.captureTeamId = "";
            entry.progressTicks = requiredTicks;
            String teamName = Teams.displayName(server, entry.ownerTeamId);
            int color = Teams.color(server, entry.ownerTeamId);
            Component title = Component.literal("Точка захвата");
            Component subtitle = Component.literal(entry.displayName + " → " + teamName);
            PjmNetworking.sendToAll(server, new NotificationPacket(title, subtitle, color, 4000));
            RankService.rewardCapturePoint(server, entry.id, entry.dimension, entry.vertices, entry.ownerTeamId);
        }
    }

    private static void sendHud(MinecraftServer server, ServerPlayer player, CapturePointSavedData data, int requiredTicks) {
        String dim = player.serverLevel().dimension().location().toString();
        Vec3 pos = player.position();
        int blockX = (int) Math.floor(pos.x);
        int blockZ = (int) Math.floor(pos.z);
        CapturePointSavedData.Entry inside = null;
        for (CapturePointSavedData.Entry entry : data.entries()) {
            if (!entry.dimension.equals(dim) || entry.vertices.size() < 3) continue;
            if (CapturePoint.contains(entry.vertices, blockX, blockZ)) {
                inside = entry;
                break;
            }
        }
        if (inside == null) {
            PjmNetworking.sendToPlayer(player, CapturePointHudPacket.empty());
            return;
        }
        boolean neutralizing = !inside.ownerTeamId.isEmpty() && !inside.captureTeamId.isEmpty();
        boolean capturing = inside.ownerTeamId.isEmpty() && !inside.captureTeamId.isEmpty();
        int percent = requiredTicks <= 0 ? (inside.ownerTeamId.isEmpty() ? 0 : 100)
                : Math.max(0, Math.min(100, inside.progressTicks * 100 / requiredTicks));
        PjmNetworking.sendToPlayer(player, new CapturePointHudPacket(
                inside.id, inside.displayName,
                Teams.displayName(server, inside.ownerTeamId), Teams.color(server, inside.ownerTeamId),
                Teams.displayName(server, inside.captureTeamId), Teams.color(server, inside.captureTeamId),
                percent, neutralizing, capturing));
    }

    /** Обработка действия редактора точек захвата (C→S пакет, OP-only). */
    public static void handleEditorAction(CapturePointEditorActionPacket packet, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CapturePointSavedData data = CapturePointSavedData.get(server);
        switch (packet.action()) {
            case ADD -> data.addPoint(packet.pointId(), packet.displayName(), packet.dimension());
            case REMOVE -> data.removePoint(packet.pointId());
            case UPDATE_VERTICES -> data.updateVertices(packet.pointId(), packet.vertices());
            case UPDATE_DISPLAY_NAME -> data.updateDisplayName(packet.pointId(), packet.displayName());
            case SET_OWNER -> data.setOwner(packet.pointId(), packet.ownerTeamId());
        }
        broadcastMapSync(server, data, "editor_action");
    }
}
