package ru.liko.pjmbasemod.common.mapmarker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.liko.pjmbasemod.common.network.packet.MapMarkerSyncPacket;

/** Тактические метки команд — персистентны, переживают рестарт сервера. */
public final class MapMarkerSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_map_markers";
    private static final SavedData.Factory<MapMarkerSavedData> FACTORY = new SavedData.Factory<>(
            MapMarkerSavedData::new,
            MapMarkerSavedData::load
    );

    /** teamId → (id метки → метка); LinkedHashMap — порядок постановки для вытеснения старых. */
    private final Map<String, LinkedHashMap<UUID, MapMarkerSyncPacket.Entry>> byTeam = new LinkedHashMap<>();

    public static MapMarkerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static MapMarkerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MapMarkerSavedData data = new MapMarkerSavedData();
        ListTag list = tag.getList("markers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            String team = t.getString("team");
            if (team.isBlank()) continue;
            UUID id = t.getUUID("id");
            data.byTeam.computeIfAbsent(team, k -> new LinkedHashMap<>()).put(id,
                    new MapMarkerSyncPacket.Entry(id, t.getString("type"),
                            t.getInt("x"), t.getInt("z"), t.getInt("x2"), t.getInt("z2"),
                            t.getString("dim"), t.getString("owner"), t.getBoolean("commander")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, LinkedHashMap<UUID, MapMarkerSyncPacket.Entry>> team : byTeam.entrySet()) {
            for (MapMarkerSyncPacket.Entry e : team.getValue().values()) {
                CompoundTag t = new CompoundTag();
                t.putString("team", team.getKey());
                t.putUUID("id", e.id());
                t.putString("type", e.type());
                t.putInt("x", e.x());
                t.putInt("z", e.z());
                t.putInt("x2", e.x2());
                t.putInt("z2", e.z2());
                t.putString("dim", e.dimension());
                t.putString("owner", e.owner());
                t.putBoolean("commander", e.commander());
                list.add(t);
            }
        }
        tag.put("markers", list);
        return tag;
    }

    /** Мутабельная карта меток команды; после изменений вызывающий обязан {@link #setDirty()}. */
    public LinkedHashMap<UUID, MapMarkerSyncPacket.Entry> team(String teamId) {
        return byTeam.computeIfAbsent(teamId, k -> new LinkedHashMap<>());
    }

    public Map<String, LinkedHashMap<UUID, MapMarkerSyncPacket.Entry>> all() {
        return byTeam;
    }

    public void clearAll() {
        byTeam.clear();
        setDirty();
    }
}
