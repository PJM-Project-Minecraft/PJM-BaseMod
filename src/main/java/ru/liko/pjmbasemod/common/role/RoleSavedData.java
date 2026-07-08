package ru.liko.pjmbasemod.common.role;

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

public final class RoleSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_roles";
    private static final SavedData.Factory<RoleSavedData> FACTORY = new SavedData.Factory<>(
            RoleSavedData::new,
            RoleSavedData::load
    );

    private final Map<UUID, RoleEntry> rolesByPlayer = new LinkedHashMap<>();

    public static RoleSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static RoleSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RoleSavedData data = new RoleSavedData();
        ListTag list = tag.getList("roles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            try {
                UUID playerId = UUID.fromString(entryTag.getString("uuid"));
                CombatRole role = CombatRole.byIdOrAlias(entryTag.getString("role"));
                String team = FrontlineTeams.normalize(entryTag.getString("team"));
                if (role == null || team.isBlank()) continue;
                String name = safeName(entryTag.getString("name"));
                data.rolesByPlayer.put(playerId, new RoleEntry(role.id(), team, name));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, RoleEntry> entry : rolesByPlayer.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("uuid", entry.getKey().toString());
            entryTag.putString("role", entry.getValue().roleId());
            entryTag.putString("team", entry.getValue().teamId());
            entryTag.putString("name", entry.getValue().lastKnownName());
            list.add(entryTag);
        }
        tag.put("roles", list);
        return tag;
    }

    @Nullable
    public RoleEntry entry(UUID playerId) {
        return playerId == null ? null : rolesByPlayer.get(playerId);
    }

    public Map<UUID, RoleEntry> entries() {
        return Map.copyOf(rolesByPlayer);
    }

    public void setRole(UUID playerId, String lastKnownName, String teamId, CombatRole role) {
        if (playerId == null || role == null) return;
        String team = FrontlineTeams.normalize(teamId);
        if (team.isBlank()) return;
        RoleEntry next = new RoleEntry(role.id(), team, safeName(lastKnownName));
        RoleEntry previous = rolesByPlayer.put(playerId, next);
        if (!next.equals(previous)) setDirty();
    }

    @Nullable
    public RoleEntry clearRole(UUID playerId) {
        RoleEntry removed = playerId == null ? null : rolesByPlayer.remove(playerId);
        if (removed != null) setDirty();
        return removed;
    }

    /** Сброс боевых ролей у всех игроков. */
    public void clearAll() {
        if (!rolesByPlayer.isEmpty()) {
            rolesByPlayer.clear();
            setDirty();
        }
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "unknown" : name.trim();
    }

    public record RoleEntry(String roleId, String teamId, String lastKnownName) {
    }
}
