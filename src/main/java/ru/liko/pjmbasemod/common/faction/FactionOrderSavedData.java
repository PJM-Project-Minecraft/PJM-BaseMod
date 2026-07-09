package ru.liko.pjmbasemod.common.faction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.liko.pjmbasemod.common.teams.Teams;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Текущий приказ каждой фракции. expiresAtGameTime == -1 → бессрочно. */
public final class FactionOrderSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_faction_orders";
    private static final SavedData.Factory<FactionOrderSavedData> FACTORY = new SavedData.Factory<>(
            FactionOrderSavedData::new,
            FactionOrderSavedData::load
    );

    private final Map<String, OrderEntry> ordersByTeam = new LinkedHashMap<>();

    public static FactionOrderSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static FactionOrderSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionOrderSavedData data = new FactionOrderSavedData();
        ListTag list = tag.getList("orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String team = Teams.normalize(entry.getString("team"));
            if (team.isBlank()) continue;
            String text = entry.getString("text");
            if (text.isBlank()) continue;
            data.ordersByTeam.put(team, new OrderEntry(
                    text,
                    entry.getString("author"),
                    entry.getLong("setAt"),
                    entry.getLong("expiresAt")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, OrderEntry> entry : ordersByTeam.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("team", entry.getKey());
            t.putString("text", entry.getValue().text());
            t.putString("author", entry.getValue().author());
            t.putLong("setAt", entry.getValue().setAtGameTime());
            t.putLong("expiresAt", entry.getValue().expiresAtGameTime());
            list.add(t);
        }
        tag.put("orders", list);
        return tag;
    }

    @Nullable
    public OrderEntry order(String teamId) {
        return ordersByTeam.get(Teams.normalize(teamId));
    }

    public void setOrder(String teamId, OrderEntry entry) {
        String team = Teams.normalize(teamId);
        if (team.isBlank() || entry == null) return;
        ordersByTeam.put(team, entry);
        setDirty();
    }

    public void clearOrder(String teamId) {
        if (ordersByTeam.remove(Teams.normalize(teamId)) != null) setDirty();
    }

    /** Убрать приказы у всех фракций. */
    public void clearAll() {
        if (!ordersByTeam.isEmpty()) {
            ordersByTeam.clear();
            setDirty();
        }
    }

    public Map<String, OrderEntry> orders() {
        return Map.copyOf(ordersByTeam);
    }

    public record OrderEntry(String text, String author, long setAtGameTime, long expiresAtGameTime) {
    }
}
