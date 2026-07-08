package ru.liko.pjmbasemod.common.fleet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Персистентный реестр активной техники гаража. Хранится в data/{@value #DATA_NAME}.dat overworld-а.
 * {@code active} — карта entityId → запись; {@code lastSpawn} — время последнего спавна на игрока (кулдаун).
 */
public final class VehicleFleetSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_fleet";
    private static final SavedData.Factory<VehicleFleetSavedData> FACTORY = new SavedData.Factory<>(
            VehicleFleetSavedData::new,
            VehicleFleetSavedData::load);

    private final Map<UUID, FleetRecord> active = new LinkedHashMap<>();
    private final Map<UUID, Long> lastSpawn = new HashMap<>();

    public static VehicleFleetSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static VehicleFleetSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        VehicleFleetSavedData data = new VehicleFleetSavedData();
        ListTag records = tag.getList("Active", Tag.TAG_COMPOUND);
        for (int i = 0; i < records.size(); i++) {
            FleetRecord record = FleetRecord.load(records.getCompound(i));
            data.active.put(record.entityId, record);
        }
        ListTag cooldowns = tag.getList("Cooldowns", Tag.TAG_COMPOUND);
        for (int i = 0; i < cooldowns.size(); i++) {
            CompoundTag c = cooldowns.getCompound(i);
            if (c.hasUUID("Owner")) data.lastSpawn.put(c.getUUID("Owner"), c.getLong("Tick"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag records = new ListTag();
        for (FleetRecord record : active.values()) {
            records.add(record.save());
        }
        tag.put("Active", records);
        ListTag cooldowns = new ListTag();
        for (Map.Entry<UUID, Long> entry : lastSpawn.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Owner", entry.getKey());
            c.putLong("Tick", entry.getValue());
            cooldowns.add(c);
        }
        tag.put("Cooldowns", cooldowns);
        return tag;
    }

    public void put(FleetRecord record) {
        active.put(record.entityId, record);
        setDirty();
    }

    public void remove(UUID entityId) {
        if (active.remove(entityId) != null) setDirty();
    }

    /** Копия для безопасной итерации при реконсиляции. */
    public Collection<FleetRecord> all() {
        return new ArrayList<>(active.values());
    }

    public long lastSpawn(UUID owner) {
        return lastSpawn.getOrDefault(owner, 0L);
    }

    public void setLastSpawn(UUID owner, long tick) {
        lastSpawn.put(owner, tick);
        setDirty();
    }
}
