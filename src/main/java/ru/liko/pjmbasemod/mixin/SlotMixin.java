package ru.liko.pjmbasemod.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.liko.pjmbasemod.common.inventory.InventoryLimitRegistry;

/**
 * Авторитетная блокировка слотов инвентаря игрока. {@code Slot.mayPickup}/{@code mayPlace}
 * вызываются в {@code AbstractContainerMenu.clicked()} на сервере для ЛЮБОГО типа клика
 * (обычный, shift, drag, double-click, swap, hotbar), поэтому возврат {@code false}
 * полностью запрещает любое взаимодействие с заблокированным слотом — без дюп-окна.
 *
 * <p>Срабатывает только на логическом сервере (проверка через {@code Inventory.player} —
 * он доступен и в {@code mayPlace}, где параметра-игрока нет). В креативе ограничение
 * отключено. На клиенте миксин не вмешивается: визуальный барьер и отмена кликов
 * обслуживаются {@code LockedSlotsClientState}.</p>
 */
@Mixin(Slot.class)
public abstract class SlotMixin {

    @Shadow
    @Final
    public Container container;

    @Shadow
    public abstract int getContainerSlot();

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void pjm_mayPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (pjm_isLocked()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void pjm_mayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (pjm_isLocked()) {
            cir.setReturnValue(false);
        }
    }

    private boolean pjm_isLocked() {
        if (!(this.container instanceof Inventory inventory)) return false;
        Player player = inventory.player;
        // Только логический сервер авторитетен; клиент не имеет серверного конфига.
        if (player == null || player.level().isClientSide()) return false;
        // В креативе ограничение слотов не действует.
        if (player.isCreative()) return false;
        return InventoryLimitRegistry.get().isSlotLocked(getContainerSlot());
    }
}
