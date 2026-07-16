package ru.liko.pjmbasemod.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.pjmbasemod.common.logging.PjmActionLogger;
import ru.liko.pjmbasemod.common.rank.RankService;

/**
 * Логирование уничтожения техники SuperbWarfare.
 *
 * <p>Техника SBW ({@code VehicleEntity}) — это {@code Entity}, а не {@code LivingEntity},
 * поэтому {@code LivingDeathEvent} для неё не срабатывает. Метод {@code destroy()} вызывается
 * ровно один раз при {@code health <= 0} (охрана {@code !isWreck} в тике сущности) — это точный
 * момент уничтожения, где доступен {@code lastAttacker}.</p>
 *
 * <p>Таргет — строковый (класс SBW отсутствует на этапе компиляции), {@code @Shadow}-метод
 * {@code getLastAttacker()} возвращает ванильный {@code Entity}. Конфиг
 * {@code pjmbasemod.sbw.mixins.json} помечен {@code required=false}: без SuperbWarfare класс
 * никогда не загрузится и миксин просто не применится.</p>
 */
@Mixin(targets = "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity", remap = false)
public abstract class SbwVehicleDestroyMixin {

    /** Kotlin {@code open val lastAttacker: Entity?} → геттер {@code getLastAttacker()}. */
    @Shadow(remap = false)
    public abstract Entity getLastAttacker();

    @Inject(method = "destroy", at = @At("HEAD"), remap = false)
    private void pjm_onVehicleDestroy(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) return;
        PjmActionLogger.instance().logVehicleDestroyed(getLastAttacker(), self);
        RankService.handleVehicleDestroyed(getLastAttacker(), self);
        // Снимаем запись из учёта флота при уничтожении техники
        if (self.getServer() != null) {
            ru.liko.pjmbasemod.common.fleet.VehicleFleetManager.unregister(self.getServer(), self.getUUID());
        }
    }
}
