package ru.liko.pjmbasemod.common.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.compat.TaczWarehouseCompat;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Серверное ограничение переносимого оружия.
 *
 * <p>Стволы TACZ делятся по типу из ганпака на <b>основные</b> и <b>вторичные</b>
 * (секция конфига {@code weapons}, по умолчанию вторичка — {@code pistol}/{@code smg}):
 * снайпер может носить винтовку и пистолет одновременно, но не две винтовки.
 * У SuperbWarfare типов нет — там общий лимит на все стволы.</p>
 */
public final class WeaponLimitService {

    private static final String SBW_GUN_ITEM_CLASS = "com.atsuishio.superbwarfare.item.gun.GunItem";
    private static final long MESSAGE_COOLDOWN_TICKS = 20;
    private static final Map<UUID, Long> LAST_LIMIT_MESSAGE_TICK = new HashMap<>();

    private WeaponLimitService() {
    }

    /** Возвращает категорию оружия, к которой относится стек, либо {@code null}. */
    @Nullable
    public static WeaponType weaponType(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (TaczWarehouseCompat.isGun(stack)) {
            // gunType == null (ганпак не отдал индекс) — считаем основным: строгий вариант,
            // иначе неопознанный ствол ездил бы в слоте вторички без ограничения.
            return Config.isSecondaryGunType(TaczWarehouseCompat.gunType(stack))
                    ? WeaponType.TACZ_SECONDARY
                    : WeaponType.TACZ_PRIMARY;
        }
        return isSuperbWarfareGun(stack) ? WeaponType.SUPERB_WARFARE : null;
    }

    /** Можно ли добавить этот стек в инвентарь без нарушения лимита. */
    public static boolean canCarry(ServerPlayer player, ItemStack stack) {
        if (isExempt(player)) return true;
        WeaponType type = weaponType(stack);
        return type == null || count(player.getInventory(), type) < limitFor(type);
    }

    /** Админы (OP) носят сколько угодно стволов — лимит только для обычных игроков. */
    private static boolean isExempt(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    /** Удаляет лишние стволы, которые могли попасть в инвентарь не через подбор предмета. */
    public static void enforce(ServerPlayer player) {
        if (isExempt(player)) return;
        Inventory inventory = player.getInventory();
        Map<WeaponType, Integer> carried = new EnumMap<>(WeaponType.class);
        boolean changed = false;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            WeaponType type = weaponType(stack);
            if (type == null) continue;

            int already = carried.getOrDefault(type, 0);
            if (already < limitFor(type)) {
                carried.put(type, already + 1);
                continue;
            }

            inventory.setItem(slot, ItemStack.EMPTY);
            player.drop(stack, false);
            notifyLimit(player, type);
            changed = true;
        }

        if (changed) player.inventoryMenu.broadcastChanges();
    }

    /** Сколько стволов этой категории разрешено нести. */
    private static int limitFor(WeaponType type) {
        return switch (type) {
            case TACZ_PRIMARY -> Config.getWeaponsMaxPrimary();
            case TACZ_SECONDARY -> Config.getWeaponsMaxSecondary();
            case SUPERB_WARFARE -> Config.getWeaponsMaxSuperbWarfare();
        };
    }

    public static Component limitMessage(WeaponType type) {
        return Component.translatable("message.pjmbasemod.weapon_limit." + type.translationKey);
    }

    /** Не засоряет чат, пока игрок стоит на недоступном ему выпавшем оружии. */
    public static void notifyLimit(ServerPlayer player, WeaponType type) {
        long gameTime = player.level().getGameTime();
        Long lastTime = LAST_LIMIT_MESSAGE_TICK.get(player.getUUID());
        if (lastTime != null && gameTime - lastTime < MESSAGE_COOLDOWN_TICKS) return;

        LAST_LIMIT_MESSAGE_TICK.put(player.getUUID(), gameTime);
        player.sendSystemMessage(limitMessage(type));
    }

    /** Освобождает временное состояние при выходе игрока. */
    public static void onPlayerLogout(UUID playerId) {
        LAST_LIMIT_MESSAGE_TICK.remove(playerId);
    }

    private static int count(Inventory inventory, WeaponType expected) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (weaponType(inventory.getItem(slot)) == expected) count++;
        }
        return count;
    }

    /** Проверка по имени базового класса, чтобы SuperbWarfare оставался опциональной зависимостью. */
    private static boolean isSuperbWarfareGun(ItemStack stack) {
        for (Class<?> type = stack.getItem().getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            if (SBW_GUN_ITEM_CLASS.equals(type.getName())) return true;
        }
        return false;
    }

    public enum WeaponType {
        TACZ_PRIMARY("tacz_primary"),
        TACZ_SECONDARY("tacz_secondary"),
        SUPERB_WARFARE("superbwarfare");

        private final String translationKey;

        WeaponType(String translationKey) {
            this.translationKey = translationKey;
        }
    }
}
