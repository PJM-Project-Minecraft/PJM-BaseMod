package ru.liko.pjmbasemod.common.rank;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class RankSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_rank_xp";
    private static final SavedData.Factory<RankSavedData> FACTORY = new SavedData.Factory<>(
            RankSavedData::new,
            RankSavedData::load
    );

    private final Map<UUID, Integer> xpByPlayer = new LinkedHashMap<>();

    public static RankSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static RankSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RankSavedData data = new RankSavedData();
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            try {
                UUID uuid = UUID.fromString(playerTag.getString("uuid"));
                data.xpByPlayer.put(uuid, Math.max(0, playerTag.getInt("xp")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Integer> entry : xpByPlayer.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putString("uuid", entry.getKey().toString());
            playerTag.putInt("xp", Math.max(0, entry.getValue()));
            players.add(playerTag);
        }
        tag.put("players", players);
        return tag;
    }

    public int xp(UUID playerId) {
        return Math.max(0, xpByPlayer.getOrDefault(playerId, 0));
    }

    public void setXp(UUID playerId, int xp) {
        int clamped = Math.max(0, xp);
        Integer previous = xpByPlayer.put(playerId, clamped);
        if (previous == null || previous != clamped) setDirty();
    }

    public void reset(UUID playerId) {
        if (xpByPlayer.remove(playerId) != null) setDirty();
    }
}
