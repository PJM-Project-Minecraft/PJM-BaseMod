package ru.liko.pjmbasemod.common.inventory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.LockedSlotsPacket;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Серверная логика ограничения слотов: authoritative-контроль (выталкивание предметов из
 * заблокированных слотов) и синхронизация списка слотов клиенту.
 *
 * <p>Перемещаются только слоты основного инвентаря (индексы {@code 0..35}); броня/offhand
 * как приёмники не используются.</p>
 */
public final class InventoryLimitService {

    /** Последний индекс основного инвентаря (хотбар + 3 ряда). */
    private static final int MAIN_INVENTORY_END = 35;

    private InventoryLimitService() {
    }

    /** Выталкивает предметы из заблокированных слотов игрока в свободные разрешённые слоты. */
    public static void enforce(ServerPlayer player) {
        // В креативе ограничение слотов не действует.
        if (player.isCreative()) return;
        InventoryLimitConfig cfg = InventoryLimitRegistry.get().config();
        if (!cfg.enabled()) return;
        List<Integer> locked = cfg.lockedSlots();
        if (locked.isEmpty()) return;

        Set<Integer> lockedSet = new HashSet<>(locked);
        Inventory inv = player.getInventory();
        boolean changed = false;

        for (int slot : locked) {
            if (slot < 0 || slot >= inv.getContainerSize()) continue;
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty()) continue;

            inv.setItem(slot, ItemStack.EMPTY);
            ItemStack remaining = moveToAllowed(inv, stack, lockedSet);
            if (!remaining.isEmpty()) {
                if (cfg.dropExcessToGround()) {
                    player.drop(remaining, false);
                } else {
                    // Некуда переместить и дроп выключен — оставляем на месте.
                    inv.setItem(slot, remaining);
                }
            }
            changed = true;
        }

        if (changed) {
            player.inventoryMenu.broadcastChanges();
        }
    }

    /**
     * Пытается разложить {@code stack} по разрешённым слотам основного инвентаря: сначала
     * докладывает в подходящие стеки, затем в пустые слоты. Возвращает непоместившийся остаток.
     */
    private static ItemStack moveToAllowed(Inventory inv, ItemStack stack, Set<Integer> lockedSet) {
        // 1. Докладываем в существующие стеки того же предмета.
        for (int i = 0; i <= MAIN_INVENTORY_END; i++) {
            if (lockedSet.contains(i)) continue;
            ItemStack target = inv.getItem(i);
            if (target.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(target, stack)) continue;
            int space = target.getMaxStackSize() - target.getCount();
            if (space <= 0) continue;
            int move = Math.min(space, stack.getCount());
            target.grow(move);
            stack.shrink(move);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }
        // 2. Кладём в пустые разрешённые слоты.
        for (int i = 0; i <= MAIN_INVENTORY_END; i++) {
            if (lockedSet.contains(i)) continue;
            if (!inv.getItem(i).isEmpty()) continue;
            inv.setItem(i, stack.copy());
            stack.setCount(0);
            return ItemStack.EMPTY;
        }
        return stack;
    }

    /** Отправляет игроку актуальный список заблокированных слотов. */
    public static void sync(ServerPlayer player) {
        InventoryLimitConfig cfg = InventoryLimitRegistry.get().config();
        PjmNetworking.sendToPlayer(player,
                new LockedSlotsPacket(cfg.enabled(), cfg.cancelClicks(), cfg.lockedSlots()));
    }

    /** Рассылает обновлённый список заблокированных слотов всем онлайн-игрокам. */
    public static void syncAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }
}
