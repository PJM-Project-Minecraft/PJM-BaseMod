package ru.liko.pjmbasemod.common.missile;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/** Персистентные кулдауны ракетных ударов по scoreboard-командам. */
public final class MissileStrikeSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_missile_strikes";
    private static final SavedData.Factory<MissileStrikeSavedData> FACTORY = new SavedData.Factory<>(
            MissileStrikeSavedData::new, MissileStrikeSavedData::load);

    private final Map<String, Long> cooldownUntilMs = new LinkedHashMap<>();

    public static MissileStrikeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static MissileStrikeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MissileStrikeSavedData data = new MissileStrikeSavedData();
        CompoundTag cooldowns = tag.getCompound("Cooldowns");
        for (String team : cooldowns.getAllKeys()) {
            long until = cooldowns.getLong(team);
            if (until > 0) data.cooldownUntilMs.put(team, until);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag cooldowns = new CompoundTag();
        cooldownUntilMs.forEach(cooldowns::putLong);
        tag.put("Cooldowns", cooldowns);
        return tag;
    }

    public long remainingSeconds(String teamId, long nowMs) {
        long remaining = cooldownUntilMs.getOrDefault(teamId, 0L) - nowMs;
        return remaining <= 0 ? 0 : (remaining + 999L) / 1000L;
    }

    public void startCooldown(String teamId, int seconds, long nowMs) {
        cooldownUntilMs.put(teamId, nowMs + Math.max(0L, seconds) * 1000L);
        setDirty();
    }

    public void clearAll() {
        if (cooldownUntilMs.isEmpty()) return;
        cooldownUntilMs.clear();
        setDirty();
    }
}
