package ru.liko.pjmbasemod.common.warehouse;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Персистентное хранилище очков поставки именованных складов.
 * Ключ — id склада, значение — очки по пулам ({@link WarehousePoolCategory}).
 * Сохраняется в data/{@value #DATA_NAME}.dat overworld-а.
 */
public final class WarehouseSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_warehouse";
    private static final SavedData.Factory<WarehouseSavedData> FACTORY = new SavedData.Factory<>(
            WarehouseSavedData::new,
            WarehouseSavedData::load);

    private final Map<String, EnumMap<WarehousePoolCategory, Integer>> stock = new LinkedHashMap<>();

    public static WarehouseSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static WarehouseSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WarehouseSavedData data = new WarehouseSavedData();
        ListTag list = tag.getList("Warehouses", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag warehouseTag = list.getCompound(i);
            String id = warehouseTag.getString("Id");
            if (id.isBlank()) continue;
            EnumMap<WarehousePoolCategory, Integer> points = emptyPoints();
            CompoundTag pointsTag = warehouseTag.getCompound("Points");
            // Ключи старых пулов (weapon/equipment/special) схлопываются в supply — см. WarehousePoolCategory.byId.
            for (String key : pointsTag.getAllKeys()) {
                WarehousePoolCategory pool = WarehousePoolCategory.byId(key);
                if (pool == null) continue;
                points.merge(pool, Math.max(0, pointsTag.getInt(key)), Integer::sum);
            }
            data.stock.put(id, points);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, EnumMap<WarehousePoolCategory, Integer>> entry : stock.entrySet()) {
            CompoundTag warehouseTag = new CompoundTag();
            warehouseTag.putString("Id", entry.getKey());
            CompoundTag pointsTag = new CompoundTag();
            for (Map.Entry<WarehousePoolCategory, Integer> p : entry.getValue().entrySet()) {
                pointsTag.putInt(p.getKey().id(), Math.max(0, p.getValue()));
            }
            warehouseTag.put("Points", pointsTag);
            list.add(warehouseTag);
        }
        tag.put("Warehouses", list);
        return tag;
    }

    public boolean exists(String warehouseId) {
        return stock.containsKey(warehouseId);
    }

    public Set<String> ids() {
        return Set.copyOf(stock.keySet());
    }

    public void createWarehouse(String warehouseId) {
        if (stock.putIfAbsent(warehouseId, emptyPoints()) == null) {
            setDirty();
        }
    }

    public boolean delete(String warehouseId) {
        if (stock.remove(warehouseId) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public int getPoints(String warehouseId, WarehousePoolCategory pool) {
        EnumMap<WarehousePoolCategory, Integer> points = stock.get(warehouseId);
        return points == null ? 0 : points.getOrDefault(pool, 0);
    }

    public void addPoints(String warehouseId, WarehousePoolCategory pool, int amount) {
        if (amount == 0) return;
        EnumMap<WarehousePoolCategory, Integer> points = stock.computeIfAbsent(warehouseId, k -> emptyPoints());
        int updated = Math.max(0, points.getOrDefault(pool, 0) + amount);
        points.put(pool, updated);
        setDirty();
    }

    /** Выставляет точное значение очков пула (clamp ≥ 0). */
    public void setPoints(String warehouseId, WarehousePoolCategory pool, int amount) {
        EnumMap<WarehousePoolCategory, Integer> points = stock.computeIfAbsent(warehouseId, k -> emptyPoints());
        points.put(pool, Math.max(0, amount));
        setDirty();
    }

    /** Списывает {@code cost} очков из пула, если их достаточно. true — успех. */
    public boolean trySpend(String warehouseId, WarehousePoolCategory pool, int cost) {
        if (cost <= 0) return true;
        EnumMap<WarehousePoolCategory, Integer> points = stock.get(warehouseId);
        if (points == null) return false;
        int have = points.getOrDefault(pool, 0);
        if (have < cost) return false;
        points.put(pool, have - cost);
        setDirty();
        return true;
    }

    /** Сброс накопленных очков всех складов (админ-разметка зон приёма не трогается). */
    public void clearAll() {
        if (!stock.isEmpty()) {
            stock.clear();
            setDirty();
        }
    }

    private static EnumMap<WarehousePoolCategory, Integer> emptyPoints() {
        EnumMap<WarehousePoolCategory, Integer> points = new EnumMap<>(WarehousePoolCategory.class);
        for (WarehousePoolCategory pool : WarehousePoolCategory.values()) {
            points.put(pool, 0);
        }
        return points;
    }
}
