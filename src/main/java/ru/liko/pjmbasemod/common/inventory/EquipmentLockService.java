package ru.liko.pjmbasemod.common.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.liko.pjmbasemod.common.role.RoleService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Серверная блокировка снаряжения чужой роли. HOLD-предметы (TACZ-оружие) выталкиваются
 * из руки на тике; USE-предметы блокируются обработчиками событий применения.
 * Сервер авторитетен; клиентских зеркал нет.
 */
public final class EquipmentLockService {

    /** Последний слот основного инвентаря (хотбар + 3 ряда). */
    private static final int MAIN_INVENTORY_END = 35;
    /** Слот второй руки (offhand) в ванильном Inventory. */
    private static final int OFFHAND_SLOT = 40;
    /** Минимальный интервал между предупреждениями игроку, тики. */
    private static final long WARN_INTERVAL_TICKS = 40L;

    private static final Map<UUID, Long> lastWarnTick = new HashMap<>();

    private EquipmentLockService() {}

    /** Предмет роль-локирован И активная роль игрока не входит в его allowedRoles (или роли нет). */
    public static boolean isForbidden(ServerPlayer player, ItemStack stack) {
        if (player == null || stack.isEmpty()) return false;
        EquipmentRoleIndex.LockInfo info = EquipmentRoleIndex.get().lookup(stack);
        if (info == null) return false;
        var role = RoleService.currentRole(player);
        return role == null || !info.allowedRoles().contains(role.id());
    }

    /** Выталкивает из обеих рук запрещённое HOLD-оружие (нельзя держать → нельзя выстрелить). */
    public static void enforceHeld(ServerPlayer player) {
        if (player == null || player.isCreative()) return;
        Inventory inv = player.getInventory();
        boolean moved = false;

        int handSlot = inv.selected;
        if (handSlot >= 0 && handSlot <= 8) {
            moved |= enforceSlot(player, inv, handSlot, player.getMainHandItem());
        }
        moved |= enforceSlot(player, inv, OFFHAND_SLOT, player.getOffhandItem());

        if (moved) {
            player.inventoryMenu.broadcastChanges();
            warn(player);
        }
    }

    /** Если в слоте запрещённое HOLD-оружие — перекладывает его в рюкзак. Возвращает true, если переместил. */
    private static boolean enforceSlot(ServerPlayer player, Inventory inv, int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        EquipmentRoleIndex.LockInfo info = EquipmentRoleIndex.get().lookup(stack);
        if (info == null || info.mode() != EquipmentRoleIndex.LockMode.HOLD) return false;
        if (!isForbidden(player, stack)) return false;
        return moveToBackpack(inv, slot);
    }

    /** Для обработчиков событий применения: запрещён ли предмет; при запрете шлёт троттленное сообщение. */
    public static boolean shouldCancelUse(ServerPlayer player, ItemStack stack) {
        if (!isForbidden(player, stack)) return false;
        warn(player);
        return true;
    }

    /**
     * Перекладывает предмет из слота {@code from} в свободный слот рюкзака (9..35).
     * Возвращает false, если свободных слотов нет.
     */
    private static boolean moveToBackpack(Inventory inv, int from) {
        ItemStack stack = inv.getItem(from);
        if (stack.isEmpty()) return false;
        for (int i = 9; i <= MAIN_INVENTORY_END; i++) {
            if (inv.getItem(i).isEmpty()) {
                inv.setItem(i, stack);
                inv.setItem(from, ItemStack.EMPTY);
                return true;
            }
        }
        return false;
    }

    private static void warn(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = lastWarnTick.get(player.getUUID());
        if (last != null && now - last < WARN_INTERVAL_TICKS) return;
        lastWarnTick.put(player.getUUID(), now);
        player.displayClientMessage(
                Component.translatable("gui.pjmbasemod.equipment.wrong_role"), true);
    }

    /** Очистка состояния троттлинга при выходе игрока. */
    public static void onPlayerLogout(UUID uuid) {
        lastWarnTick.remove(uuid);
    }
}
