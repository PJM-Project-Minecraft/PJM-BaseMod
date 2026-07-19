package ru.liko.pjmbasemod.common.warehouse;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.liko.pjmbasemod.common.access.AccessPermissions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Серверный запрет <b>ношения</b> закрытых предметов склада: если запись {@code items.json}
 * требует донат-пермишен ({@link WarehouseItemDefinition#permission()}) или Доступ
 * ({@link WarehouseItemDefinition#access()}), игрок без права не может ни подобрать такой
 * предмет, ни держать его в инвентаре — при периодической проверке предмет выбрасывается.
 * Дополняет проверку на выдаче в {@link WarehouseManager}: закрывает пути «передали из рук
 * в руки / сундук / смерть донатера».
 */
public final class RestrictedCarryService {

    private static final long MESSAGE_COOLDOWN_TICKS = 20;
    private static final Map<UUID, Long> LAST_MESSAGE_TICK = new HashMap<>();

    /** Период проверки инвентаря в тиках (раз в секунду — как InventoryLimitService по умолчанию). */
    public static final int ENFORCE_EVERY_TICKS = 20;

    private RestrictedCarryService() {}

    /**
     * Может ли игрок держать этот стек. {@code false} — стек матчится с закрытой записью склада
     * и ни одна матчащаяся запись (в т.ч. свободная-дубликат) игроку не разрешена.
     */
    public static boolean canCarry(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return true;
        Boolean denied = null;
        for (WarehouseItemDefinition def : WarehouseItemRegistry.get().all()) {
            boolean restricted = def.donateRestricted() || def.accessRestricted();
            // ponytail: matchesStack только для закрытых записей — свободный дубликат того же
            // предмета не спасает от конфискации; если такое понадобится, убрать этот continue.
            if (!restricted) continue;
            if (!def.matchesStack(stack)) continue;
            if (allowed(player, def)) return true;
            denied = true;
        }
        return denied == null;
    }

    /** Выбрасывает из инвентаря закрытые предметы, на которые у игрока нет права. */
    public static void enforce(ServerPlayer player) {
        // OP носит что угодно; заодно не сканируем инвентарь админам каждый период.
        if (player.hasPermissions(2)) return;
        Inventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (canCarry(player, stack)) continue;
            inventory.setItem(slot, ItemStack.EMPTY);
            player.drop(stack, false);
            notifyDenied(player);
            changed = true;
        }
        if (changed) player.inventoryMenu.broadcastChanges();
    }

    /** Не засоряет чат, пока игрок стоит на недоступном ему предмете. */
    public static void notifyDenied(ServerPlayer player) {
        long gameTime = player.level().getGameTime();
        Long last = LAST_MESSAGE_TICK.get(player.getUUID());
        if (last != null && gameTime - last < MESSAGE_COOLDOWN_TICKS) return;
        LAST_MESSAGE_TICK.put(player.getUUID(), gameTime);
        player.sendSystemMessage(Component.translatable("message.pjmbasemod.warehouse.carry_restricted"));
    }

    /** Освобождает временное состояние при выходе игрока. */
    public static void onPlayerLogout(UUID playerId) {
        LAST_MESSAGE_TICK.remove(playerId);
    }

    private static boolean allowed(ServerPlayer player, WarehouseItemDefinition def) {
        return WarehouseDonorPermissions.canAccess(player, def)
                && (!def.accessRestricted() || AccessPermissions.has(player, def.access()));
    }
}
