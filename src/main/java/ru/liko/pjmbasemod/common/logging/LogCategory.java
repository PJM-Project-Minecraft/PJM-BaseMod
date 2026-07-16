package ru.liko.pjmbasemod.common.logging;

/**
 * Категория записи в {@code pjmlogs} — задаёт {@code [TAG]}-префикс строки лога.
 *
 * @see PjmActionLogger
 */
public enum LogCategory {
    KILL("KILL"),
    VEHICLE("VEHICLE"),
    JOIN("JOIN"),
    LEFT("LEFT"),
    WAREHOUSE("WAREHOUSE"),
    GARAGE("GARAGE"),
    FRONTLINE("FRONTLINE"),
    BASEZONE("BASEZONE"),
    CHAT("CHAT"),
    COMMAND("COMMAND"),
    FACTION("FACTION"),
    CAPTURE("CAPTURE"),
    REPORT("REPORT"),
    MOD("MOD");

    private final String tag;

    LogCategory(String tag) {
        this.tag = tag;
    }

    /** Тег без квадратных скобок, например {@code KILL}. */
    public String tag() {
        return tag;
    }
}
