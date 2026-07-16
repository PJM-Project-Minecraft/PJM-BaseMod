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
import ru.liko.pjmbasemod.common.warehouse.WarehousePoolCategory;
import ru.liko.pjmbasemod.common.warehouse.WarehouseSavedData;

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

    /** Серверных тиков в минуте — период начисления пассивного дохода складам. */
    private static final int MINUTE_TICKS = 20 * 60;

    private static int tickCounter;
    private static int incomeTickCounter;
    private static volatile long mapSyncRevision;
    private static volatile long lastMapSyncAtMs;
    private static volatile String lastMapSyncReason = "startup";

    private CapturePointManager() {}

    public static void onServerTick(MinecraftServer server) {
        if (server == null) return;
        evaluateSchedule(server);
        if (!Config.isCapturePointsEnabled()) return;
        tickIncome(server);
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
            // Последовательный захват: атакующий (не владелец) может брать точку,
            // только владея order-соседней точкой на линии фронта. Иначе — гейт.
            if (leader != null && !leader.equals(entry.ownerTeamId)
                    && Config.isCapturePointSequential() && !canAttack(data, entry, leader)) {
                leader = null;
            }
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

    /**
     * Пассивный доход складов с удерживаемых точек: раз в минуту каждая команда получает
     * {@code incomePerPointPerMinute} очков пула {@code SUPPLY} за каждую свою точку.
     * Склад команды берётся из карты {@code capturePoints.warehouseByTeam} (teamId → warehouseId);
     * команда без записи в карте дохода не получает. Начисляется только при активном захвате
     * (вызов стоит после гейта {@code enabled}) — в межсезонье склады не капают.
     */
    private static void tickIncome(MinecraftServer server) {
        if (!Config.isCapturePointIncomeEnabled()) return;
        int perPoint = Config.getCapturePointIncomePerPointPerMinute();
        if (perPoint <= 0) return;
        Map<String, String> warehouseByTeam = Config.getCapturePointWarehouseByTeam();
        if (warehouseByTeam.isEmpty()) return;
        if (++incomeTickCounter < MINUTE_TICKS) return;
        incomeTickCounter = 0;

        Map<String, Integer> pointsOwned = new LinkedHashMap<>();
        for (CapturePointSavedData.Entry entry : CapturePointSavedData.get(server).entries()) {
            if (!entry.ownerTeamId.isEmpty()) pointsOwned.merge(entry.ownerTeamId, 1, Integer::sum);
        }
        if (pointsOwned.isEmpty()) return;

        WarehouseSavedData stock = WarehouseSavedData.get(server);
        for (Map.Entry<String, Integer> owned : pointsOwned.entrySet()) {
            String warehouseId = warehouseByTeam.get(owned.getKey());
            if (warehouseId == null || warehouseId.isBlank()) continue;
            // addPoints создаёт склад, если его ещё нет — как и приёмка ящиков (WarehouseManager.depositCrate).
            stock.addPoints(warehouseId, WarehousePoolCategory.SUPPLY, owned.getValue() * perPoint);
        }
    }

    /**
     * Автопереключение {@code capturePoints.enabled} по расписанию (час:минута, серверное
     * локальное время). Реагирует только на смену состояния окна — ручной {@code /pjm
     * capturepoint enable|disable} не перебивается до следующей границы окна.
     */
    private static void evaluateSchedule(MinecraftServer server) {
        if (!Config.isCapturePointScheduleEnabled()) return;
        List<Config.ScheduleWindow> windows = Config.getCapturePointScheduleWindows();
        if (windows.isEmpty()) return;
        java.time.LocalTime now = java.time.LocalTime.now();
        int nowMinutes = now.getHour() * 60 + now.getMinute();
        boolean shouldBeActive = false;
        for (Config.ScheduleWindow w : windows) {
            if (withinScheduleWindow(nowMinutes, w.startTotalMinutes(), w.endTotalMinutes())) {
                shouldBeActive = true;
                break;
            }
        }
        // Состояние окна хранится в конфиге, а не в памяти: иначе рестарт сервера внутри
        // окна сбрасывал бы его и расписание перебивало бы ручной enable/disable заново.
        Boolean previous = Config.getCapturePointScheduleLastState();
        if (previous != null && previous == shouldBeActive) return;
        Config.setCapturePointScheduleLastState(shouldBeActive);
        if (Config.isCapturePointsEnabled() == shouldBeActive) return;

        setEnabled(server, shouldBeActive);
        Component title = Component.literal("Точки захвата");
        Component subtitle = Component.literal(shouldBeActive ? "Захват включён по расписанию" : "Захват выключен по расписанию");
        PjmNetworking.sendToAll(server, new NotificationPacket(title, subtitle, shouldBeActive ? 0x4CAF50 : 0x9B9B9B, 4000));
    }

    /** @param nowMinutes, startMinutes, endMinutes — минуты от полуночи (0-1439). */
    private static boolean withinScheduleWindow(int nowMinutes, int startMinutes, int endMinutes) {
        if (startMinutes == endMinutes) return true;
        if (startMinutes < endMinutes) return nowMinutes >= startMinutes && nowMinutes < endMinutes;
        return nowMinutes >= startMinutes || nowMinutes < endMinutes;
    }

    /**
     * Вкл/выкл всей подсистемы захвата (команда или расписание). При выключении разом
     * очищает HUD у всех игроков — иначе тик-цикл встаёт и последний HUD-пакет
     * «зависает» у игрока, стоявшего в точке (см. {@link #onServerTick}). Также сразу
     * пушит карту: при выключении — пустую (иначе оверлей JourneyMap держит устаревшие
     * точки), при включении — актуальные точки (иначе они появятся лишь на первом
     * изменившемся тике).
     */
    public static void setEnabled(MinecraftServer server, boolean enabled) {
        if (Config.isCapturePointsEnabled() == enabled) return;
        Config.setCapturePointsEnabled(enabled);
        if (server == null) return;
        if (enabled) {
            broadcastMapSync(server, CapturePointSavedData.get(server), "enabled");
        } else {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PjmNetworking.sendToPlayer(player, CapturePointHudPacket.empty());
            }
            PjmNetworking.sendToAll(server, new CapturePointMapSyncPacket(List.of()));
            mapSyncRevision++;
            lastMapSyncAtMs = System.currentTimeMillis();
            lastMapSyncReason = "disabled";
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
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) continue;
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

    /**
     * Гейт последовательного захвата: команда может брать {@code target}, только если владеет
     * непосредственно order-соседней точкой (prev или next) в том же измерении. Концы линии
     * (базы) заранее назначаются через {@code /pjm capturepoint setowner} — так цепочка стартует.
     * Если у точки нет строгих соседей по order (все order равны, напр. неразмеченные) — гейт
     * не применяется (поведение как у обычного KoTH).
     * ponytail: naive O(n) на точку, O(n²) на тик — точек единицы, не оптимизирую.
     */
    private static boolean canAttack(CapturePointSavedData data, CapturePointSavedData.Entry target, String team) {
        CapturePointSavedData.Entry prev = null, next = null;
        for (CapturePointSavedData.Entry e : data.entries()) {
            if (e == target || !e.dimension.equals(target.dimension)) continue;
            if (e.order < target.order && (prev == null || e.order > prev.order)) prev = e;
            if (e.order > target.order && (next == null || e.order < next.order)) next = e;
        }
        if (prev == null && next == null) return true;
        return (prev != null && team.equals(prev.ownerTeamId))
                || (next != null && team.equals(next.ownerTeamId));
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
            entry.ownerColor = 0x9B9B9B;
            entry.progressTicks = Math.min(requiredTicks, entry.progressTicks + interval);
        }
        return true;
    }

    private static void handleCaptureComplete(MinecraftServer server, CapturePointSavedData.Entry entry, int requiredTicks) {
        if (entry.ownerTeamId.isEmpty() && entry.progressTicks >= requiredTicks && !entry.captureTeamId.isEmpty()) {
            entry.ownerTeamId = entry.captureTeamId;
            entry.ownerColor = Teams.color(server, entry.ownerTeamId);
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
        if (player.isSpectator() || player.isCreative()) {
            PjmNetworking.sendToPlayer(player, CapturePointHudPacket.empty());
            return;
        }
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
