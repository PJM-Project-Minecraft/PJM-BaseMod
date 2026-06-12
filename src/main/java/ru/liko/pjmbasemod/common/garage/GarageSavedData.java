package ru.liko.pjmbasemod.common.garage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Персистентное хранилище гаражей. Ключ — UUID владельца, значение — список
 * сохранённых экземпляров техники. Сохраняется в data/{@value #DATA_NAME}.dat overworld-а.
 */
public final class GarageSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_garage";
    private static final SavedData.Factory<GarageSavedData> FACTORY = new SavedData.Factory<>(
            GarageSavedData::new,
            GarageSavedData::load);

    private final Map<UUID, List<StoredVehicle>> garages = new LinkedHashMap<>();

    public static GarageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static GarageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        GarageSavedData data = new GarageSavedData();
        ListTag owners = tag.getList("Owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size(); i++) {
            CompoundTag ownerTag = owners.getCompound(i);
            if (!ownerTag.hasUUID("Owner")) continue;
            UUID owner = ownerTag.getUUID("Owner");
            List<StoredVehicle> list = new ArrayList<>();
            ListTag vehicles = ownerTag.getList("Vehicles", Tag.TAG_COMPOUND);
            for (int j = 0; j < vehicles.size(); j++) {
                list.add(StoredVehicle.load(vehicles.getCompound(j)));
            }
            data.garages.put(owner, list);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        for (Map.Entry<UUID, List<StoredVehicle>> entry : garages.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID("Owner", entry.getKey());
            ListTag vehicles = new ListTag();
            for (StoredVehicle vehicle : entry.getValue()) {
                vehicles.add(vehicle.save());
            }
            ownerTag.put("Vehicles", vehicles);
            owners.add(ownerTag);
        }
        tag.put("Owners", owners);
        return tag;
    }

    public List<StoredVehicle> garageOf(UUID owner) {
        return List.copyOf(garages.getOrDefault(owner, List.of()));
    }

    public void add(UUID owner, StoredVehicle vehicle) {
        garages.computeIfAbsent(owner, k -> new ArrayList<>()).add(vehicle);
        setDirty();
    }

    @Nullable
    public StoredVehicle remove(UUID owner, UUID instanceId) {
        List<StoredVehicle> list = garages.get(owner);
        if (list == null) return null;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).instanceId().equals(instanceId)) {
                StoredVehicle removed = list.remove(i);
                setDirty();
                return removed;
            }
        }
        return null;
    }
}
