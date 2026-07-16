package ru.liko.pjmbasemod.common.compat;

import com.atsuishio.superbwarfare.data.vehicle.subdata.SeatInfo;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Работа с местами техники SuperbWarfare для радиального меню PJM.
 *
 * <p><b>Инвариант:</b> все методы этого класса жёстко ссылаются на типы SuperbWarfare,
 * поэтому класс линкуется только при первом вызове. Вызывать <b>только</b> после того как
 * {@link SbwVehicleClassifier#isVehicleEntity(Entity)} вернул {@code true} — это гарантирует,
 * что SBW загружен. Клиент вызывает read-методы напрямую (сигнатуры используют только ванильные
 * типы), т.к. типы SBW спрятаны внутри тел методов.</p>
 */
public final class SbwVehicleSeats {

    private SbwVehicleSeats() {}

    /** Число мест в технике. */
    public static int seatCount(Entity vehicle) {
        return ((VehicleEntity) vehicle).getMaxPassengers();
    }

    /** Занято ли место с данным индексом. */
    public static boolean seatOccupied(Entity vehicle, int index) {
        return ((VehicleEntity) vehicle).getNthEntity(index) != null;
    }

    /** Локализованная подпись места: водитель / стрелок N / пассажир N. */
    public static Component seatLabel(Entity vehicle, int index) {
        if (index == 0) {
            return Component.translatable("gui.pjmbasemod.radial.seat.driver");
        }
        SeatInfo seat = ((VehicleEntity) vehicle).getSeat(index);
        boolean gunner = seat != null && !seat.weapons().isEmpty();
        String key = gunner ? "gui.pjmbasemod.radial.seat.gunner" : "gui.pjmbasemod.radial.seat.passenger";
        return Component.translatable(key, index + 1);
    }

    /**
     * Сервер: посадить игрока на выбранное место. Проверки замка/команды/поломки — внутри
     * {@link VehicleEntity#tryEnterSeat}. Здесь только гейт наличия SBW и анти-чит по дистанции.
     */
    public static void handleEnterSeat(ServerPlayer player, int vehicleId, int seat) {
        if (player == null) return;
        Entity vehicle = player.level().getEntity(vehicleId);
        if (!SbwVehicleClassifier.isVehicleEntity(vehicle)) return;
        if (player.distanceToSqr(vehicle) > 12.0 * 12.0) return; // радиалка открывается по наводке — рядом
        ((VehicleEntity) vehicle).tryEnterSeat(player, seat);
    }
}
