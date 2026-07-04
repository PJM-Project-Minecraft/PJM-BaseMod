package ru.liko.pjmbasemod.common.moderation;

import java.util.Locale;

/**
 * Разбор и форматирование длительностей наказаний.
 * Вход: {@code "30m"}, {@code "1h30m"}, {@code "1d"}, {@code "2w"}, {@code "45s"},
 * {@code "permanent"}/{@code "perm"}/{@code "0"} → перманент. Возврат — миллисекунды
 * ({@link #PERMANENT} для вечного) или {@link #INVALID} при ошибке разбора.
 */
public final class DurationParser {

    /** Значение expiresAt для перманентного наказания. */
    public static final long PERMANENT = Long.MAX_VALUE;
    /** Возвращается при некорректном вводе. */
    public static final long INVALID = -1L;

    private static final long SEC = 1000L;
    private static final long MIN = 60L * SEC;
    private static final long HOUR = 60L * MIN;
    private static final long DAY = 24L * HOUR;
    private static final long WEEK = 7L * DAY;

    private DurationParser() {}

    /** @return длительность в мс, {@link #PERMANENT} для вечного, {@link #INVALID} при ошибке. */
    public static long parseToMillis(String raw) {
        if (raw == null) return INVALID;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return INVALID;
        if (s.equals("permanent") || s.equals("perm") || s.equals("perma") || s.equals("0") || s.equals("forever")) {
            return PERMANENT;
        }
        long total = 0L;
        long number = 0L;
        boolean sawDigit = false;
        boolean sawUnit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                number = number * 10 + (c - '0');
                sawDigit = true;
            } else {
                if (!sawDigit) return INVALID;
                long unit = switch (c) {
                    case 's' -> SEC;
                    case 'm' -> MIN;
                    case 'h' -> HOUR;
                    case 'd' -> DAY;
                    case 'w' -> WEEK;
                    default -> INVALID;
                };
                if (unit == INVALID) return INVALID;
                total += number * unit;
                number = 0L;
                sawDigit = false;
                sawUnit = true;
            }
        }
        // Голое число без суффикса трактуем как минуты (частый ввод модератора).
        if (sawDigit) {
            total += number * MIN;
            sawUnit = true;
        }
        if (!sawUnit || total <= 0L) return INVALID;
        return total;
    }

    /** Человекочитаемое представление длительности: {@code "1d 2h"}, {@code "30m"}, {@code "вечно"}. */
    public static String format(long millis) {
        if (millis == PERMANENT) return "вечно";
        if (millis <= 0L) return "0с";
        long d = millis / DAY;
        long h = (millis % DAY) / HOUR;
        long m = (millis % HOUR) / MIN;
        long sec = (millis % MIN) / SEC;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append('д').append(' ');
        if (h > 0) sb.append(h).append('ч').append(' ');
        if (m > 0) sb.append(m).append('м').append(' ');
        if (sec > 0 && d == 0 && h == 0) sb.append(sec).append('с').append(' ');
        String out = sb.toString().trim();
        return out.isEmpty() ? "0с" : out;
    }

    /** Абсолютное время истечения от текущего момента, либо {@link #PERMANENT}. */
    public static long expiresAtFromNow(long durationMillis) {
        if (durationMillis == PERMANENT) return PERMANENT;
        long now = System.currentTimeMillis();
        long exp = now + durationMillis;
        return exp < now ? PERMANENT : exp; // overflow-guard
    }
}
