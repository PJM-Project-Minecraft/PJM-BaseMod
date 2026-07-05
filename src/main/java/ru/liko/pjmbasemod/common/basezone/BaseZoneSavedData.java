package ru.liko.pjmbasemod.common.basezone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class BaseZoneSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_basezones";
    private static final SavedData.Factory<BaseZoneSavedData> FACTORY = new SavedData.Factory<>(
            BaseZoneSavedData::new,
            BaseZoneSavedData::load
    );

    private final Map<String, BaseZone> zones = new LinkedHashMap<>();

    public static BaseZoneSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static BaseZoneSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BaseZoneSavedData data = new BaseZoneSavedData();
        ListTag list = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            BaseZone zone = BaseZone.load(list.getCompound(i));
            if (!zone.name().isBlank()) data.zones.put(key(zone.name()), zone);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BaseZone zone : zones.values()) list.add(zone.save());
        tag.put("zones", list);
        return tag;
    }

    public Collection<BaseZone> zones() {
        return zones.values();
    }

    @Nullable
    public BaseZone zone(String name) {
        if (name == null) return null;
        return zones.get(key(name));
    }

    public BaseZone getOrCreateZone(String name) {
        String key = key(name);
        BaseZone zone = zones.get(key);
        if (zone != null) return zone;
        zone = new BaseZone(name);
        zones.put(key, zone);
        setDirty();
        return zone;
    }

    public boolean deleteZone(String name) {
        if (name == null) return false;
        BaseZone removed = zones.remove(key(name));
        if (removed == null) return false;
        setDirty();
        return true;
    }

    /** Первая завершённая зона с назначенным владельцем, содержащая точку. */
    @Nullable
    public BaseZone findZoneAt(String dimension, BlockPos pos) {
        for (BaseZone zone : zones.values()) {
            if (!zone.owner().isBlank() && zone.contains(dimension, pos)) return zone;
        }
        return null;
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
