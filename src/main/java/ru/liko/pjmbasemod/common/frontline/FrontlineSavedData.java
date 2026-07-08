package ru.liko.pjmbasemod.common.frontline;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.region.Region;
import ru.liko.pjmbasemod.common.region.RegionSavedData;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class FrontlineSavedData extends SavedData {

    private static final int[][] NEIGHBOR_DELTAS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static final String DATA_NAME = "pjmbasemod_frontline_state";
    private static final SavedData.Factory<FrontlineSavedData> FACTORY = new SavedData.Factory<>(
            FrontlineSavedData::new,
            FrontlineSavedData::load
    );

    private final Map<FrontlineChunkKey, FrontlineChunkState> chunks = new LinkedHashMap<>();
    private final Map<FrontlineSectorKey, FrontlineSectorState> sectors = new LinkedHashMap<>();
    private boolean manualActive = Config.isFrontlineManualActive();

    public static FrontlineSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static FrontlineSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FrontlineSavedData data = new FrontlineSavedData();
        data.manualActive = tag.contains("manualActive") ? tag.getBoolean("manualActive") : Config.isFrontlineManualActive();

        ListTag chunkTags = tag.getList("chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < chunkTags.size(); i++) {
            FrontlineChunkState state = FrontlineChunkState.load(chunkTags.getCompound(i));
            data.chunks.put(state.key(), state);
        }

        ListTag sectorTags = tag.getList("sectors", Tag.TAG_COMPOUND);
        for (int i = 0; i < sectorTags.size(); i++) {
            FrontlineSectorState state = FrontlineSectorState.load(sectorTags.getCompound(i));
            data.sectors.put(state.key(), state);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("manualActive", manualActive);

        ListTag chunkTags = new ListTag();
        for (FrontlineChunkState state : chunks.values()) {
            chunkTags.add(state.save());
        }
        tag.put("chunks", chunkTags);

        ListTag sectorTags = new ListTag();
        for (FrontlineSectorState state : sectors.values()) {
            sectorTags.add(state.save());
        }
        tag.put("sectors", sectorTags);
        return tag;
    }

    /** Сброс всех захватов: чанки и сектора возвращаются в нейтраль. */
    public void clearAll() {
        if (!chunks.isEmpty() || !sectors.isEmpty()) {
            chunks.clear();
            sectors.clear();
            setDirty();
        }
    }

    public boolean isManualActive() {
        return manualActive;
    }

    public void setManualActive(boolean manualActive) {
        this.manualActive = manualActive;
        setDirty();
    }

    public Collection<FrontlineChunkState> chunks() {
        return chunks.values();
    }

    public Collection<FrontlineSectorState> sectors() {
        return sectors.values();
    }

    public FrontlineChunkState getOrCreateChunk(FrontlineChunkKey key) {
        FrontlineChunkState state = chunks.get(key);
        if (state != null) return state;
        state = new FrontlineChunkState(key);
        chunks.put(key, state);
        setDirty();
        return state;
    }

    public FrontlineSectorState getOrCreateSector(FrontlineSectorKey key) {
        FrontlineSectorState state = sectors.get(key);
        if (state != null) return state;
        state = new FrontlineSectorState(key);
        sectors.put(key, state);
        setDirty();
        return state;
    }

    @Nullable
    public FrontlineChunkState chunk(FrontlineChunkKey key) {
        return chunks.get(key);
    }

    @Nullable
    public FrontlineSectorState sector(FrontlineSectorKey key) {
        return sectors.get(key);
    }

    public String ownerOf(FrontlineChunkKey key) {
        FrontlineChunkState state = chunks.get(key);
        return state == null ? FrontlineTeams.NEUTRAL_ID : state.ownerTeamId();
    }

    public boolean removeStateOutsideRegions(RegionSavedData regions) {
        // Захват сохраняем, пока чанк/сектор ещё покрыт существующим регионом по границам —
        // независимо от флага isFrontline(). Выключение frontline лишь ставит захват на паузу,
        // прогресс не должен стираться и восстанавливается при повторном включении.
        // Реально чистим только осиротевшее состояние: регион удалён или границы больше не покрывают чанк.
        boolean changed = chunks.entrySet().removeIf(entry -> {
            FrontlineChunkKey key = entry.getKey();
            return regions.findRegion(key.dimension(), key.x(), key.z()) == null;
        });
        changed |= sectors.entrySet().removeIf(entry -> {
            FrontlineSectorKey key = entry.getKey();
            Region region = regions.region(key.regionName());
            return region == null || !region.isComplete() || sectorChunks(region, key).isEmpty();
        });
        if (changed) setDirty();
        return changed;
    }

    public void setOwner(FrontlineChunkKey key, String ownerTeamId) {
        FrontlineChunkState state = getOrCreateChunk(key);
        state.setOwnerTeamId(ownerTeamId);
        state.clearCapture();
        setDirty();
    }

    public List<FrontlineChunkKey> sectorChunks(Region region, FrontlineSectorKey sectorKey) {
        if (region == null || sectorKey == null) return List.of();
        List<FrontlineChunkKey> keys = new ArrayList<>(FrontlineSectorKey.SIZE_CHUNKS * FrontlineSectorKey.SIZE_CHUNKS);
        for (int x = sectorKey.minChunkX(); x <= sectorKey.maxChunkX(); x++) {
            for (int z = sectorKey.minChunkZ(); z <= sectorKey.maxChunkZ(); z++) {
                if (region.contains(sectorKey.dimension(), x, z)) {
                    keys.add(new FrontlineChunkKey(sectorKey.dimension(), x, z));
                }
            }
        }
        return keys;
    }

    public Set<FrontlineChunkKey> setSectorOwnerRaw(Region region, FrontlineSectorKey sectorKey, String ownerTeamId) {
        Set<FrontlineChunkKey> changed = new LinkedHashSet<>();
        String owner = FrontlineTeams.normalize(ownerTeamId);
        for (FrontlineChunkKey chunkKey : sectorChunks(region, sectorKey)) {
            FrontlineChunkState chunk = getOrCreateChunk(chunkKey);
            if (!owner.equals(chunk.ownerTeamId())) {
                chunk.setOwnerTeamId(owner);
                changed.add(chunkKey);
            }
            chunk.clearCapture();
        }

        FrontlineSectorState sector = sectors.get(sectorKey);
        if (sector != null) sector.clearCapture();
        if (!changed.isEmpty() || sector != null) setDirty();
        return changed;
    }

    public boolean teamOwnsWholeSector(Region region, FrontlineSectorKey sectorKey, String teamId) {
        if (!FrontlineTeams.isCombatTeam(teamId)) return false;
        List<FrontlineChunkKey> keys = sectorChunks(region, sectorKey);
        if (keys.isEmpty()) return false;
        for (FrontlineChunkKey key : keys) {
            if (!teamId.equals(ownerOf(key))) return false;
        }
        return true;
    }

    public boolean sectorHasGrayZone(Region region, FrontlineSectorKey sectorKey) {
        for (FrontlineChunkKey key : sectorChunks(region, sectorKey)) {
            if (FrontlineTeams.GRAY_ZONE_ID.equals(ownerOf(key))) return true;
        }
        return false;
    }

    public boolean sectorHasNeutralOwner(Region region, FrontlineSectorKey sectorKey) {
        for (FrontlineChunkKey key : sectorChunks(region, sectorKey)) {
            if (ownerOf(key).isBlank()) return true;
        }
        return false;
    }

    public boolean hasAdjacentOwner(Region region, FrontlineSectorKey sectorKey, String teamId) {
        if (!FrontlineTeams.isCombatTeam(teamId)) return false;
        Set<FrontlineChunkKey> sectorChunks = new HashSet<>(sectorChunks(region, sectorKey));
        for (FrontlineChunkKey key : sectorChunks) {
            if (teamId.equals(ownerOf(key))) return true;
            if (isOwnedNeighbor(region, key, teamId, 1, 0)
                    || isOwnedNeighbor(region, key, teamId, -1, 0)
                    || isOwnedNeighbor(region, key, teamId, 0, 1)
                    || isOwnedNeighbor(region, key, teamId, 0, -1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Котёл: находит связные области чанков, не принадлежащих {@code teamId}, которые
     * полностью окружены её территорией и не касаются границы региона. Такие «карманы»
     * целиком переходят к {@code teamId} — вместе с нейтралью и серой зоной внутри кольца.
     * Прогресс захвата секторов, пересекающихся с котлом, сбрасывается.
     * Возвращает перешедшие чанки.
     */
    public Set<FrontlineChunkKey> captureEncircledPockets(Region region, String teamId) {
        if (region == null || !region.isComplete() || !FrontlineTeams.isCombatTeam(teamId)) return Set.of();

        int minX = region.minX();
        int maxX = region.maxX();
        int minZ = region.minZ();
        int maxZ = region.maxZ();
        int depth = maxZ - minZ + 1;
        boolean[] visited = new boolean[(maxX - minX + 1) * depth];
        Set<FrontlineChunkKey> transferred = new LinkedHashSet<>();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int startIndex = (x - minX) * depth + (z - minZ);
                if (visited[startIndex]) continue;
                if (teamId.equals(ownerOf(new FrontlineChunkKey(region.dimension(), x, z)))) continue;

                // Флуд-филл связной области «не наших» чанков (враг/нейтраль/серая зона).
                List<FrontlineChunkKey> pocket = new ArrayList<>();
                boolean touchesBorder = false;
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                visited[startIndex] = true;
                queue.add(new int[]{x, z});
                while (!queue.isEmpty()) {
                    int[] cell = queue.poll();
                    int cx = cell[0];
                    int cz = cell[1];
                    pocket.add(new FrontlineChunkKey(region.dimension(), cx, cz));
                    if (cx == minX || cx == maxX || cz == minZ || cz == maxZ) touchesBorder = true;

                    for (int[] delta : NEIGHBOR_DELTAS) {
                        int nx = cx + delta[0];
                        int nz = cz + delta[1];
                        if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;
                        int index = (nx - minX) * depth + (nz - minZ);
                        if (visited[index]) continue;
                        if (teamId.equals(ownerOf(new FrontlineChunkKey(region.dimension(), nx, nz)))) continue;
                        visited[index] = true;
                        queue.add(new int[]{nx, nz});
                    }
                }

                if (!touchesBorder) transferred.addAll(pocket);
            }
        }

        if (transferred.isEmpty()) return Set.of();

        Set<FrontlineSectorKey> pocketSectors = new LinkedHashSet<>();
        for (FrontlineChunkKey key : transferred) {
            FrontlineChunkState chunk = getOrCreateChunk(key);
            chunk.setOwnerTeamId(teamId);
            chunk.clearCapture();
            pocketSectors.add(FrontlineSectorKey.of(region, key.x(), key.z()));
        }
        for (FrontlineSectorKey sectorKey : pocketSectors) {
            FrontlineSectorState sector = sectors.get(sectorKey);
            if (sector != null) sector.clearCapture();
        }
        setDirty();
        return transferred;
    }

    public int rebuildGrayZones(Region region, Collection<FrontlineChunkKey> preferredChunks) {
        if (region == null || !region.isComplete()) return 0;

        Set<FrontlineChunkKey> preferred = preferredChunks == null ? Set.of() : new HashSet<>(preferredChunks);
        int changed = 0;
        for (FrontlineChunkState state : chunks.values()) {
            FrontlineChunkKey key = state.key();
            if (!region.contains(key.dimension(), key.x(), key.z())) continue;
            if (FrontlineTeams.GRAY_ZONE_ID.equals(state.ownerTeamId())) {
                state.setOwnerTeamId(FrontlineTeams.NEUTRAL_ID);
                state.clearCapture();
                changed++;
            }
        }

        Map<FrontlineChunkKey, String> combatOwners = new LinkedHashMap<>();
        for (FrontlineChunkState state : chunks.values()) {
            FrontlineChunkKey key = state.key();
            if (!region.contains(key.dimension(), key.x(), key.z())) continue;
            if (FrontlineTeams.isCombatTeam(state.ownerTeamId())) {
                combatOwners.put(key, state.ownerTeamId());
            }
        }

        Set<FrontlineChunkKey> grayTargets = new LinkedHashSet<>();
        for (Map.Entry<FrontlineChunkKey, String> entry : combatOwners.entrySet()) {
            collectGrayTarget(region, combatOwners, preferred, grayTargets, entry.getKey(), entry.getValue(), 1, 0);
            collectGrayTarget(region, combatOwners, preferred, grayTargets, entry.getKey(), entry.getValue(), 0, 1);
        }

        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                FrontlineChunkKey key = new FrontlineChunkKey(region.dimension(), x, z);
                if (FrontlineTeams.isCombatTeam(ownerOf(key))) continue;
                Set<String> adjacentTeams = adjacentCombatOwners(region, key);
                if (adjacentTeams.size() > 1) grayTargets.add(key);
            }
        }

        for (FrontlineChunkKey target : grayTargets) {
            FrontlineChunkState state = getOrCreateChunk(target);
            if (!FrontlineTeams.GRAY_ZONE_ID.equals(state.ownerTeamId())) {
                state.setOwnerTeamId(FrontlineTeams.GRAY_ZONE_ID);
                state.clearCapture();
                changed++;
            }
        }

        if (changed > 0) setDirty();
        return changed;
    }

    public boolean hasAdjacentOwner(Region region, FrontlineChunkKey key, String teamId) {
        if (teamId == null || teamId.isBlank()) return false;
        return isOwnedNeighbor(region, key, teamId, 1, 0)
                || isOwnedNeighbor(region, key, teamId, -1, 0)
                || isOwnedNeighbor(region, key, teamId, 0, 1)
                || isOwnedNeighbor(region, key, teamId, 0, -1);
    }

    public boolean teamOwnsAnyChunkInRegion(Region region, String teamId) {
        if (region == null || teamId == null || teamId.isBlank()) return false;
        for (FrontlineChunkState state : chunks.values()) {
            FrontlineChunkKey key = state.key();
            if (region.contains(key.dimension(), key.x(), key.z()) && teamId.equals(state.ownerTeamId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isOwnedNeighbor(Region region, FrontlineChunkKey key, String teamId, int dx, int dz) {
        int nx = key.x() + dx;
        int nz = key.z() + dz;
        if (!region.contains(key.dimension(), nx, nz)) return false;
        return teamId.equals(ownerOf(new FrontlineChunkKey(key.dimension(), nx, nz)));
    }

    private void collectGrayTarget(Region region, Map<FrontlineChunkKey, String> combatOwners, Set<FrontlineChunkKey> preferred,
                                   Set<FrontlineChunkKey> grayTargets, FrontlineChunkKey key, String owner, int dx, int dz) {
        int nx = key.x() + dx;
        int nz = key.z() + dz;
        if (!region.contains(key.dimension(), nx, nz)) return;

        FrontlineChunkKey neighbor = new FrontlineChunkKey(key.dimension(), nx, nz);
        String neighborOwner = combatOwners.get(neighbor);
        if (neighborOwner == null || neighborOwner.equals(owner)) return;

        grayTargets.add(selectGrayTarget(key, owner, neighbor, neighborOwner, preferred));
    }

    private FrontlineChunkKey selectGrayTarget(FrontlineChunkKey first, String firstOwner, FrontlineChunkKey second, String secondOwner,
                                               Set<FrontlineChunkKey> preferred) {
        boolean firstPreferred = preferred.contains(first);
        boolean secondPreferred = preferred.contains(second);
        if (firstPreferred != secondPreferred) return firstPreferred ? second : first;
        int ownerCompare = firstOwner.compareTo(secondOwner);
        if (ownerCompare != 0) return ownerCompare > 0 ? first : second;
        if (first.x() != second.x()) return first.x() > second.x() ? first : second;
        return first.z() > second.z() ? first : second;
    }

    private Set<String> adjacentCombatOwners(Region region, FrontlineChunkKey key) {
        Set<String> owners = new LinkedHashSet<>();
        addAdjacentCombatOwner(region, key, owners, 1, 0);
        addAdjacentCombatOwner(region, key, owners, -1, 0);
        addAdjacentCombatOwner(region, key, owners, 0, 1);
        addAdjacentCombatOwner(region, key, owners, 0, -1);
        return owners;
    }

    private void addAdjacentCombatOwner(Region region, FrontlineChunkKey key, Set<String> owners, int dx, int dz) {
        int nx = key.x() + dx;
        int nz = key.z() + dz;
        if (!region.contains(key.dimension(), nx, nz)) return;
        String owner = ownerOf(new FrontlineChunkKey(key.dimension(), nx, nz));
        if (FrontlineTeams.isCombatTeam(owner)) owners.add(owner);
    }
}
