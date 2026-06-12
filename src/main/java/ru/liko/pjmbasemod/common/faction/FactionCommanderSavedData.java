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

public final class FactionCommanderSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_faction_commanders";
    private static final SavedData.Factory<FactionCommanderSavedData> FACTORY = new SavedData.Factory<>(
            FactionCommanderSavedData::new,
            FactionCommanderSavedData::load
    );

    private final Map<String, CommanderEntry> commandersByTeam = new LinkedHashMap<>();

    public static FactionCommanderSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static FactionCommanderSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionCommanderSavedData data = new FactionCommanderSavedData();
        ListTag list = tag.getList("commanders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            String teamId = normalizeTeam(entryTag.getString("team"));
            if (teamId.isBlank()) continue;
            try {
                UUID playerId = UUID.fromString(entryTag.getString("uuid"));
                String name = entryTag.getString("name");
                data.commandersByTeam.put(teamId, new CommanderEntry(playerId, name));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, CommanderEntry> entry : commandersByTeam.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("team", entry.getKey());
            entryTag.putString("uuid", entry.getValue().playerId().toString());
            entryTag.putString("name", entry.getValue().lastKnownName());
            list.add(entryTag);
        }
        tag.put("commanders", list);
        return tag;
    }

    @Nullable
    public CommanderEntry commander(String teamId) {
        return commandersByTeam.get(normalizeTeam(teamId));
    }

    public Map<String, CommanderEntry> commanders() {
        return Map.copyOf(commandersByTeam);
    }

    @Nullable
    public String teamOf(UUID playerId) {
        if (playerId == null) return null;
        for (Map.Entry<String, CommanderEntry> entry : commandersByTeam.entrySet()) {
            if (playerId.equals(entry.getValue().playerId())) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Nullable
    public CommanderEntry setCommander(String teamId, UUID playerId, String lastKnownName) {
        String team = normalizeTeam(teamId);
        if (team.isBlank() || playerId == null) return null;
        CommanderEntry previous = commandersByTeam.put(team, new CommanderEntry(playerId, safeName(lastKnownName)));
        if (previous == null || !previous.playerId().equals(playerId)
                || !previous.lastKnownName().equals(safeName(lastKnownName))) {
            setDirty();
        }
        return previous;
    }

    @Nullable
    public CommanderEntry clearCommander(String teamId) {
        CommanderEntry removed = commandersByTeam.remove(normalizeTeam(teamId));
        if (removed != null) setDirty();
        return removed;
    }

    public Map<String, CommanderEntry> clearPlayer(UUID playerId) {
        Map<String, CommanderEntry> removed = new LinkedHashMap<>();
        if (playerId == null) return removed;
        commandersByTeam.entrySet().removeIf(entry -> {
            if (!playerId.equals(entry.getValue().playerId())) return false;
            removed.put(entry.getKey(), entry.getValue());
            return true;
        });
        if (!removed.isEmpty()) setDirty();
        return removed;
    }

    private static String normalizeTeam(String teamId) {
        return FrontlineTeams.normalize(teamId);
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "unknown" : name.trim();
    }

    public record CommanderEntry(UUID playerId, String lastKnownName) {
    }
}
