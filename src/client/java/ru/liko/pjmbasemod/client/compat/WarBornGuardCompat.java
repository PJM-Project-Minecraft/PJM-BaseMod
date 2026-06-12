package ru.liko.pjmbasemod.client.compat;

import java.util.UUID;

/**
 * Заглушка интеграции с WarBornGuard. Подключим реальную реализацию,
 * когда античит будет добавлен снова.
 */
public final class WarBornGuardCompat {

    public static final int FLAG_VANISH = 0x01;
    public static final int FLAG_ESP    = 0x02;

    private WarBornGuardCompat() {}

    public static boolean isLocked(UUID playerId) { return false; }
    public static int     getFlags(UUID playerId) { return 0; }
    public static boolean isAdmin(UUID playerId)  { return false; }
}
