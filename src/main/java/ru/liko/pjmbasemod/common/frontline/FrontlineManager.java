package ru.liko.pjmbasemod.common.frontline;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.frontline.bluemap.FrontlineBlueMapService;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.FrontlineHudPacket;
import ru.liko.pjmbasemod.common.network.packet.FrontlineMapSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;
import ru.liko.pjmbasemod.common.rank.RankService;
import ru.liko.pjmbasemod.common.region.Region;
import ru.liko.pjmbasemod.common.region.RegionSavedData;

import javax.annotation.Nullable;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FrontlineManager {

    private static int tickCounter;
    private static volatile long mapSyncRevision;
    private static volatile long lastMapSyncAtMs;
    private static volatile String lastMapSyncReason = "startup";
    private static volatile String lastSectorSyncSignature = "";
    private static volatile List<FrontlineMapSyncPacket.SectorEntry> lastLiveSectorEntries = List.of();

    private FrontlineManager() {}

    public static void onServerTick(MinecraftServer server) {
        if (server == null || !Config.isFrontlineEnabled()) return;

        int interval = Math.max(1, Config.getFrontlineTickIntervalTicks());
        tickCounter++;
        if (tickCounter % interval != 0) return;

        FrontlineSavedData data = FrontlineSavedData.get(server);
        RegionSavedData regions = RegionSavedData.get(server);
        boolean cleanupChanged = data.removeStateOutsideRegions(regions);
        boolean captureActive = isCaptureActive(server, data);
        Map<FrontlineSectorKey, SectorPresence> presences = collectPresences(server, regions);

        boolean changed = cleanupChanged | processCaptures(server, data, presences, captureActive, interval);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendHud(player, data, regions, presences, captureActive);
        }
        String sectorSignature = sectorSyncSignature(server, data, regions, presences, captureActive);
        boolean sectorVisualChanged = !sectorSignature.equals(lastSectorSyncSignature);
        if (changed || sectorVisualChanged) {
            lastSectorSyncSignature = sectorSignature;
            broadcastMapSync(server, data, changed ? "frontline_data_changed" : "frontline_sector_state_changed", regions, presences, captureActive);
        }
    }

    public static boolean isCaptureActive(MinecraftServer server, FrontlineSavedData data) {
        if (!Config.isFrontlineEnabled() || !data.isManualActive()) return false;
        if (!Config.useFrontlineRealTimeWindow()) return true;

        int start = Config.parseTimeToMinute(Config.getFrontlineRealTimeStart());
        int end = Config.parseTimeToMinute(Config.getFrontlineRealTimeEnd());
        if (start < 0 || end < 0) return true;

        LocalTime now = LocalTime.now(frontlineZone());
        int current = now.getHour() * 60 + now.getMinute();
        if (start <= end) return current >= start && current <= end;
        return current >= start || current <= end;
    }

    public static void sendInitialSync(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        FrontlineSavedData data = FrontlineSavedData.get(player.getServer());
        RegionSavedData regions = RegionSavedData.get(player.getServer());
        PjmNetworking.sendToPlayer(player, createMapSync(player.getServer(), data, regions));
        sendHud(player, data, regions, Map.of(), isCaptureActive(player.getServer(), data));
    }

    public static void broadcastMapSync(MinecraftServer server, FrontlineSavedData data) {
        broadcastMapSync(server, data, "frontline_data_changed");
    }

    public static void broadcastMapSync(MinecraftServer server, FrontlineSavedData data, String reason) {
        FrontlineMapSyncPacket payload = createMapSync(server, data);
        broadcastMapSync(server, payload, reason);
    }

    private static void broadcastMapSync(MinecraftServer server, FrontlineSavedData data, String reason, RegionSavedData regions, Map<FrontlineSectorKey, SectorPresence> presences, boolean captureActive) {
        FrontlineMapSyncPacket payload = createMapSync(server, data, regions, presences, captureActive);
        broadcastMapSync(server, payload, reason);
    }

    private static void broadcastMapSync(MinecraftServer server, FrontlineMapSyncPacket payload, String reason) {
        PjmNetworking.sendToAll(server, payload);
        mapSyncRevision++;
        lastMapSyncAtMs = System.currentTimeMillis();
        lastMapSyncReason = (reason == null || reason.isBlank()) ? "unknown" : reason;
        FrontlineBlueMapService.requestSync(lastMapSyncReason);
    }

    public static MapSyncStatus mapSyncStatus() {
        return new MapSyncStatus(mapSyncRevision, lastMapSyncAtMs, lastMapSyncReason);
    }

    public static boolean cleanupInvalidState(MinecraftServer server, String reason) {
        if (server == null) return false;
        FrontlineSavedData data = FrontlineSavedData.get(server);
        boolean changed = data.removeStateOutsideRegions(RegionSavedData.get(server));
        if (changed) {
            broadcastMapSync(server, data, reason == null || reason.isBlank() ? "region_cleanup" : reason);
        }
        return changed;
    }

    public static FrontlineMapSyncPacket createMapSync(MinecraftServer server, FrontlineSavedData data) {
        return createMapSync(server, data, RegionSavedData.get(server));
    }

    public static FrontlineMapSyncPacket createMapSync(MinecraftServer server, FrontlineSavedData data, RegionSavedData regions) {
        return createMapSync(server, data, regions, Map.of(), isCaptureActive(server, data));
    }

    public static FrontlineMapSyncPacket createMapSync(MinecraftServer server, FrontlineSavedData data, RegionSavedData regions, Map<FrontlineSectorKey, SectorPresence> presences, boolean captureActive) {
        List<FrontlineMapSyncPacket.ChunkEntry> chunks = new ArrayList<>();
        for (FrontlineChunkState state : data.chunks()) {
            FrontlineChunkKey key = state.key();
            chunks.add(new FrontlineMapSyncPacket.ChunkEntry(
                    key.dimension(), key.x(), key.z(),
                    state.ownerTeamId(),
                    FrontlineTeams.displayName(server, state.ownerTeamId()), FrontlineTeams.color(server, state.ownerTeamId())));
        }

        List<FrontlineMapSyncPacket.SectorEntry> sectors = createSectorEntries(server, data, regions, presences, captureActive);
        return new FrontlineMapSyncPacket(List.copyOf(chunks), List.copyOf(sectors));
    }

    public static List<FrontlineMapSyncPacket.SectorEntry> createSectorEntries(MinecraftServer server, FrontlineSavedData data) {
        return createSectorEntries(server, data, RegionSavedData.get(server));
    }

    public static List<FrontlineMapSyncPacket.SectorEntry> createSectorEntries(MinecraftServer server, FrontlineSavedData data, RegionSavedData regions) {
        return createSectorEntries(server, data, regions, Map.of(), isCaptureActive(server, data));
    }

    public static List<FrontlineMapSyncPacket.SectorEntry> currentSectorEntries(MinecraftServer server, FrontlineSavedData data) {
        return createSectorEntries(server, data);
    }

    public static List<FrontlineMapSyncPacket.SectorEntry> currentSectorEntries(MinecraftServer server, FrontlineSavedData data, RegionSavedData regions) {
        return createSectorEntries(server, data, regions);
    }

    private static List<FrontlineMapSyncPacket.SectorEntry> createSectorEntries(MinecraftServer server, FrontlineSavedData data, RegionSavedData regions, Map<FrontlineSectorKey, SectorPresence> presences, boolean captureActive) {
        Map<FrontlineSectorKey, FrontlineMapSyncPacket.SectorEntry> entries = new LinkedHashMap<>();
        int requiredTicks = requiredCaptureTicks();

        for (FrontlineSectorState state : data.sectors()) {
            if (!state.hasProgress()) continue;
            FrontlineSectorKey key = state.key();
            Region region = regions.region(key.regionName());
            if (region == null || !region.isFrontline() || !region.isComplete()) continue;
            entries.put(key, sectorEntry(server, region, key,
                    FrontlineTeams.displayName(server, state.captureTeamId()),
                    FrontlineTeams.color(server, state.captureTeamId()),
                    percent(state.progressTicks(), requiredTicks),
                    false));
        }

        if (captureActive) {
            for (Map.Entry<FrontlineSectorKey, SectorPresence> presenceEntry : presences.entrySet()) {
                CaptureLeader leader = leader(presenceEntry.getValue().counts());
                if (!leader.tied() || presenceEntry.getValue().counts().size() <= 1) continue;
                FrontlineSectorKey key = presenceEntry.getKey();
                Region region = presenceEntry.getValue().region();
                FrontlineSectorState state = data.sector(key);
                int progress = state == null ? 0 : percent(state.progressTicks(), requiredTicks);
                entries.put(key, sectorEntry(server, region, key, "Оспаривается", 0xFFC13D, progress, true));
            }
        }

        return List.copyOf(entries.values());
    }

    private static FrontlineMapSyncPacket.SectorEntry sectorEntry(MinecraftServer server, Region region, FrontlineSectorKey key, String teamName, int teamColor, int progressPercent, boolean contested) {
        int minX = Math.max(region.minX(), key.minChunkX());
        int minZ = Math.max(region.minZ(), key.minChunkZ());
        int maxX = Math.min(region.maxX(), key.maxChunkX());
        int maxZ = Math.min(region.maxZ(), key.maxChunkZ());
        return new FrontlineMapSyncPacket.SectorEntry(
                region.dimension(), region.name(), key.x(), key.z(), minX, minZ, maxX, maxZ,
                teamName, teamColor, Math.max(0, Math.min(100, progressPercent)), contested);
    }

    public static void sendHud(ServerPlayer player, FrontlineSavedData data, RegionSavedData regions, Map<FrontlineSectorKey, SectorPresence> presences, boolean captureActive) {
        String dimension = dimensionId(player);
        ChunkPos pos = player.chunkPosition();
        FrontlineChunkKey key = FrontlineChunkKey.of(dimension, pos);
        Region region = regions.findFrontlineRegion(dimension, pos);
        FrontlineSectorKey sectorKey = region == null ? null : FrontlineSectorKey.of(region, pos);
        FrontlineChunkState state = data.chunk(key);
        FrontlineSectorState sectorState = sectorKey == null ? null : data.sector(sectorKey);
        String owner = state == null ? FrontlineTeams.NEUTRAL_ID : state.ownerTeamId();
        String captureTeam = sectorState == null ? FrontlineTeams.NEUTRAL_ID : sectorState.captureTeamId();
        int requiredTicks = requiredCaptureTicks();
        int progress = sectorState == null ? 0 : percent(sectorState.progressTicks(), requiredTicks);
        int secondsRemaining = sectorState == null || sectorState.progressTicks() <= 0
                ? 0
                : Math.max(0, (requiredTicks - sectorState.progressTicks() + 19) / 20);
        boolean playerCanCapture = canParticipateInCapture(player);

        SectorPresence presence = sectorKey == null ? null : presences.get(sectorKey);
        Map<String, Integer> counts = presence == null ? Map.of() : presence.counts();
        String playerTeam = FrontlineTeams.resolvePlayerTeamId(player);
        String status = statusText(player.getServer(), data, region, sectorKey, owner, captureTeam, counts, captureActive, playerTeam, playerCanCapture);

        PjmNetworking.sendToPlayer(player, new FrontlineHudPacket(
                true,
                region != null,
                captureActive,
                region == null ? "" : region.displayName(),
                sectorKey == null ? Math.floorDiv(pos.x, FrontlineSectorKey.SIZE_CHUNKS) : sectorKey.x(),
                sectorKey == null ? Math.floorDiv(pos.z, FrontlineSectorKey.SIZE_CHUNKS) : sectorKey.z(),
                FrontlineTeams.displayName(player.getServer(), owner),
                FrontlineTeams.color(player.getServer(), owner),
                FrontlineTeams.displayName(player.getServer(), captureTeam),
                FrontlineTeams.color(player.getServer(), captureTeam),
                status,
                progress,
                secondsRemaining,
                countsText(player.getServer(), counts)
        ));
    }

    public static boolean canAttack(FrontlineSavedData data, Region region, FrontlineChunkKey key, String attackingTeam) {
        if (attackingTeam == null || attackingTeam.isBlank() || region == null) return false;
        if (!Config.isFrontlineRequireAdjacentOwner()) return true;

        String owner = data.ownerOf(key);
        if (owner.isBlank() && Config.isFrontlineAllowNeutralOpening()) return true;
        if (attackingTeam.equals(owner)) return false;
        if (!data.teamOwnsAnyChunkInRegion(region, attackingTeam) && owner.isBlank()) return true;
        return data.hasAdjacentOwner(region, key, attackingTeam);
    }

    public static boolean canAttackSector(FrontlineSavedData data, Region region, FrontlineSectorKey sectorKey, String attackingTeam) {
        if (!FrontlineTeams.isCombatTeam(attackingTeam) || region == null || sectorKey == null) return false;
        if (data.teamOwnsWholeSector(region, sectorKey, attackingTeam)) return false;
        if (!Config.isFrontlineRequireAdjacentOwner()) return true;

        boolean hasGrayZone = data.sectorHasGrayZone(region, sectorKey);
        boolean hasNeutral = data.sectorHasNeutralOwner(region, sectorKey);
        if (!hasGrayZone && hasNeutral && Config.isFrontlineAllowNeutralOpening()) return true;
        if (!data.teamOwnsAnyChunkInRegion(region, attackingTeam) && !hasGrayZone && hasNeutral) return true;
        return data.hasAdjacentOwner(region, sectorKey, attackingTeam);
    }

    private static Map<FrontlineSectorKey, SectorPresence> collectPresences(MinecraftServer server, RegionSavedData regions) {
        Map<FrontlineSectorKey, SectorPresence> presences = new LinkedHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!canParticipateInCapture(player)) continue;

            String team = FrontlineTeams.resolvePlayerTeamId(player);
            if (team == null || team.isBlank()) continue;

            String dimension = dimensionId(player);
            ChunkPos pos = player.chunkPosition();
            Region region = regions.findFrontlineRegion(dimension, pos);
            if (region == null) continue;

            FrontlineSectorKey key = FrontlineSectorKey.of(region, pos);
            SectorPresence presence = presences.computeIfAbsent(key, ignored -> new SectorPresence(region, new HashMap<>()));
            presence.counts().merge(team, 1, Integer::sum);
        }
        return presences;
    }

    private static boolean canParticipateInCapture(ServerPlayer player) {
        return player != null && !player.isSpectator() && !player.isCreative();
    }

    private static boolean processCaptures(MinecraftServer server, FrontlineSavedData data, Map<FrontlineSectorKey, SectorPresence> presences, boolean captureActive, int intervalTicks) {
        boolean changed = false;
        int requiredTicks = requiredCaptureTicks();
        int decayTicks = Math.max(20, Config.getFrontlineDecayTimeSeconds() * 20);

        for (Map.Entry<FrontlineSectorKey, SectorPresence> entry : presences.entrySet()) {
            FrontlineSectorKey key = entry.getKey();
            SectorPresence presence = entry.getValue();
            FrontlineSectorState state = data.getOrCreateSector(key);
            CaptureLeader leader = leader(presence.counts());

            if (!captureActive || leader.teamId() == null || leader.advantage() < Config.getFrontlineMinAdvantage()) {
                changed |= decayOrFreeze(data, state, requiredTicks, decayTicks, intervalTicks, !captureActive || !Config.isFrontlineContestedFreeze());
                continue;
            }

            if (data.teamOwnsWholeSector(presence.region(), key, leader.teamId())) {
                if (state.hasProgress()) {
                    state.clearCapture();
                    data.setDirty();
                    changed = true;
                }
                continue;
            }

            if (!canAttackSector(data, presence.region(), key, leader.teamId())) {
                changed |= decayOrFreeze(data, state, requiredTicks, decayTicks, intervalTicks, true);
                continue;
            }

            if (!leader.teamId().equals(state.captureTeamId())) {
                if (state.progressTicks() > 0) {
                    state.setProgressTicks(state.progressTicks() - intervalTicks * Math.max(1, leader.advantage()));
                    data.setDirty();
                    continue;
                }
                state.setCaptureTeamId(leader.teamId());
                changed = true;
            }

            int nextProgress = state.progressTicks() + intervalTicks * Math.max(1, leader.advantage());
            if (nextProgress >= requiredTicks) {
                List<FrontlineChunkKey> changedChunks = new ArrayList<>(data.setSectorOwnerRaw(presence.region(), key, leader.teamId()));
                int grayChanged = data.rebuildGrayZones(presence.region(), changedChunks);
                int rankRewards = RankService.rewardSectorCapture(server, presence.region(), key, leader.teamId());
                changed = true;
                announceCapture(server, presence.region(), key, leader.teamId(), changedChunks.size(), grayChanged, rankRewards);
                continue;
            }

            if (nextProgress != state.progressTicks()) {
                state.setProgressTicks(nextProgress);
                data.setDirty();
            }
        }

        for (FrontlineSectorState state : List.copyOf(data.sectors())) {
            if (presences.containsKey(state.key())) continue;
            changed |= decayOrFreeze(data, state, requiredTicks, decayTicks, intervalTicks, true);
        }
        return changed;
    }

    private static boolean decayOrFreeze(FrontlineSavedData data, FrontlineSectorState state, int requiredTicks, int decayTicks, int intervalTicks, boolean shouldDecay) {
        if (!state.hasProgress()) return false;
        if (!shouldDecay) return false;

        int decayAmount = Math.max(1, (int) Math.ceil(requiredTicks * (intervalTicks / (double) decayTicks)));
        int next = Math.max(0, state.progressTicks() - decayAmount);
        if (next == state.progressTicks()) return false;
        state.setProgressTicks(next);
        if (next <= 0) state.setCaptureTeamId(FrontlineTeams.NEUTRAL_ID);
        data.setDirty();
        return next <= 0;
    }

    private static void announceCapture(MinecraftServer server, Region region, FrontlineSectorKey key, String teamId, int chunkCount, int grayChanged, int rankRewards) {
        String teamName = FrontlineTeams.displayName(server, teamId);
        String sector = key.x() + ", " + key.z();
        Component title = Component.literal("Линия фронта");
        Component subtitle = Component.literal(teamName + " захватили сектор №" + sector + " в регионе " + region.displayName());
        PjmNetworking.sendToAll(server, new NotificationPacket(title, subtitle, FrontlineTeams.color(server, teamId), 3500L));
        Pjmbasemod.LOGGER.info("[FRONTLINE] {} captured sector {} in region {} (chunks={}, grayChanged={}, rankRewards={})", teamId, sector, region.name(), chunkCount, grayChanged, rankRewards);
    }

    private static CaptureLeader leader(Map<String, Integer> counts) {
        if (counts.isEmpty()) return new CaptureLeader(null, 0, 0, true);
        List<Map.Entry<String, Integer>> entries = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .toList();
        Map.Entry<String, Integer> first = entries.getFirst();
        int second = entries.size() > 1 ? entries.get(1).getValue() : 0;
        boolean tied = entries.size() > 1 && first.getValue().equals(entries.get(1).getValue());
        return new CaptureLeader(tied ? null : first.getKey(), first.getValue(), first.getValue() - second, tied);
    }

    private static String statusText(MinecraftServer server, FrontlineSavedData data, @Nullable Region region, @Nullable FrontlineSectorKey sectorKey, String owner, String captureTeam, Map<String, Integer> counts, boolean captureActive, @Nullable String playerTeam, boolean playerCanCapture) {
        if (region == null) return "Вне зоны захвата";
        if (!Config.isFrontlineEnabled()) return "Линия фронта отключена";
        if (!data.isManualActive()) return "Захват закрыт админом";
        if (!captureActive) return "Захват закрыт по времени";
        if (!playerCanCapture) return "Режим без захвата";
        if (playerTeam == null || playerTeam.isBlank()) return "Нет фракции";

        CaptureLeader leader = leader(counts);
        if (leader.tied() && counts.size() > 1) return "Оспаривается";
        if (!captureTeam.isBlank()) return "Захват: " + FrontlineTeams.displayName(server, captureTeam);
        if (playerTeam.equals(owner)) return "Ваша территория";
        if (FrontlineTeams.GRAY_ZONE_ID.equals(owner)) return "Серая Зона";
        if (sectorKey != null && !canAttackSector(data, region, sectorKey, playerTeam) && !playerTeam.equals(owner)) return "Нет связи с фронтом";
        if (owner.isBlank()) return "Нейтральный сектор";
        return "Территория: " + FrontlineTeams.displayName(server, owner);
    }

    private static String countsText(MinecraftServer server, Map<String, Integer> counts) {
        if (counts.isEmpty()) return "Игроков в секторе нет";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            parts.add(FrontlineTeams.displayName(server, entry.getKey()) + ": " + entry.getValue());
        }
        return String.join("  |  ", parts);
    }

    private static String sectorSyncSignature(MinecraftServer server, FrontlineSavedData data, RegionSavedData regions, Map<FrontlineSectorKey, SectorPresence> presences, boolean captureActive) {
        StringBuilder sb = new StringBuilder();
        List<FrontlineMapSyncPacket.SectorEntry> entries = createSectorEntries(server, data, regions, presences, captureActive);
        lastLiveSectorEntries = entries;
        for (FrontlineMapSyncPacket.SectorEntry entry : entries) {
            sb.append(entry.dimension()).append('|')
                    .append(entry.regionName()).append('|')
                    .append(entry.sectorX()).append(':').append(entry.sectorZ()).append('|')
                    .append(entry.teamName()).append('|')
                    .append(entry.teamColor()).append('|')
                    .append(entry.contested()).append(';');
        }
        return sb.toString();
    }

    private static int requiredCaptureTicks() {
        return Math.max(1, Config.getFrontlineCaptureTimeSeconds() * 20);
    }

    private static int percent(int progressTicks, int requiredTicks) {
        if (requiredTicks <= 0) return 0;
        if (progressTicks <= 0) return 0;
        if (progressTicks >= requiredTicks) return 100;
        return Math.max(0, Math.min(99, (int) (progressTicks * 100L / requiredTicks)));
    }

    private static String dimensionId(ServerPlayer player) {
        return player.serverLevel().dimension().location().toString();
    }

    private static ZoneId frontlineZone() {
        try {
            return ZoneId.of(Config.getFrontlineRealTimeZone());
        } catch (DateTimeException ignored) {
            return ZoneId.systemDefault();
        }
    }

    public record SectorPresence(Region region, Map<String, Integer> counts) {}
    public record MapSyncStatus(long revision, long lastBroadcastAtMs, String lastReason) {}
    private record CaptureLeader(@Nullable String teamId, int count, int advantage, boolean tied) {}
}
