package ru.liko.pjmbasemod.common.customization;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-world выбор скина игроком (UUID → skinId). Паттерн {@code RoleSavedData}. */
public final class SkinSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_skins";
    private static final SavedData.Factory<SkinSavedData> FACTORY = new SavedData.Factory<>(
            SkinSavedData::new,
            SkinSavedData::load
    );

    private final Map<UUID, String> skinByPlayer = new LinkedHashMap<>();
    /** Скины, назначенные админом принудительно: не откатываются валидацией по пулу команды. */
    private final Set<UUID> forcedPlayers = new HashSet<>();

    public static SkinSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static SkinSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SkinSavedData data = new SkinSavedData();
        ListTag list = tag.getList("skins", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                UUID id = UUID.fromString(entry.getString("uuid"));
                String skin = SkinRegistry.sanitize(entry.getString("skin"));
                if (!skin.isBlank()) {
                    data.skinByPlayer.put(id, skin);
                    if (entry.getBoolean("forced")) data.forcedPlayers.add(id);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, String> e : skinByPlayer.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("uuid", e.getKey().toString());
            entry.putString("skin", e.getValue());
            if (forcedPlayers.contains(e.getKey())) entry.putBoolean("forced", true);
            list.add(entry);
        }
        tag.put("skins", list);
        return tag;
    }

    @Nullable
    public String getSkin(UUID playerId) {
        return playerId == null ? null : skinByPlayer.get(playerId);
    }

    public void setSkin(UUID playerId, String skinId) {
        if (playerId == null) return;
        String id = SkinRegistry.sanitize(skinId);
        if (id.isBlank()) {
            clear(playerId);
            return;
        }
        String prev = skinByPlayer.put(playerId, id);
        // Собственный выбор игрока снимает принудительное назначение админа.
        if (forcedPlayers.remove(playerId) || !id.equals(prev)) setDirty();
    }

    /** Принудительное назначение скина админом: переживает валидацию по пулу команды. */
    public void setForced(UUID playerId, String skinId) {
        if (playerId == null) return;
        String id = SkinRegistry.sanitize(skinId);
        if (id.isBlank()) {
            clear(playerId);
            return;
        }
        String prev = skinByPlayer.put(playerId, id);
        if (forcedPlayers.add(playerId) || !id.equals(prev)) setDirty();
    }

    public boolean isForced(UUID playerId) {
        return playerId != null && forcedPlayers.contains(playerId);
    }

    public void clear(UUID playerId) {
        if (playerId == null) return;
        boolean removedForced = forcedPlayers.remove(playerId);
        if (skinByPlayer.remove(playerId) != null || removedForced) setDirty();
    }
}
