package ru.liko.pjmbasemod.common.rank;

import javax.annotation.Nullable;

public record RankSnapshot(
        int xp,
        String rankId,
        String displayName,
        String shortName,
        String icon,
        int accentColor,
        int minXp,
        String nextDisplayName,
        int nextMinXp,
        boolean enabled,
        boolean showRankHud,
        boolean showXpPopups,
        boolean showTabPrefix
) {

    public static RankSnapshot of(int xp) {
        RankRegistry registry = RankRegistry.get();
        RankConfig config = registry.config();
        RankDefinition rank = registry.rankForXp(xp);
        RankDefinition next = registry.nextRankAfter(rank);
        return of(xp, config, rank, next);
    }

    public static RankSnapshot of(int xp, RankConfig config, RankDefinition rank, @Nullable RankDefinition next) {
        return new RankSnapshot(
                Math.max(0, xp),
                rank.id(),
                rank.displayName(),
                rank.shortName(),
                rank.icon(),
                rank.accentColorRgb(),
                rank.minXp(),
                next == null ? "" : next.displayName(),
                next == null ? -1 : next.minXp(),
                config.enabled(),
                config.showRankHud(),
                config.showXpPopups(),
                config.showTabPrefix()
        );
    }
}
