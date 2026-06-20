package ru.liko.pjmbasemod.common.warehouse;

import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.common.rank.RankDefinition;
import ru.liko.pjmbasemod.common.rank.RankRegistry;
import ru.liko.pjmbasemod.common.rank.RankService;

/**
 * Резолвит эффективный личный лимит ({@code max}/{@code regen}) очков склада для игрока.
 *
 * <p>Лимит больше НЕ глобальный (секция {@code warehouse} убрана из конфига) — он берётся из текущего
 * ранга игрока ({@link RankDefinition#warehouseBudgetMax()}). Чем выше ранг — тем выше потолок квоты.
 * OP-игроки (уровень прав 2) и ранги с потолком {@code null}/{@code -1} получают безлимитную квоту.</p>
 */
public final class WarehouseBudgetLimits {

    /** Минимальный уровень прав, дающий безлимитную квоту (как у команд /pjm). */
    private static final int OP_LEVEL = 2;

    private WarehouseBudgetLimits() {}

    /**
     * Эффективный лимит игрока. {@code unlimited} — квота не списывается и не показывается числом.
     * При {@code unlimited == false} поля {@code max}/{@code regenPerHour} задают потолок и регенерацию.
     */
    public record Limit(int max, int regenPerHour, boolean unlimited) {
        public static final Limit UNLIMITED = new Limit(0, 0, true);
    }

    public static Limit forPlayer(ServerPlayer player) {
        if (player.hasPermissions(OP_LEVEL)) return Limit.UNLIMITED;

        RankDefinition rank = RankRegistry.get().rankForXp(RankService.xp(player));
        Integer maxBox = rank == null ? null : rank.warehouseBudgetMax();
        // null (не задано) или -1 → безлимит для этого ранга.
        if (maxBox == null || maxBox < 0) return Limit.UNLIMITED;

        int max = maxBox;
        Integer regenBox = rank.warehouseBudgetRegenPerHour();
        int regen = regenBox == null ? max : Math.max(0, regenBox); // не задано → полное восстановление за час
        return new Limit(max, regen, false);
    }
}
