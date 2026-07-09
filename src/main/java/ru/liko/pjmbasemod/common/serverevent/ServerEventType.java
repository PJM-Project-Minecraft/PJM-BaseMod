package ru.liko.pjmbasemod.common.serverevent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;

/**
 * Фабрика типа серверного события. Каждая реализация ({@code drone_raid},
 * {@code signal_hunt}, …) регистрируется в {@link ServerEventManager#registerType}.
 * Позволяет менеджеру быть агностичным к конкретным классам событий.
 */
public interface ServerEventType {

    /** Идентификатор типа (ключ в NBT-поле Type). */
    String typeId();

    /** Доступен ли тип в текущем окружении (мод-зависимость, конфиг и т.п.). */
    boolean available(MinecraftServer server);

    /**
     * Создать новое событие (не запускать — менеджер сам вызовет {@code onStart},
     * персист и {@code broadcastZone}).
     *
     * @param pointName конкретная точка/зона или {@code null} — случайная
     * @return результат: событие либо текст ошибки
     */
    CreateResult create(MinecraftServer server, @Nullable String pointName);

    /** Восстановить событие из NBT после рестарта сервера. */
    ServerEvent load(CompoundTag tag);

    /** Результат создания: либо событие, либо текст ошибки (для команды). */
    record CreateResult(@Nullable ServerEvent event, @Nullable String error) {
        public static CreateResult error(String message) { return new CreateResult(null, message); }
        public static CreateResult ok(ServerEvent event) { return new CreateResult(event, null); }
    }
}
