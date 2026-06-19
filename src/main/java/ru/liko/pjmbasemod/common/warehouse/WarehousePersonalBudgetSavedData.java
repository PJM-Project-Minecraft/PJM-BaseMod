package ru.liko.pjmbasemod.common.warehouse;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.liko.pjmbasemod.Config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Персистентный личный бюджет очков склада на игрока (анти-«пылесос»).
 *
 * <p>Глобальный на игрока: один бюджет на все склады и все пулы. При выдаче предмета
 * списывается {@code pointCost} и со склада, и из этого бюджета; когда бюджет исчерпан —
 * выдача блокируется, даже если на складе очки есть. Бюджет плавно регенерирует до потолка.</p>
 *
 * <p>Регенерация ленивая: баланс пересчитывается при каждом обращении по серверному игровому
 * времени overworld-а (как и кулдаун выдачи), фоновые тики не нужны. Хранит дробный баланс,
 * чтобы реген &lt; 1 очка/тик не терялся.</p>
 */
public final class WarehousePersonalBudgetSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_warehouse_budget";
    private static final double TICKS_PER_HOUR = 72_000.0; // 20 тиков/с × 3600 с
    private static final SavedData.Factory<WarehousePersonalBudgetSavedData> FACTORY = new SavedData.Factory<>(
            WarehousePersonalBudgetSavedData::new,
            WarehousePersonalBudgetSavedData::load);

    /** Состояние одного игрока: дробный баланс и метка игрового времени последнего пересчёта. */
    private static final class Entry {
        double balance;
        long lastTick;
        Entry(double balance, long lastTick) { this.balance = balance; this.lastTick = lastTick; }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static WarehousePersonalBudgetSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static WarehousePersonalBudgetSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WarehousePersonalBudgetSavedData data = new WarehousePersonalBudgetSavedData();
        ListTag list = tag.getList("Budgets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (!e.hasUUID("Id")) continue;
            data.entries.put(e.getUUID("Id"), new Entry(e.getDouble("Balance"), e.getLong("LastTick")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> entry : entries.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", entry.getKey());
            e.putDouble("Balance", entry.getValue().balance);
            e.putLong("LastTick", entry.getValue().lastTick);
            list.add(e);
        }
        tag.put("Budgets", list);
        return tag;
    }

    /** Текущий баланс игрока (целое, округление вниз) после ленивого пересчёта регенерации. */
    public int getBudget(MinecraftServer server, UUID playerId) {
        return (int) Math.floor(refresh(server, playerId).balance);
    }

    /** Списывает {@code cost} из личного бюджета, если хватает. true — успех. */
    public boolean trySpend(MinecraftServer server, UUID playerId, int cost) {
        if (cost <= 0) return true;
        Entry e = refresh(server, playerId);
        if (e.balance < cost) return false;
        e.balance -= cost;
        setDirty();
        return true;
    }

    /** Возвращает очки в личный бюджет (откат при неудачном списании со склада), не выше потолка. */
    public void refund(MinecraftServer server, UUID playerId, int amount) {
        if (amount <= 0) return;
        Entry e = refresh(server, playerId);
        e.balance = Math.min(Math.max(0, Config.getWarehousePersonalBudgetMax()), e.balance + amount);
        setDirty();
    }

    /** Ленивый пересчёт: добавляет накопленную регенерацию до потолка и обновляет метку времени. */
    private Entry refresh(MinecraftServer server, UUID playerId) {
        double max = Math.max(0, Config.getWarehousePersonalBudgetMax());
        double regenPerHour = Math.max(0, Config.getWarehousePersonalBudgetRegenPerHour());
        long now = server.overworld().getGameTime();

        Entry e = entries.get(playerId);
        if (e == null) {
            // Новый игрок стартует с полным бюджетом.
            e = new Entry(max, now);
            entries.put(playerId, e);
            setDirty();
            return e;
        }
        long elapsed = now - e.lastTick;
        if (elapsed < 0) elapsed = 0; // защита от перевода игрового времени назад
        double regen = regenPerHour > 0 ? elapsed * (regenPerHour / TICKS_PER_HOUR) : 0.0;
        e.balance = Math.min(max, e.balance + regen); // зажимаем и при уменьшении потолка в конфиге
        e.lastTick = now;
        setDirty();
        return e;
    }
}
