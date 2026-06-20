package ru.liko.pjmbasemod.common.rank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RankConfig {

    private boolean enabled = true;
    private int enemyKillXp = 100;
    private int teamKillXp = -300;
    private int sectorCaptureXp = 250;
    private boolean allowDemotion = true;
    private boolean showRankHud = true;
    private boolean showXpPopups = true;
    private boolean showTabPrefix = true;
    private boolean showChatRank = true;
    private List<RankDefinition> ranks = new ArrayList<>();

    public RankConfig() {
    }

    public static RankConfig defaults() {
        RankConfig config = new RankConfig();
        // Квота склада растёт с рангом (regen = null → равен потолку, т.е. полное восстановление за час).
        config.ranks = new ArrayList<>(List.of(
                new RankDefinition("private", "Рядовой", "PVT", 0, "textures/rangs/private.png", "#d8b15f").budget(50, null),
                new RankDefinition("corporal", "Ефрейтор", "CPL", 500, "textures/rangs/corporal.png", "#d8b15f").budget(100, null),
                new RankDefinition("sergeant", "Сержант", "SGT", 1500, "textures/rangs/sergeant.png", "#d8b15f").budget(200, null),
                new RankDefinition("lieutenant", "Лейтенант", "LT", 3500, "textures/rangs/lieutenant.png", "#e8d06a").budget(350, null),
                new RankDefinition("captain", "Капитан", "CPT", 7000, "textures/rangs/captain.png", "#d7d7d7").budget(600, null),
                new RankDefinition("major", "Майор", "MAJ", 12000, "textures/rangs/major.png", "#f0b43a").budget(1000, null)
        ));
        config.normalize();
        return config;
    }

    void normalize() {
        if (ranks == null) ranks = new ArrayList<>();
        ranks.removeIf(rank -> rank == null);
        for (RankDefinition rank : ranks) {
            rank.normalize();
        }
        ranks.sort(Comparator.comparingInt(RankDefinition::minXp).thenComparing(RankDefinition::id));
        if (ranks.isEmpty()) {
            ranks.add(new RankDefinition("private", "Рядовой", "PVT", 0, "textures/rangs/private.png", "#d8b15f"));
        }
        if (ranks.getFirst().minXp() > 0) {
            ranks.add(new RankDefinition("private", "Рядовой", "PVT", 0, "textures/rangs/private.png", "#d8b15f"));
            ranks.sort(Comparator.comparingInt(RankDefinition::minXp).thenComparing(RankDefinition::id));
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public int enemyKillXp() {
        return enemyKillXp;
    }

    public int teamKillXp() {
        return teamKillXp;
    }

    public int sectorCaptureXp() {
        return sectorCaptureXp;
    }

    public boolean allowDemotion() {
        return allowDemotion;
    }

    public boolean showRankHud() {
        return showRankHud;
    }

    public boolean showXpPopups() {
        return showXpPopups;
    }

    public boolean showTabPrefix() {
        return showTabPrefix;
    }

    public boolean showChatRank() {
        return showChatRank;
    }

    public List<RankDefinition> ranks() {
        return List.copyOf(ranks);
    }
}
