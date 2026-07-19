package ru.liko.pjmbasemod.common.campaign;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Состояние недельной кампании: очки победы (VP) по командам + эпоха-метки
 * старта раунда и последнего начисления VP (реальное время, переживает рестарты).
 */
public final class CampaignSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_campaign";
    private static final SavedData.Factory<CampaignSavedData> FACTORY = new SavedData.Factory<>(
            CampaignSavedData::new,
            CampaignSavedData::load
    );

    private final Map<String, Long> vpByTeam = new LinkedHashMap<>();
    private long startEpochMs;
    private long lastVpGrantEpochMs;

    public static CampaignSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static CampaignSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CampaignSavedData data = new CampaignSavedData();
        data.startEpochMs = tag.getLong("start");
        data.lastVpGrantEpochMs = tag.getLong("lastGrant");
        CompoundTag vp = tag.getCompound("vp");
        for (String team : vp.getAllKeys()) {
            data.vpByTeam.put(team, Math.max(0L, vp.getLong(team)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("start", startEpochMs);
        tag.putLong("lastGrant", lastVpGrantEpochMs);
        CompoundTag vp = new CompoundTag();
        vpByTeam.forEach(vp::putLong);
        tag.put("vp", vp);
        return tag;
    }

    public long startEpochMs() { return startEpochMs; }

    public long lastVpGrantEpochMs() { return lastVpGrantEpochMs; }

    public Map<String, Long> vpByTeam() { return Map.copyOf(vpByTeam); }

    public long vp(String teamId) { return vpByTeam.getOrDefault(teamId, 0L); }

    public void addVp(String teamId, long delta) {
        if (delta <= 0 || teamId == null || teamId.isBlank()) return;
        vpByTeam.merge(teamId, delta, Long::sum);
        setDirty();
    }

    public void markVpGrant(long nowMs) {
        lastVpGrantEpochMs = nowMs;
        setDirty();
    }

    /** Новый раунд: VP в ноль, старт = now. */
    public void restart(long nowMs) {
        vpByTeam.clear();
        startEpochMs = nowMs;
        lastVpGrantEpochMs = nowMs;
        setDirty();
    }
}
