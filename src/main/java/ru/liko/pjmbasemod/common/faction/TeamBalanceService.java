package ru.liko.pjmbasemod.common.faction;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.teams.Teams;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Мягкий автобалансер команд. При выборе фракции блокирует вступление в команду, которая после
 * этого превысит настроенную долю ({@code teams.balancer.maxSharePercent}) от всех боевых игроков
 * онлайн. Вступление в наименьшую (или равную наименьшей) команду разрешается всегда — баланс
 * остаётся достижимым, а на малом онлайне порог не действует ({@code teams.balancer.minPlayers}).
 * Игрок, уже состоящий в целевой команде (например, меняет только роль), не блокируется.
 */
public final class TeamBalanceService {

    private TeamBalanceService() {}

    /** Итог проверки: разрешено ли вступление и подсказка по наименьшей команде. */
    public record Decision(boolean allowed, String suggestedTeamId) {}

    public static Decision check(MinecraftServer server, ServerPlayer player, String targetTeamId) {
        if (!Config.isTeamBalancerEnabled() || server == null
                || targetTeamId == null || targetTeamId.isBlank()) {
            return new Decision(true, targetTeamId);
        }

        // Остаться в своей команде (смена роли) никогда не ухудшает баланс.
        String currentTeam = Teams.resolvePlayerTeamId(player);
        if (currentTeam != null && currentTeam.equalsIgnoreCase(targetTeamId)) {
            return new Decision(true, targetTeamId);
        }

        // Онлайн-состав боевых команд, исключая самого игрока (он мог быть в другой команде).
        Map<String, Integer> counts = new HashMap<>();
        for (Config.ConfiguredTeam team : Teams.all()) {
            counts.put(team.id().toLowerCase(Locale.ROOT), 0);
        }
        UUID self = player.getUUID();
        int totalExcl = 0;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.getUUID().equals(self)) continue;
            String team = Teams.resolvePlayerTeamId(online);
            if (team == null || !Teams.isCombatTeam(team)) continue;
            String key = team.toLowerCase(Locale.ROOT);
            if (!counts.containsKey(key)) continue;
            counts.merge(key, 1, Integer::sum);
            totalExcl++;
        }

        int minCount = counts.values().stream().min(Integer::compareTo).orElse(0);
        String suggested = smallestTeam(counts, minCount, targetTeamId);

        // На малом онлайне порог отключён — иначе первые игроки не смогли бы выбрать команду.
        if (totalExcl + 1 < Config.getTeamBalancerMinPlayers()) {
            return new Decision(true, targetTeamId);
        }

        int targetCount = counts.getOrDefault(targetTeamId.toLowerCase(Locale.ROOT), 0);
        // Вступление в наименьшую (или равную наименьшей) команду всегда разрешено.
        if (targetCount <= minCount) {
            return new Decision(true, targetTeamId);
        }

        // Иначе проверяем долю после вступления: newCount / newTotal <= maxShare%.
        int newCount = targetCount + 1;
        int newTotal = totalExcl + 1;
        boolean withinShare = newCount * 100 <= Config.getTeamBalancerMaxShare() * newTotal;
        return new Decision(withinShare, suggested);
    }

    /** Первая по порядку конфига команда с минимальным составом (стабильная подсказка). */
    private static String smallestTeam(Map<String, Integer> counts, int minCount, String fallback) {
        for (Config.ConfiguredTeam team : Teams.all()) {
            if (counts.getOrDefault(team.id().toLowerCase(Locale.ROOT), 0) == minCount) {
                return team.id();
            }
        }
        return fallback;
    }
}
