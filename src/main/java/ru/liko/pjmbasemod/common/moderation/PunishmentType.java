package ru.liko.pjmbasemod.common.moderation;

import javax.annotation.Nullable;
import java.util.Locale;

/** Тип модераторского наказания. Используется в записях SavedData, командах, эскалации и сети. */
public enum PunishmentType {
    WARN("warn"),
    BAN("ban"),
    TEMPBAN("tempban"),
    KICK("kick"),
    MUTE_VOICE("mute_voice"),
    MUTE_TEXT("mute_text");

    private final String id;

    PunishmentType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Разбор из строки конфига/сети. Принимает алиасы (mute → mute_voice, unban → ban). */
    @Nullable
    public static PunishmentType byId(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "warn" -> WARN;
            case "ban", "permban", "unban" -> BAN;
            case "tempban", "temp_ban" -> TEMPBAN;
            case "kick" -> KICK;
            case "mute", "mute_voice", "mutevoice", "voice" -> MUTE_VOICE;
            case "mute_text", "mutetext", "text" -> MUTE_TEXT;
            default -> null;
        };
    }
}
