package ru.liko.pjmbasemod.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.liko.pjmbasemod.common.vanish.VanishService;

/**
 * Ваниш на уровне трекинга сущностей: {@code ChunkMap.TrackedEntity.updatePlayer} каждый тик
 * спрашивает {@code broadcastToPlayer} — {@code false} снимает парность (клиент получает
 * remove-entity), {@code true} возвращает её. Поэтому переключение ваниша не требует
 * ручной рассылки пакетов сущности: ванилла синхронизирует сама.
 *
 * <p>Себя ванишнутый видит всегда (вызов с {@code this} ChunkMap отсекает раньше, но
 * проверка ниже страхует). Админы видят ванишнутых наравне с собой — им сущность не скрываем.</p>
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void pjm_hideVanished(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (viewer != self && !VanishService.isAdmin(viewer) && VanishService.isVanished(self)) {
            cir.setReturnValue(false);
        }
    }
}
