package ru.liko.pjmbasemod.common.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Ремонт техники SuperbWarfare для «Ремки»
 * ({@link ru.liko.pjmbasemod.common.blockentity.RemkaBlockEntity}).
 *
 * <p>SuperbWarfare — {@code compileOnly}-зависимость, поэтому все обращения к его типам заперты
 * в этом классе. <b>Ни один</b> метод отсюда нельзя звать без проверки, что мод установлен:
 * обращение к любому из них грузит класс, а его верификация тянет типы SBW. Проверку делает
 * вызывающий ({@code RemkaBlockEntity.SBW_LOADED}) — держать её здесь же было бы бессмысленно.</p>
 */
public final class SbwRepairCompat {

    private SbwRepairCompat() {}

    /** Является ли сущность техникой SuperbWarfare. */
    public static boolean isVehicle(@Nullable Entity entity) {
        return entity instanceof VehicleEntity;
    }

    /**
     * UUID последнего водителя — по нему определяется, чьей фракции техника.
     * {@code null}, если техника ещё никем не управлялась.
     */
    @Nullable
    public static String lastDriverUuid(Entity entity) {
        if (!(entity instanceof VehicleEntity vehicle)) return null;
        String uuid = vehicle.getLastDriverUUID();
        return uuid == null || uuid.isBlank() || "undefined".equals(uuid) ? null : uuid;
    }

    /**
     * Чинит корпус и части техники на долю от их максимума.
     *
     * <p>Обломки ({@code isWreck}) не чиним — их поднимает только ремонтный инструмент игрока.
     * {@code VehicleEntity.heal} не ограничивает здоровье сверху, поэтому зажимаем сами.</p>
     *
     * @return {@code true}, если что-то реально починилось (нужно для звука и партиклов).
     */
    public static boolean repair(Entity entity, float hullPercent, float partPercent) {
        if (!(entity instanceof VehicleEntity vehicle)) return false;
        if (vehicle.isWreck() || vehicle.getHealth() <= 0) return false;

        boolean healed = false;

        float maxHealth = vehicle.getMaxHealth();
        if (vehicle.getHealth() < maxHealth) {
            vehicle.heal(Math.min(hullPercent * maxHealth, maxHealth - vehicle.getHealth()));
            healed = true;
        }

        if (vehicle.hasTurret() && vehicle.getTurretHealth() < vehicle.getTurretMaxHealth()) {
            vehicle.setTurretHealth(raise(vehicle.getTurretHealth(), vehicle.getTurretMaxHealth(), partPercent));
            healed = true;
        }
        if (vehicle.getLeftWheelHealth() < vehicle.getWheelMaxHealth()) {
            vehicle.setLeftWheelHealth(raise(vehicle.getLeftWheelHealth(), vehicle.getWheelMaxHealth(), partPercent));
            healed = true;
        }
        if (vehicle.getRightWheelHealth() < vehicle.getWheelMaxHealth()) {
            vehicle.setRightWheelHealth(raise(vehicle.getRightWheelHealth(), vehicle.getWheelMaxHealth(), partPercent));
            healed = true;
        }
        if (vehicle.getMainEngineHealth() < vehicle.getEngineMaxHealth()) {
            vehicle.setMainEngineHealth(raise(vehicle.getMainEngineHealth(), vehicle.getEngineMaxHealth(), partPercent));
            healed = true;
        }
        if (vehicle.getSubEngineHealth() < vehicle.getEngineMaxHealth()) {
            vehicle.setSubEngineHealth(raise(vehicle.getSubEngineHealth(), vehicle.getEngineMaxHealth(), partPercent));
            healed = true;
        }

        return healed;
    }

    private static float raise(float current, float max, float percent) {
        return Math.min(current + percent * max, max);
    }
}
