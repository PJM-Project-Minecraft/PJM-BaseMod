package ru.liko.pjmbasemod.common.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import ru.liko.pjmbasemod.common.moderation.DurationParser;
import ru.liko.pjmbasemod.common.moderation.ModerationPermissions;
import ru.liko.pjmbasemod.common.moderation.ModerationSavedData;
import ru.liko.pjmbasemod.common.moderation.ModerationSavedData.HistoryEntry;
import ru.liko.pjmbasemod.common.moderation.ModerationSavedData.ModerationProfile;
import ru.liko.pjmbasemod.common.moderation.ModerationSavedData.WarnEntry;
import ru.liko.pjmbasemod.common.moderation.ModerationService;
import ru.liko.pjmbasemod.common.moderation.PunishmentType;
import ru.liko.pjmbasemod.common.network.handler.ServerPacketHandlers;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Дерево команд {@code /pjm mod ...} — варны, баны, кики, муты войса/текста, история, GUI.
 * Executor-методы {@code executeBan/Kick/Pardon/BanIp} статические и публичные —
 * их переиспользует {@link VanillaModerationOverride} при перехвате ванильных команд.
 */
public final class ModerationCommands {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private ModerationCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mod")
                .requires(src -> can(src, ModerationPermissions.HISTORY)
                        || can(src, ModerationPermissions.WARN) || can(src, ModerationPermissions.BAN)
                        || can(src, ModerationPermissions.KICK) || can(src, ModerationPermissions.MUTE_VOICE)
                        || can(src, ModerationPermissions.MUTE_TEXT) || can(src, ModerationPermissions.GUI))
                // warn <name> <reason>
                .then(Commands.literal("warn")
                        .requires(src -> can(src, ModerationPermissions.WARN))
                        .then(nameArg().then(reasonArg((ctx, name, reason) -> {
                            ResolvedTarget t = resolve(ctx.getSource(), name);
                            return t == null ? 0 : executeWarn(ctx.getSource(), t, reason);
                        }))))
                // ban / tempban <name> <duration> <reason>
                .then(banLike("ban"))
                .then(banLike("tempban"))
                // kick <name> <reason>
                .then(Commands.literal("kick")
                        .requires(src -> can(src, ModerationPermissions.KICK))
                        .then(nameArg().then(reasonArg((ctx, name, reason) ->
                                executeKick(ctx.getSource(), name, reason)))))
                // pardon / unban <name>
                .then(pardonLike("pardon"))
                .then(pardonLike("unban"))
                // banip <name|ip> <duration> <reason>
                .then(Commands.literal("banip")
                        .requires(src -> can(src, ModerationPermissions.BAN))
                        .then(Commands.argument("target", StringArgumentType.word())
                                .then(Commands.argument("duration", StringArgumentType.word())
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> executeBanIp(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "target"),
                                                        StringArgumentType.getString(ctx, "duration"),
                                                        StringArgumentType.getString(ctx, "reason")))))))
                // pardonip <ip>
                .then(Commands.literal("pardonip")
                        .requires(src -> can(src, ModerationPermissions.BAN))
                        .then(Commands.argument("ip", StringArgumentType.word())
                                .executes(ctx -> executePardonIp(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "ip")))))
                // mutevoice / unmutevoice
                .then(muteCmd("mutevoice", true))
                .then(unmuteCmd("unmutevoice", true))
                // mutetext / unmutetext
                .then(muteCmd("mutetext", false))
                .then(unmuteCmd("unmutetext", false))
                // warns / clearwarns
                .then(Commands.literal("warns")
                        .requires(src -> can(src, ModerationPermissions.HISTORY))
                        .then(nameArg().executes(ctx -> withTarget(ctx, ModerationCommands::showWarns))))
                .then(Commands.literal("clearwarns")
                        .requires(src -> can(src, ModerationPermissions.WARN))
                        .then(nameArg().executes(ctx -> withTarget(ctx, ModerationCommands::clearWarns))))
                // history / info
                .then(Commands.literal("history")
                        .requires(src -> can(src, ModerationPermissions.HISTORY))
                        .then(nameArg().executes(ctx -> withTarget(ctx, ModerationCommands::showHistory))))
                .then(Commands.literal("info")
                        .requires(src -> can(src, ModerationPermissions.HISTORY))
                        .then(nameArg().executes(ctx -> withTarget(ctx, ModerationCommands::showInfo))))
                // gui
                .then(Commands.literal("gui")
                        .requires(src -> can(src, ModerationPermissions.GUI))
                        .executes(ctx -> openGui(ctx.getSource())));
    }

    // ---------------------------------------------------------------- построители веток

    private static LiteralArgumentBuilder<CommandSourceStack> banLike(String literal) {
        return Commands.literal(literal)
                .requires(src -> can(src, ModerationPermissions.BAN))
                .then(nameArg().then(Commands.argument("duration", StringArgumentType.word())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeBan(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "duration"),
                                        StringArgumentType.getString(ctx, "reason"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> pardonLike(String literal) {
        return Commands.literal(literal)
                .requires(src -> can(src, ModerationPermissions.BAN))
                .then(nameArg().executes(ctx -> withTarget(ctx, ModerationCommands::executePardon)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> muteCmd(String literal, boolean voice) {
        PermissionNode<Boolean> node = voice ? ModerationPermissions.MUTE_VOICE : ModerationPermissions.MUTE_TEXT;
        return Commands.literal(literal)
                .requires(src -> can(src, node))
                .then(nameArg().then(Commands.argument("duration", StringArgumentType.word())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeMute(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "duration"),
                                        StringArgumentType.getString(ctx, "reason"), voice)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> unmuteCmd(String literal, boolean voice) {
        PermissionNode<Boolean> node = voice ? ModerationPermissions.MUTE_VOICE : ModerationPermissions.MUTE_TEXT;
        return Commands.literal(literal)
                .requires(src -> can(src, node))
                .then(nameArg().executes(ctx -> withTarget(ctx, (source, t) -> executeUnmute(source, t, voice))));
    }

    // ---------------------------------------------------------------- executor-методы (публичные для override)

    public static int executeWarn(CommandSourceStack source, ResolvedTarget t, String reason) {
        PunishmentType applied = ModerationService.warn(source.getServer(), t.id(), t.name(), reason, moderator(source));
        MutableComponent base = Component.translatable("pjmbasemod.moderation.cmd.warn.success", t.name());
        if (applied != null) {
            base = base.append(Component.translatable("pjmbasemod.moderation.cmd.warn.escalation", applied.id()));
        }
        Component msg = base;
        source.sendSuccess(() -> msg, true);
        return 1;
    }

    public static int executeBan(CommandSourceStack source, String name, String durationRaw, String reason) {
        ResolvedTarget t = resolve(source, name);
        if (t == null) return 0;
        long dur = DurationParser.parseToMillis(durationRaw);
        if (dur == DurationParser.INVALID) {
            source.sendFailure(Component.translatable("pjmbasemod.moderation.cmd.invalid_duration", durationRaw));
            return 0;
        }
        ModerationService.applyBan(source.getServer(), t.id(), t.name(), dur, reason, moderator(source));
        Component msg = Component.translatable("pjmbasemod.moderation.cmd.ban.success", t.name(), DurationParser.format(dur));
        source.sendSuccess(() -> msg, true);
        return 1;
    }

    public static int executeKick(CommandSourceStack source, String name, String reason) {
        MinecraftServer server = source.getServer();
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online == null) {
            source.sendFailure(Component.translatable("pjmbasemod.moderation.cmd.player_offline", name));
            return 0;
        }
        ModerationService.kick(online, reason, moderator(source));
        Component msg = Component.translatable("pjmbasemod.moderation.cmd.kick.success", name);
        source.sendSuccess(() -> msg, true);
        return 1;
    }

    public static int executePardon(CommandSourceStack source, ResolvedTarget t) {
        boolean ok = ModerationService.pardon(source.getServer(), t.id(), t.name(), moderator(source));
        if (ok) {
            Component msg = Component.translatable("pjmbasemod.moderation.cmd.pardon.success", t.name());
            source.sendSuccess(() -> msg, true);
        } else {
            source.sendFailure(Component.translatable("pjmbasemod.moderation.cmd.pardon.no_ban", t.name()));
        }
        return ok ? 1 : 0;
    }

    public static int executeBanIp(CommandSourceStack source, String target, String durationRaw, String reason) {
        long dur = DurationParser.parseToMillis(durationRaw);
        if (dur == DurationParser.INVALID) {
            source.sendFailure(Component.translatable("pjmbasemod.moderation.cmd.invalid_duration.short", durationRaw));
            return 0;
        }
        String ip = target;
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(target);
        if (online != null) ip = online.getIpAddress();
        // Анти-футган: за прокси getIpAddress() у всех одинаков — бан такого IP выкинет полсервера.
        long sharing = ModerationService.onlinePlayersWithIp(source.getServer(), ip);
        if (sharing > 1) {
            final String sharedIp = ip;
            source.sendFailure(Component.translatable("pjmbasemod.moderation.cmd.banip.shared", sharedIp, sharing));
            return 0;
        }
        ModerationService.applyIpBan(source.getServer(), ip, dur, reason, moderator(source));
        final String bannedIp = ip;
        Component msg = Component.translatable("pjmbasemod.moderation.cmd.banip.success", bannedIp, DurationParser.format(dur));
        source.sendSuccess(() -> msg, true);
        return 1;
    }

    public static int executePardonIp(CommandSourceStack source, String ip) {
        boolean ok = ModerationService.pardonIp(source.getServer(), ip);
        if (ok) {
            Component msg = Component.translatable("pjmbasemod.moderation.cmd.pardonip.success", ip);
            source.sendSuccess(() -> msg, true);
        } else {
            source.sendFailure(Component.translatable("pjmbasemod.moderation.cmd.pardonip.no_ban", ip));
        }
        return ok ? 1 : 0;
    }

    private static int executeMute(CommandSourceStack source, String name, String durationRaw, String reason, boolean voice) {
        ResolvedTarget t = resolve(source, name);
        if (t == null) return 0;
        long dur = DurationParser.parseToMillis(durationRaw);
        if (dur == DurationParser.INVALID) {
            source.sendFailure(Component.translatable("pjmbasemod.moderation.cmd.invalid_duration.short", durationRaw));
            return 0;
        }
        String key = voice ? "pjmbasemod.moderation.cmd.mute.voice.success" : "pjmbasemod.moderation.cmd.mute.text.success";
        if (voice) ModerationService.muteVoice(source.getServer(), t.id(), t.name(), dur, reason, moderator(source));
        else ModerationService.muteText(source.getServer(), t.id(), t.name(), dur, reason, moderator(source));
        Component msg = Component.translatable(key, t.name(), DurationParser.format(dur));
        source.sendSuccess(() -> msg, true);
        return 1;
    }

    private static int executeUnmute(CommandSourceStack source, ResolvedTarget t, boolean voice) {
        boolean ok = voice
                ? ModerationService.unmuteVoice(source.getServer(), t.id(), t.name(), moderator(source))
                : ModerationService.unmuteText(source.getServer(), t.id(), t.name(), moderator(source));
        String successKey = voice ? "pjmbasemod.moderation.cmd.unmute.voice.success" : "pjmbasemod.moderation.cmd.unmute.text.success";
        String noMuteKey  = voice ? "pjmbasemod.moderation.cmd.unmute.voice.no_mute" : "pjmbasemod.moderation.cmd.unmute.text.no_mute";
        if (ok) {
            Component msg = Component.translatable(successKey, t.name());
            source.sendSuccess(() -> msg, true);
        } else {
            source.sendFailure(Component.translatable(noMuteKey, t.name()));
        }
        return ok ? 1 : 0;
    }

    // ---------------------------------------------------------------- просмотр

    private static int showWarns(CommandSourceStack source, ResolvedTarget t) {
        ModerationProfile p = ModerationSavedData.get(source.getServer()).profile(t.id());
        if (p == null || p.warns().isEmpty()) {
            Component empty = Component.translatable("pjmbasemod.moderation.cmd.warns.empty", t.name());
            source.sendSuccess(() -> empty, false);
            return 1;
        }
        Component header = Component.translatable("pjmbasemod.moderation.cmd.warns.header", t.name(), p.warnCount());
        source.sendSuccess(() -> header, false);
        for (WarnEntry w : p.warns()) {
            source.sendSuccess(() -> Component.translatable("pjmbasemod.moderation.cmd.warns.entry",
                    TS_FMT.format(Instant.ofEpochMilli(w.issuedAtMs())), w.reason(), w.moderatorName()), false);
        }
        return 1;
    }

    private static int clearWarns(CommandSourceStack source, ResolvedTarget t) {
        int n = ModerationSavedData.get(source.getServer()).clearWarns(t.id());
        Component msg = Component.translatable("pjmbasemod.moderation.cmd.clearwarns.success", t.name(), n);
        source.sendSuccess(() -> msg, true);
        return 1;
    }

    private static int showHistory(CommandSourceStack source, ResolvedTarget t) {
        ModerationProfile p = ModerationSavedData.get(source.getServer()).profile(t.id());
        if (p == null || p.history().isEmpty()) {
            Component empty = Component.translatable("pjmbasemod.moderation.cmd.history.empty", t.name());
            source.sendSuccess(() -> empty, false);
            return 1;
        }
        Component header = Component.translatable("pjmbasemod.moderation.cmd.history.header", t.name());
        source.sendSuccess(() -> header, false);
        for (HistoryEntry h : p.history()) {
            source.sendSuccess(() -> Component.translatable("pjmbasemod.moderation.cmd.history.entry",
                    TS_FMT.format(Instant.ofEpochMilli(h.timestampMs())),
                    h.type().id(), h.action(),
                    h.reason().isBlank() ? "" : h.reason(),
                    h.moderatorName()), false);
        }
        return 1;
    }

    private static int showInfo(CommandSourceStack source, ResolvedTarget t) {
        ModerationProfile p = ModerationSavedData.get(source.getServer()).profile(t.id());
        boolean banned = ModerationService.isBanned(source.getServer(), t.id());
        boolean vMute = ModerationService.isVoiceMuted(source.getServer(), t.id());
        boolean tMute = ModerationService.isTextMuted(source.getServer(), t.id());
        int warns = p == null ? 0 : p.warnCount();
        String yes = Component.translatable("pjmbasemod.moderation.cmd.yes").getString();
        String no = Component.translatable("pjmbasemod.moderation.cmd.no").getString();
        Component msg = Component.translatable("pjmbasemod.moderation.cmd.info",
                t.name(),
                banned ? yes : no,
                vMute ? yes : no,
                tMute ? yes : no,
                warns);
        source.sendSuccess(() -> msg, false);
        return 1;
    }

    private static int openGui(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("pjmbasemod.moderation.cmd.gui.only_player"));
            return 0;
        }
        ServerPacketHandlers.sendModerationScreen(player);
        return 1;
    }

    // ---------------------------------------------------------------- резолв целей / права / DSL

    /** Разрешённая цель: UUID + актуальный ник. */
    public record ResolvedTarget(UUID id, String name) {}

    /** Онлайн → кэш профилей → last known name из SavedData. Отправляет ошибку и возвращает null, если не найдено. */
    @Nullable
    public static ResolvedTarget resolve(CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return new ResolvedTarget(online.getUUID(), online.getGameProfile().getName());
        Optional<GameProfile> profile = server.getProfileCache() == null
                ? Optional.empty() : server.getProfileCache().get(name);
        if (profile.isPresent()) return new ResolvedTarget(profile.get().getId(), profile.get().getName());
        ModerationSavedData data = ModerationSavedData.get(server);
        for (Map.Entry<UUID, ModerationProfile> e : data.entries().entrySet()) {
            if (e.getValue().lastKnownName().equalsIgnoreCase(name)) {
                return new ResolvedTarget(e.getKey(), e.getValue().lastKnownName());
            }
        }
        source.sendFailure(Component.translatable("pjmbasemod.moderation.cmd.player_not_found", name));
        return null;
    }

    /** Резолвит аргумент name и выполняет действие; отправляет ошибку и возвращает 0, если не найдено. */
    private static int withTarget(CommandContext<CommandSourceStack> ctx, TargetAction action) {
        ResolvedTarget t = resolve(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
        return t == null ? 0 : action.run(ctx.getSource(), t);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> nameArg() {
        return Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_TARGETS);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> reasonArg(ReasonAction action) {
        return Commands.argument("reason", StringArgumentType.greedyString()).executes(ctx ->
                action.run(ctx, StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "reason")));
    }

    private static boolean can(CommandSourceStack source, PermissionNode<Boolean> node) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return ModerationPermissions.can(player, node);
        }
        return source.hasPermission(2);
    }

    @Nullable
    private static ServerPlayer moderator(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer p ? p : null;
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TARGETS = (ctx, builder) -> {
        MinecraftServer server = ctx.getSource().getServer();
        if (server != null) {
            server.getPlayerList().getPlayers().forEach(p -> builder.suggest(p.getGameProfile().getName()));
            for (ModerationProfile p : ModerationSavedData.get(server).entries().values()) {
                builder.suggest(p.lastKnownName());
            }
        }
        return builder.buildFuture();
    };

    @FunctionalInterface
    private interface TargetAction {
        int run(CommandSourceStack source, ResolvedTarget target);
    }

    @FunctionalInterface
    private interface ReasonAction {
        int run(CommandContext<CommandSourceStack> ctx, String name, String reason);
    }
}
