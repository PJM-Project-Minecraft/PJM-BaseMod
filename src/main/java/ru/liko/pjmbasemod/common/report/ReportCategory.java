package ru.liko.pjmbasemod.common.report;

import java.util.Locale;

/** Категория обращения игрока в администрацию. */
public enum ReportCategory {
    CHEATER,
    BUG,
    INSULT,
    OTHER;

    /** Ключ локализации названия категории. */
    public String langKey() {
        return "gui.pjmbasemod.report.category." + name().toLowerCase(Locale.ROOT);
    }
}
