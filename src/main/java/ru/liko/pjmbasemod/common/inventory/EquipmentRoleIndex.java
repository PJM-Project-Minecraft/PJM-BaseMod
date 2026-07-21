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
 * Индекс локированного снаряжения: подмножество определений склада с roleLocked=true
 * и/или allowedTeams (команда-владелец), с предвычисленным режимом блокировки. Режим
 * вычисляется один раз при rebuild (TACZ дёргается только здесь, не на каждом тике).
 * Опознание стека — через существующий {@link WarehouseItemDefinition#matchesStack(ItemStack)}.
 */
public final class EquipmentRoleIndex {

    public enum LockMode { USE, HOLD }

    /**
     * Кто допускает предмет и каким способом он блокируется для остальных.
     * Пустой {@code allowedRoles} — роль-лока нет; пустой {@code allowedTeams} — тим-лока нет.
     */
    public record LockInfo(List<String> allowedRoles, List<String> allowedTeams, LockMode mode) {}

    private static final EquipmentRoleIndex INSTANCE = new EquipmentRoleIndex();

    /** Пара: определение + предвычисленный лок. */
    private record Entry(WarehouseItemDefinition def, LockInfo info) {}

    private volatile List<Entry> entries = List.of();

    private EquipmentRoleIndex() {}

    public static EquipmentRoleIndex get() { return INSTANCE; }

    /** Пересобирает индекс из текущего каталога склада. Возвращает число локированных предметов. */
    public synchronized int rebuild() {
        List<Entry> built = new ArrayList<>();
        for (WarehouseItemDefinition def : WarehouseItemRegistry.get().all()) {
            boolean roleGate = def.roleLocked();
            boolean teamGate = def.teamRestricted();
            if (roleGate && def.allowedRoles().isEmpty()) {
                Pjmbasemod.LOGGER.warn(
                        "Equipment: предмет '{}' помечен roleLocked, но не имеет allowedRoles — роль-лок не действует.",
                        def.id());
                roleGate = false;
            }
            if (!roleGate && !teamGate) continue;
            LockInfo info = new LockInfo(
                    roleGate ? def.allowedRoles() : List.of(),
                    teamGate ? def.allowedTeams() : List.of(),
                    resolveMode(def));
            built.add(new Entry(def, info));
        }
        entries = List.copyOf(built);
        Pjmbasemod.LOGGER.info("Equipment: индекс локированных предметов: {}.", entries.size());
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
                return entry.info();
            }
        }
        return null;
    }
}
