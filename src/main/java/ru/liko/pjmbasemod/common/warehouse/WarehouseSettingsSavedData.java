package ru.liko.pjmbasemod.common.warehouse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Персистентные настройки зон приёма всех именованных складов.
 */
public final class WarehouseSettingsSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_warehouse_settings";
    private static final SavedData.Factory<WarehouseSettingsSavedData> FACTORY = new SavedData.Factory<>(
            WarehouseSettingsSavedData::new,
            WarehouseSettingsSavedData::load);

    private final Map<String, WarehouseSettings> settings = new LinkedHashMap<>();

    public static WarehouseSettingsSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static WarehouseSettingsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WarehouseSettingsSavedData data = new WarehouseSettingsSavedData();
        ListTag list = tag.getList("Settings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            WarehouseSettings s = WarehouseSettings.load(list.getCompound(i));
            if (!s.warehouseId().isBlank()) {
                data.settings.put(s.warehouseId(), s);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (WarehouseSettings s : settings.values()) {
            list.add(s.save());
        }
        tag.put("Settings", list);
        return tag;
    }

    public Collection<WarehouseSettings> all() {
        return List.copyOf(settings.values());
    }

    @Nullable
    public WarehouseSettings get(String warehouseId) {
        return settings.get(warehouseId);
    }

    public WarehouseSettings getOrEmpty(String warehouseId) {
        return settings.getOrDefault(warehouseId, WarehouseSettings.empty(warehouseId));
    }

    public WarehouseSettings setReception(String warehouseId, ServerLevel level, BlockPos pos) {
        WarehouseSettings updated = getOrEmpty(warehouseId).withReception(level.dimension(), pos);
        settings.put(warehouseId, updated);
        setDirty();
        return updated;
    }

    public WarehouseSettings setRadius(String warehouseId, int radius) {
        WarehouseSettings updated = getOrEmpty(warehouseId).withRadius(radius);
        settings.put(warehouseId, updated);
        setDirty();
        return updated;
    }

    public void remove(String warehouseId) {
        if (settings.remove(warehouseId) != null) {
            setDirty();
        }
    }
}
