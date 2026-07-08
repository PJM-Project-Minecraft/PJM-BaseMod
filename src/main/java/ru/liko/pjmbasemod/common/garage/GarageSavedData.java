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
 * Персистентное хранилище гаражей.
 *
 * <p>Ключ — строка гаража: {@code "team:<teamId>"} для общего гаража команды (все сокомандники
 * делят один пул техники) либо {@code "player:<uuid>"} для личного гаража игрока без команды.
 * Значение — список сохранённых экземпляров техники. Сохраняется в data/{@value #DATA_NAME}.dat
 * overworld-а.</p>
 *
 * <p>Старый формат (per-owner по UUID) при загрузке мигрирует в личный ключ {@code "player:<uuid>"},
 * чтобы накопленная техника не терялась.</p>
 */
public final class GarageSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_garage";
    private static final SavedData.Factory<GarageSavedData> FACTORY = new SavedData.Factory<>(
            GarageSavedData::new,
            GarageSavedData::load);

    /** Префикс ключа личного гаража игрока без команды. */
    public static final String PLAYER_KEY_PREFIX = "player:";
    /** Префикс ключа общего гаража команды. */
    public static final String TEAM_KEY_PREFIX = "team:";

    private final Map<String, List<StoredVehicle>> garages = new LinkedHashMap<>();

    public static GarageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Ключ личного гаража игрока (фолбэк, если игрок не в команде). */
    public static String playerKey(UUID owner) {
        return PLAYER_KEY_PREFIX + owner;
    }

    /** Ключ общего гаража команды. */
    public static String teamKey(String teamId) {
        return TEAM_KEY_PREFIX + teamId;
    }

    public static GarageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        GarageSavedData data = new GarageSavedData();
        ListTag owners = tag.getList("Owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size(); i++) {
            CompoundTag ownerTag = owners.getCompound(i);
            String key;
            if (ownerTag.contains("Key", Tag.TAG_STRING)) {
                key = ownerTag.getString("Key");
            } else if (ownerTag.hasUUID("Owner")) {
                // Миграция старого формата: личный гараж по UUID.
                key = playerKey(ownerTag.getUUID("Owner"));
            } else {
                continue;
            }
            if (key.isBlank()) continue;
            List<StoredVehicle> list = new ArrayList<>();
            ListTag vehicles = ownerTag.getList("Vehicles", Tag.TAG_COMPOUND);
            for (int j = 0; j < vehicles.size(); j++) {
                list.add(StoredVehicle.load(vehicles.getCompound(j)));
            }
            data.garages.put(key, list);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        for (Map.Entry<String, List<StoredVehicle>> entry : garages.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putString("Key", entry.getKey());
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

    public List<StoredVehicle> garageOf(String key) {
        return List.copyOf(garages.getOrDefault(key, List.of()));
    }

    public void add(String key, StoredVehicle vehicle) {
        garages.computeIfAbsent(key, k -> new ArrayList<>()).add(vehicle);
        setDirty();
    }

    /** Опустошает хранимую технику во всех гаражах, сами гаражи (ключи) сохраняются. */
    public void clearVehicles() {
        boolean changed = false;
        for (List<StoredVehicle> list : garages.values()) {
            if (!list.isEmpty()) {
                list.clear();
                changed = true;
            }
        }
        if (changed) setDirty();
    }

    @Nullable
    public StoredVehicle remove(String key, UUID instanceId) {
        List<StoredVehicle> list = garages.get(key);
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
