package ru.liko.pjmbasemod.common.region;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RegionSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_regions";
    private static final SavedData.Factory<RegionSavedData> FACTORY = new SavedData.Factory<>(
            RegionSavedData::new,
            RegionSavedData::load
    );

    private final Map<String, Region> regions = new LinkedHashMap<>();

    public static RegionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static RegionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RegionSavedData data = new RegionSavedData();
        ListTag regionTags = tag.getList("regions", Tag.TAG_COMPOUND);
        for (int i = 0; i < regionTags.size(); i++) {
            Region region = Region.load(regionTags.getCompound(i));
            if (!region.name().isBlank()) data.regions.put(key(region.name()), region);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag regionTags = new ListTag();
        for (Region region : regions.values()) {
            regionTags.add(region.save());
        }
        tag.put("regions", regionTags);
        return tag;
    }

    public Collection<Region> regions() {
        return regions.values();
    }

    public List<Region> frontlineRegions() {
        List<Region> result = new ArrayList<>();
        for (Region region : regions.values()) {
            if (region.isFrontline()) result.add(region);
        }
        return List.copyOf(result);
    }

    @Nullable
    public Region region(String name) {
        if (name == null) return null;
        return regions.get(key(name));
    }

    public Region getOrCreateRegion(String name) {
        String key = key(name);
        Region region = regions.get(key);
        if (region != null) return region;
        region = new Region(name);
        regions.put(key, region);
        setDirty();
        return region;
    }

    public boolean deleteRegion(String name) {
        if (name == null) return false;
        Region removed = regions.remove(key(name));
        if (removed == null) return false;
        setDirty();
        return true;
    }

    @Nullable
    public Region findRegion(String dimension, int chunkX, int chunkZ) {
        for (Region region : regions.values()) {
            if (region.contains(dimension, chunkX, chunkZ)) return region;
        }
        return null;
    }

    @Nullable
    public Region findFrontlineRegion(String dimension, int chunkX, int chunkZ) {
        for (Region region : regions.values()) {
            if (region.isFrontline() && region.contains(dimension, chunkX, chunkZ)) return region;
        }
        return null;
    }

    @Nullable
    public Region findRegion(String dimension, ChunkPos pos) {
        return findRegion(dimension, pos.x, pos.z);
    }

    @Nullable
    public Region findFrontlineRegion(String dimension, ChunkPos pos) {
        return findFrontlineRegion(dimension, pos.x, pos.z);
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
