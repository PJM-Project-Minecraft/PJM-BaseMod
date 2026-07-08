package ru.liko.pjmbasemod.common.faction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.role.CombatRole;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class FactionSelectionSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_faction_selection";
    private static final SavedData.Factory<FactionSelectionSavedData> FACTORY = new SavedData.Factory<>(
            FactionSelectionSavedData::new,
            FactionSelectionSavedData::load
    );

    private final Map<UUID, SelectionEntry> selectionsByPlayer = new LinkedHashMap<>();

    public static FactionSelectionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static FactionSelectionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionSelectionSavedData data = new FactionSelectionSavedData();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            try {
                UUID playerId = UUID.fromString(entryTag.getString("uuid"));
                String team = FrontlineTeams.normalize(entryTag.getString("team"));
                CombatRole role = CombatRole.byIdOrAlias(entryTag.getString("role"));
                if (team.isBlank() || role == null) continue;
                String name = safeName(entryTag.getString("name"));
                data.selectionsByPlayer.put(playerId, new SelectionEntry(team, role.id(), name));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, SelectionEntry> entry : selectionsByPlayer.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("uuid", entry.getKey().toString());
            entryTag.putString("team", entry.getValue().teamId());
            entryTag.putString("role", entry.getValue().roleId());
            entryTag.putString("name", entry.getValue().lastKnownName());
            list.add(entryTag);
        }
        tag.put("players", list);
        return tag;
    }

    public boolean isComplete(UUID playerId) {
        return playerId != null && selectionsByPlayer.containsKey(playerId);
    }

    @Nullable
    public SelectionEntry entry(UUID playerId) {
        return playerId == null ? null : selectionsByPlayer.get(playerId);
    }

    public void markComplete(UUID playerId, String lastKnownName, String teamId, CombatRole role) {
        if (playerId == null || role == null) return;
        String team = FrontlineTeams.normalize(teamId);
        if (team.isBlank()) return;
        SelectionEntry next = new SelectionEntry(team, role.id(), safeName(lastKnownName));
        SelectionEntry previous = selectionsByPlayer.put(playerId, next);
        if (!next.equals(previous)) setDirty();
    }

    public void clear(UUID playerId) {
        if (playerId != null && selectionsByPlayer.remove(playerId) != null) {
            setDirty();
        }
    }

    /** Сброс выбора фракции у всех игроков. */
    public void clearAll() {
        if (!selectionsByPlayer.isEmpty()) {
            selectionsByPlayer.clear();
            setDirty();
        }
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "unknown" : name.trim();
    }

    public record SelectionEntry(String teamId, String roleId, String lastKnownName) {
    }
}
