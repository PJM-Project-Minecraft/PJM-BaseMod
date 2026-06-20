package ru.liko.pjmbasemod.common.faction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Заместители фракций: teamId → (playerId → битмаска прав DeputyPermission). */
public final class FactionDeputySavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_faction_deputies";
    private static final SavedData.Factory<FactionDeputySavedData> FACTORY = new SavedData.Factory<>(
            FactionDeputySavedData::new,
            FactionDeputySavedData::load
    );

    private final Map<String, Map<UUID, Integer>> deputiesByTeam = new LinkedHashMap<>();

    public static FactionDeputySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static FactionDeputySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionDeputySavedData data = new FactionDeputySavedData();
        ListTag list = tag.getList("deputies", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String team = normalize(entry.getString("team"));
            if (team.isBlank()) continue;
            try {
                UUID uuid = UUID.fromString(entry.getString("uuid"));
                int perms = DeputyPermission.sanitize(entry.getInt("perms"));
                data.deputiesByTeam.computeIfAbsent(team, k -> new LinkedHashMap<>()).put(uuid, perms);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Map<UUID, Integer>> team : deputiesByTeam.entrySet()) {
            for (Map.Entry<UUID, Integer> member : team.getValue().entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("team", team.getKey());
                entry.putString("uuid", member.getKey().toString());
                entry.putInt("perms", member.getValue());
                list.add(entry);
            }
        }
        tag.put("deputies", list);
        return tag;
    }

    public boolean isDeputy(String teamId, UUID playerId) {
        Map<UUID, Integer> team = deputiesByTeam.get(normalize(teamId));
        return team != null && playerId != null && team.containsKey(playerId);
    }

    public int permissions(String teamId, UUID playerId) {
        Map<UUID, Integer> team = deputiesByTeam.get(normalize(teamId));
        if (team == null || playerId == null) return 0;
        return team.getOrDefault(playerId, 0);
    }

    public int deputyCount(String teamId) {
        Map<UUID, Integer> team = deputiesByTeam.get(normalize(teamId));
        return team == null ? 0 : team.size();
    }

    public Map<UUID, Integer> deputies(String teamId) {
        Map<UUID, Integer> team = deputiesByTeam.get(normalize(teamId));
        return team == null ? Map.of() : Map.copyOf(team);
    }

    public void setDeputy(String teamId, UUID playerId, int perms) {
        String team = normalize(teamId);
        if (team.isBlank() || playerId == null) return;
        int sanitized = DeputyPermission.sanitize(perms);
        Integer previous = deputiesByTeam.computeIfAbsent(team, k -> new LinkedHashMap<>()).put(playerId, sanitized);
        if (previous == null || previous != sanitized) setDirty();
    }

    public boolean removeDeputy(String teamId, UUID playerId) {
        Map<UUID, Integer> team = deputiesByTeam.get(normalize(teamId));
        if (team == null || playerId == null) return false;
        boolean removed = team.remove(playerId) != null;
        if (team.isEmpty()) deputiesByTeam.remove(normalize(teamId));
        if (removed) setDirty();
        return removed;
    }

    @Nullable
    public String deputyTeamOf(UUID playerId) {
        if (playerId == null) return null;
        for (Map.Entry<String, Map<UUID, Integer>> team : deputiesByTeam.entrySet()) {
            if (team.getValue().containsKey(playerId)) return team.getKey();
        }
        return null;
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) return;
        boolean changed = false;
        for (Map<UUID, Integer> team : deputiesByTeam.values()) {
            if (team.remove(playerId) != null) changed = true;
        }
        deputiesByTeam.values().removeIf(Map::isEmpty);
        if (changed) setDirty();
    }

    private static String normalize(String teamId) {
        return FrontlineTeams.normalize(teamId);
    }
}
