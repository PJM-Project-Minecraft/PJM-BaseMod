package ru.liko.pjmbasemod.common.serverevent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;

/**
 * Активное серверное событие. Тикается менеджером раз в секунду (20 тиков).
 * Реализации сериализуются в NBT и восстанавливаются после рестарта сервера
 * (см. {@link ServerEventManager}).
 */
public interface ServerEvent {

    /** Идентификатор типа события (ключ загрузчика из NBT). */
    String typeId();

    /** Вызывается при запуске нового события (не при восстановлении из NBT): уведомления и т.п. */
    void onStart(MinecraftServer server);

    /** Секундный тик события. */
    void tick(MinecraftServer server);

    /** true — событие завершилось, менеджер вызовет {@link #onStop} и снимет его. */
    boolean isFinished();

    /** Завершение: {@code aborted} — остановлено вручную/по таймауту, не штатно. */
    void onStop(MinecraftServer server, boolean aborted);

    CompoundTag save();

    /** Однострочный статус для /pjm event status. */
    String statusLine();

    /** Зона события для карты (null — не отображается). */
    @Nullable
    EventZone zone();

    /** Данные зоны события для синхронизации на карту клиента. */
    record EventZone(String pointName, String dimension, int centerX, int centerY, int centerZ, int radius) {}
}
