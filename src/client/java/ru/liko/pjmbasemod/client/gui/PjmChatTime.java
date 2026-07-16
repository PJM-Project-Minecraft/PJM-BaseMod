package ru.liko.pjmbasemod.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Форматирование времени сообщений в чат-переписке (локальная зона клиента). */
public final class PjmChatTime {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private PjmChatTime() {}

    /** Метка «ЧЧ:ММ» по времени сообщения (epoch millis). */
    public static String clock(long epochMillis) {
        return HHMM.format(Instant.ofEpochMilli(epochMillis));
    }
}
