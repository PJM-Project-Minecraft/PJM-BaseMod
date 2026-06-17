package ru.liko.pjmbasemod.common.inventory;

import net.minecraft.world.item.ItemStack;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.compat.TaczWarehouseCompat;
import ru.liko.pjmbasemod.common.warehouse.WarehouseItemDefinition;
import ru.liko.pjmbasemod.common.warehouse.WarehouseItemRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Индекс роль-локированного снаряжения: подмножество определений склада с roleLocked=true,
 * с предвычисленным режимом блокировки. Режим вычисляется один раз при rebuild
 * (TACZ дёргается только здесь, не на каждом тике). Опознание стека — через
 * существующий {@link WarehouseItemDefinition#matchesStack(ItemStack)}.
 */
public final class EquipmentRoleIndex {

    public enum LockMode { USE, HOLD }

    /** Какие роли допускают предмет и каким способом он блокируется для остальных. */
    public record LockInfo(List<String> allowedRoles, LockMode mode) {}

    private static final EquipmentRoleIndex INSTANCE = new EquipmentRoleIndex();

    /** Пара: определение + предвычисленный режим. */
    private record Entry(WarehouseItemDefinition def, LockMode mode) {}

    private volatile List<Entry> entries = List.of();

    private EquipmentRoleIndex() {}

    public static EquipmentRoleIndex get() { return INSTANCE; }

    /** Пересобирает индекс из текущего каталога склада. Возвращает число роль-локированных предметов. */
    public synchronized int rebuild() {
        List<Entry> built = new ArrayList<>();
        for (WarehouseItemDefinition def : WarehouseItemRegistry.get().all()) {
            if (!def.roleLocked()) continue;
            if (def.allowedRoles().isEmpty()) {
                Pjmbasemod.LOGGER.warn(
                        "Equipment: предмет '{}' помечен roleLocked, но не имеет allowedRoles — роль-лок не действует.",
                        def.id());
                continue;
            }
            built.add(new Entry(def, resolveMode(def)));
        }
        entries = List.copyOf(built);
        Pjmbasemod.LOGGER.info("Equipment: индекс роль-локированных предметов: {}.", entries.size());
        return entries.size();
    }

    /** Определение режима блокировки для предмета: явный lockMode или auto (TACZ-ствол → HOLD, иначе USE). */
    private LockMode resolveMode(WarehouseItemDefinition def) {
        switch (def.lockMode()) {
            case "use": return LockMode.USE;
            case "hold": return LockMode.HOLD;
            default:
                boolean gun = def.hasTaczGun() || TaczWarehouseCompat.isGun(def.iconStack());
                return gun ? LockMode.HOLD : LockMode.USE;
        }
    }

    /** Находит роль-лок для стека или null, если предмет не роль-локирован. */
    @Nullable
    public LockInfo lookup(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Entry entry : entries) {
            if (entry.def().matchesStack(stack)) {
                return new LockInfo(entry.def().allowedRoles(), entry.mode());
            }
        }
        return null;
    }
}
