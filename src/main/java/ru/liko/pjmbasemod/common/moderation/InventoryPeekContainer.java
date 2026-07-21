package ru.liko.pjmbasemod.common.moderation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Живое зеркало инвентаря игрока в виде {@link Container} на 45 слотов (5 рядов сундука):
 * 0–35 — основной инвентарь, 36–39 — броня, 40 — offhand, 41–44 — пустой хвост до кратности 9.
 * Открывается админу как ванильное меню {@code GENERIC_9x5} — клиентского кода не требуется.
 * Правки идут прямо в инвентарь цели (админ может изъять контрабанду).
 */
public final class InventoryPeekContainer implements Container {

    /** Реальных слотов у ванильного {@link Inventory}: 36 + 4 брони + 1 offhand. */
    private static final int REAL = 41;
    /** 5 рядов сундука. */
    private static final int SIZE = 45;

    private final ServerPlayer target;
    private final Inventory inv;

    public InventoryPeekContainer(ServerPlayer target) {
        this.target = target;
        this.inv = target.getInventory();
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < REAL; i++) {
            if (!inv.getItem(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot < REAL ? inv.getItem(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return slot < REAL ? inv.removeItem(slot, amount) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return slot < REAL ? inv.removeItemNoUpdate(slot) : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < REAL) inv.setItem(slot, stack);
    }

    @Override
    public void setChanged() {
        inv.setChanged();
    }

    /** Пока цель онлайн — меню валидно; на дисконнекте entity удаляется и меню закрывается само. */
    @Override
    public boolean stillValid(Player player) {
        return !target.isRemoved();
    }

    /** Хвостовые 41–44 не принимают предметы — иначе они уходили бы в никуда. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < REAL;
    }

    /** Не даём «очистить» через меню — это стёрло бы инвентарь игрока целиком. */
    @Override
    public void clearContent() {
    }
}
