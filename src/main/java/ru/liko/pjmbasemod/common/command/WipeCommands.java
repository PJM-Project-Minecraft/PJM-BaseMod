package ru.liko.pjmbasemod.common.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.teams.Teams;
import ru.liko.pjmbasemod.common.wipe.WipeService;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * {@code /pjm wipe ...} — сброс сезонного прогресса игроков. Только OP (level 4),
 * с двухшаговым подтверждением (повтор команды с {@code confirm} в течение 15 сек).
 */
public final class WipeCommands {

    private static final long WINDOW_MS = 15_000L;
    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(String scope, @Nullable String team, long expiresAt) {}

    private static final SuggestionProvider<CommandSourceStack> TEAM_SUGGESTIONS = (ctx, builder) -> {
        for (Config.ConfiguredTeam team : Teams.all()) {
            builder.suggest(team.id());
        }
        return builder.buildFuture();
    };

    private WipeCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("wipe")
                .requires(src -> src.hasPermission(4))
                .then(Commands.literal("ranks")
                        .executes(ctx -> handleRanks(ctx.getSource(), null, false))
                        .then(Commands.literal("confirm")
                                .executes(ctx -> handleRanks(ctx.getSource(), null, true)))
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests(TEAM_SUGGESTIONS)
                                .executes(ctx -> handleRanks(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "team"), false))
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> handleRanks(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team"), true)))))
                .then(Commands.literal("all")
                        .executes(ctx -> handleAll(ctx.getSource(), false))
                        .then(Commands.literal("confirm")
                                .executes(ctx -> handleAll(ctx.getSource(), true))));
    }

    // ---------------------------------------------------------------- ranks

    private static int handleRanks(CommandSourceStack src, @Nullable String team, boolean confirm) {
        MinecraftServer server = src.getServer();
        if (team != null && !isKnownTeam(team)) {
            src.sendFailure(Component.translatable("pjmbasemod.wipe.unknown_team", team));
            return 0;
        }
        String scope = team == null ? "ranks" : "ranks_team";

        if (!confirm) {
            arm(src, scope, team);
            Component warn = team == null
                    ? Component.translatable("pjmbasemod.wipe.warn.ranks")
                    : Component.translatable("pjmbasemod.wipe.warn.ranks_team", team);
            src.sendSuccess(() -> warn.copy().withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        if (!disarm(src, scope, team)) {
            src.sendFailure(Component.translatable("pjmbasemod.wipe.expired"));
            return 0;
        }

        if (team == null) {
            WipeService.wipeRanks(server);
            src.sendSuccess(() -> Component.translatable("pjmbasemod.wipe.success.ranks"), true);
        } else {
            Set<UUID> members = WipeService.resolveTeamMemberUuids(server, team);
            int total = teamMemberCount(server, team);
            int skipped = Math.max(0, total - members.size());
            WipeService.wipeRanksForTeam(server, members);
            String teamFinal = team;
            src.sendSuccess(() -> Component.translatable(
                    "pjmbasemod.wipe.success.ranks_team", teamFinal, members.size()), true);
            if (skipped > 0) {
                src.sendSuccess(() -> Component.translatable(
                        "pjmbasemod.wipe.team_offline_skipped", skipped), false);
            }
        }
        return 1;
    }

    // ---------------------------------------------------------------- all

    private static int handleAll(CommandSourceStack src, boolean confirm) {
        MinecraftServer server = src.getServer();
        if (!confirm) {
            arm(src, "all", null);
            src.sendSuccess(() -> Component.translatable("pjmbasemod.wipe.warn.all")
                    .copy().withStyle(ChatFormatting.RED), false);
            return 1;
        }
        if (!disarm(src, "all", null)) {
            src.sendFailure(Component.translatable("pjmbasemod.wipe.expired"));
            return 0;
        }
        WipeService.wipeAll(server);
        src.sendSuccess(() -> Component.translatable("pjmbasemod.wipe.success.all"), true);
        return 1;
    }

    // ---------------------------------------------------------------- helpers

    private static void arm(CommandSourceStack src, String scope, @Nullable String team) {
        PENDING.put(sourceKey(src), new Pending(scope, team, nowMs() + WINDOW_MS));
    }

    /** true, если для источника есть валидная (не просроченная, совпадающая) заявка; удаляет её. */
    private static boolean disarm(CommandSourceStack src, String scope, @Nullable String team) {
        Pending pending = PENDING.remove(sourceKey(src));
        return pending != null
                && pending.scope().equals(scope)
                && Objects.equals(pending.team(), team)
                && nowMs() < pending.expiresAt();
    }

    private static String sourceKey(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        return player != null ? player.getUUID().toString() : "__console__";
    }

    private static boolean isKnownTeam(String teamId) {
        for (Config.ConfiguredTeam team : Teams.all()) {
            if (team.id().equals(teamId)) return true;
        }
        return false;
    }

    private static int teamMemberCount(MinecraftServer server, String teamId) {
        var team = server.getScoreboard().getPlayerTeam(teamId);
        return team == null ? 0 : team.getPlayers().size();
    }

    private static long nowMs() {
        return System.currentTimeMillis();
    }
}
