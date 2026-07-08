package ru.liko.pjmbasemod.common.moderation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.moderation.ModerationSavedData.BanEntry;
import ru.liko.pjmbasemod.common.moderation.ModerationSavedData.HistoryEntry;
import ru.liko.pjmbasemod.common.moderation.ModerationSavedData.ModerationProfile;
import ru.liko.pjmbasemod.common.moderation.ModerationSavedData.MuteEntry;
import ru.liko.pjmbasemod.common.moderation.ModerationSavedData.WarnEntry;
import ru.liko.pjmbasemod.common.voice.VoicechatBridge;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Центральная логика модерации — единственная точка применения/снятия наказаний.
 * Команды, GUI-экшены и автоэскалация варнов вызывают только её.
 * Время в мс эпохи; enforcement банов — на входе игрока, войс-мут — через {@link VoicechatBridge},
 * текст-мут — проверкой в чат-событии.
 */
public final class ModerationService {

    private static final String CONSOLE = "Console";
    private static long lastMuteSweepMs = 0L;

    private ModerationService() {}

    // ---------------------------------------------------------------- варны + автоэскалация

    /** Выдать варн. Возвращает применённое автонаказание (или null), чтобы вызвавший показал итог. */
    @Nullable
    public static PunishmentType warn(MinecraftServer server, UUID target, String targetName,
                                      String reason, @Nullable ServerPlayer moderator) {
        ModerationSavedData data = ModerationSavedData.get(server);
        long now = System.currentTimeMillis();
        data.addWarn(target, targetName, new WarnEntry(reason, modId(moderator), modName(moderator), now));
        data.addHistory(target, targetName, new HistoryEntry(PunishmentType.WARN, "apply", reason, modName(moderator), now, 0L));

        int count = activeWarnCount(data.profile(target));
        notifyTarget(server, target, Component.translatable("pjmbasemod.moderation.warn.notify", reason, count));
        broadcast(server, Component.translatable("pjmbasemod.moderation.warn.broadcast", targetName, reason));

        WarnThreshold threshold = matchingThreshold(count);
        if (threshold == null) return null;

        // Автонаказание по достигнутому порогу.
        String escReason = Component.translatable("pjmbasemod.moderation.warn.escalation_reason", count).getString();
        switch (threshold.action()) {
            case MUTE_VOICE -> muteVoice(server, target, targetName, threshold.durationMs(), escReason, moderator);
            case MUTE_TEXT -> muteText(server, target, targetName, threshold.durationMs(), escReason, moderator);
            case TEMPBAN, BAN -> applyBan(server, target, targetName, threshold.durationMs(), escReason, moderator);
            case KICK -> {
                ServerPlayer online = server.getPlayerList().getPlayer(target);
                if (online != null) kick(online, escReason, moderator);
            }
            case WARN -> { /* нет смысла эскалировать варн варном */ }
        }
        return threshold.action();
    }

    private static int activeWarnCount(@Nullable ModerationProfile profile) {
        if (profile == null) return 0;
        int decayDays = Config.getModerationWarnDecayDays();
        if (decayDays <= 0) return profile.warnCount();
        long cutoff = System.currentTimeMillis() - (long) decayDays * 24L * 60L * 60L * 1000L;
        int count = 0;
        for (WarnEntry w : profile.warns()) {
            if (w.issuedAtMs() >= cutoff) count++;
        }
        return count;
    }

    // ---------------------------------------------------------------- баны

    public static void applyBan(MinecraftServer server, UUID target, String targetName,
                                long durationMs, String reason, @Nullable ServerPlayer moderator) {
        ModerationSavedData data = ModerationSavedData.get(server);
        long now = System.currentTimeMillis();
        long expires = DurationParser.expiresAtFromNow(durationMs);
        BanEntry ban = new BanEntry(reason, modId(moderator), modName(moderator), now, expires);
        data.setBan(target, targetName, ban);
        data.addHistory(target, targetName, new HistoryEntry(
                expires == DurationParser.PERMANENT ? PunishmentType.BAN : PunishmentType.TEMPBAN,
                "apply", reason, modName(moderator), now, durationMs));

        ServerPlayer online = server.getPlayerList().getPlayer(target);
        if (online != null) online.connection.disconnect(banScreen(ban));

        broadcast(server, Component.translatable("pjmbasemod.moderation.ban.broadcast", targetName,
                DurationParser.format(durationMs), reason));
        ru.liko.pjmbasemod.common.logging.PjmActionLogger.instance().logSubsystem(
                ru.liko.pjmbasemod.common.logging.LogCategory.MOD,
                modName(moderator) + " забанил " + targetName + " (" + DurationParser.format(durationMs) + "): " + reason);
    }

    /**
     * Сколько РАЗНЫХ онлайн-игроков сейчас подключены с этого IP. За прокси
     * (BungeeCord/Velocity/NAT без ip-forwarding) {@link ServerPlayer#getIpAddress()} возвращает
     * общий IP прокси для всех — значение &gt; 1 является его сигнатурой. Используется, чтобы
     * запретить IP-бан общего адреса (иначе бан одного вычистит полсервера).
     */
    public static long onlinePlayersWithIp(MinecraftServer server, String ip) {
        if (server == null || ip == null || ip.isBlank()) return 0L;
        return server.getPlayerList().getPlayers().stream()
                .filter(p -> ip.equals(p.getIpAddress()))
                .map(ServerPlayer::getUUID)
                .distinct()
                .count();
    }

    public static void applyIpBan(MinecraftServer server, String ip, long durationMs,
                                  String reason, @Nullable ServerPlayer moderator) {
        if (ip == null || ip.isBlank()) return;
        ModerationSavedData data = ModerationSavedData.get(server);
        long now = System.currentTimeMillis();
        long expires = DurationParser.expiresAtFromNow(durationMs);
        BanEntry ban = new BanEntry(reason, modId(moderator), modName(moderator), now, expires);
        data.setIpBan(ip, ban);
        // Кикнуть всех онлайн с этим IP.
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
            if (ip.equals(p.getIpAddress())) p.connection.disconnect(banScreen(ban));
        }
        broadcast(server, Component.translatable("pjmbasemod.moderation.ipban.broadcast", ip, reason));
        ru.liko.pjmbasemod.common.logging.PjmActionLogger.instance().logSubsystem(
                ru.liko.pjmbasemod.common.logging.LogCategory.MOD,
                modName(moderator) + " забанил IP " + ip + ": " + reason);
    }

    /** @return true, если бан был снят. */
    public static boolean pardon(MinecraftServer server, UUID target, String targetName, @Nullable ServerPlayer moderator) {
        ModerationSavedData data = ModerationSavedData.get(server);
        BanEntry removed = data.clearBan(target);
        if (removed == null) return false;
        data.addHistory(target, targetName, new HistoryEntry(PunishmentType.BAN, "revoke", "", modName(moderator),
                System.currentTimeMillis(), 0L));
        broadcast(server, Component.translatable("pjmbasemod.moderation.pardon.broadcast", targetName));
        return true;
    }

    public static boolean pardonIp(MinecraftServer server, String ip) {
        return ModerationSavedData.get(server).clearIpBan(ip) != null;
    }

    public static void kick(ServerPlayer target, String reason, @Nullable ServerPlayer moderator) {
        if (target == null) return;
        MinecraftServer server = target.server;
        ModerationSavedData.get(server).addHistory(target.getUUID(), target.getGameProfile().getName(),
                new HistoryEntry(PunishmentType.KICK, "apply", reason, modName(moderator),
                        System.currentTimeMillis(), 0L));
        target.connection.disconnect(Component.translatable("pjmbasemod.moderation.disconnect.kick", reason));
        broadcast(server, Component.translatable("pjmbasemod.moderation.kick.broadcast", target.getGameProfile().getName(), reason));
        ru.liko.pjmbasemod.common.logging.PjmActionLogger.instance().logSubsystem(
                ru.liko.pjmbasemod.common.logging.LogCategory.MOD,
                modName(moderator) + " кикнул " + target.getGameProfile().getName() + ": " + reason);
    }

    // ---------------------------------------------------------------- муты

    public static void muteVoice(MinecraftServer server, UUID target, String targetName,
                                 long durationMs, String reason, @Nullable ServerPlayer moderator) {
        ModerationSavedData data = ModerationSavedData.get(server);
        long now = System.currentTimeMillis();
        long expires = DurationParser.expiresAtFromNow(durationMs);
        data.setVoiceMute(target, targetName, new MuteEntry(reason, modId(moderator), modName(moderator), now, expires));
        data.addHistory(target, targetName, new HistoryEntry(PunishmentType.MUTE_VOICE, "apply", reason, modName(moderator), now, durationMs));
        VoicechatBridge.setVoiceMuted(target, true);
        notifyTarget(server, target, Component.translatable("pjmbasemod.moderation.mute.voice.notify",
                DurationParser.format(durationMs), reason));
        broadcast(server, Component.translatable("pjmbasemod.moderation.mute.voice.broadcast", targetName,
                DurationParser.format(durationMs)));
    }

    public static boolean unmuteVoice(MinecraftServer server, UUID target, String targetName, @Nullable ServerPlayer moderator) {
        ModerationSavedData data = ModerationSavedData.get(server);
        MuteEntry removed = data.clearVoiceMute(target);
        VoicechatBridge.setVoiceMuted(target, false);
        if (removed == null) return false;
        data.addHistory(target, targetName, new HistoryEntry(PunishmentType.MUTE_VOICE, "revoke", "", modName(moderator),
                System.currentTimeMillis(), 0L));
        notifyTarget(server, target, Component.translatable("pjmbasemod.moderation.unmute.voice.notify"));
        return true;
    }

    public static void muteText(MinecraftServer server, UUID target, String targetName,
                                long durationMs, String reason, @Nullable ServerPlayer moderator) {
        ModerationSavedData data = ModerationSavedData.get(server);
        long now = System.currentTimeMillis();
        long expires = DurationParser.expiresAtFromNow(durationMs);
        data.setTextMute(target, targetName, new MuteEntry(reason, modId(moderator), modName(moderator), now, expires));
        data.addHistory(target, targetName, new HistoryEntry(PunishmentType.MUTE_TEXT, "apply", reason, modName(moderator), now, durationMs));
        notifyTarget(server, target, Component.translatable("pjmbasemod.moderation.mute.text.notify",
                DurationParser.format(durationMs), reason));
        broadcast(server, Component.translatable("pjmbasemod.moderation.mute.text.broadcast", targetName,
                DurationParser.format(durationMs)));
    }

    public static boolean unmuteText(MinecraftServer server, UUID target, String targetName, @Nullable ServerPlayer moderator) {
        ModerationSavedData data = ModerationSavedData.get(server);
        MuteEntry removed = data.clearTextMute(target);
        if (removed == null) return false;
        data.addHistory(target, targetName, new HistoryEntry(PunishmentType.MUTE_TEXT, "revoke", "", modName(moderator),
                System.currentTimeMillis(), 0L));
        notifyTarget(server, target, Component.translatable("pjmbasemod.moderation.unmute.text.notify"));
        return true;
    }

    // ---------------------------------------------------------------- проверки (ленивое истечение)

    public static boolean isBanned(MinecraftServer server, UUID target) {
        ModerationSavedData data = ModerationSavedData.get(server);
        BanEntry ban = data.activeBan(target);
        if (ban == null) return false;
        if (isExpired(ban.expiresAtMs())) {
            data.clearBan(target);
            data.addHistory(target, nameOf(data, target), new HistoryEntry(PunishmentType.BAN, "expire", "", "System",
                    System.currentTimeMillis(), 0L));
            return false;
        }
        return true;
    }

    public static boolean isTextMuted(MinecraftServer server, UUID target) {
        ModerationSavedData data = ModerationSavedData.get(server);
        ModerationProfile p = data.profile(target);
        if (p == null || p.textMute() == null) return false;
        if (isExpired(p.textMute().expiresAtMs())) {
            data.clearTextMute(target);
            return false;
        }
        return true;
    }

    public static boolean isVoiceMuted(MinecraftServer server, UUID target) {
        ModerationSavedData data = ModerationSavedData.get(server);
        ModerationProfile p = data.profile(target);
        if (p == null || p.voiceMute() == null) return false;
        if (isExpired(p.voiceMute().expiresAtMs())) {
            data.clearVoiceMute(target);
            VoicechatBridge.setVoiceMuted(target, false);
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- enforcement / жизненный цикл

    /**
     * Вызывается при входе игрока: кик забаненного, ресинк актуальных мутов.
     * @return true, если игрок был отключён баном (дальнейшую синхронизацию делать не нужно).
     */
    public static boolean enforceOnLogin(ServerPlayer player) {
        if (player == null) return false;
        MinecraftServer server = player.server;
        UUID id = player.getUUID();
        String name = player.getGameProfile().getName();
        ModerationSavedData data = ModerationSavedData.get(server);
        data.touchName(id, name);

        // Бан по нику или по IP.
        BanEntry ban = data.activeBan(id);
        if (ban != null && !isExpired(ban.expiresAtMs())) {
            player.connection.disconnect(banScreen(ban));
            return true;
        }
        BanEntry ipBan = data.ipBan(player.getIpAddress());
        if (ipBan != null && !isExpired(ipBan.expiresAtMs())) {
            player.connection.disconnect(banScreen(ipBan));
            return true;
        }

        // Ресинк войс-мута в рантайм-плагин.
        VoicechatBridge.setVoiceMuted(id, isVoiceMuted(server, id));
        return false;
    }

    /** Периодически (раз в ~5 сек реального времени) снимает истёкшие муты у онлайн-игроков. */
    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastMuteSweepMs < 5000L) return;
        lastMuteSweepMs = now;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            // Ленивое снятие истёкших + ре-ассерт рантайм-флага войс-плагина (на случай его сброса).
            VoicechatBridge.setVoiceMuted(p.getUUID(), isVoiceMuted(server, p.getUUID()));
            isTextMuted(server, p.getUUID());
        }
    }

    // ---------------------------------------------------------------- пороги эскалации

    /** Порог автонаказания при накоплении варнов. */
    public record WarnThreshold(int count, PunishmentType action, long durationMs) {}

    public static List<WarnThreshold> parsedThresholds() {
        List<WarnThreshold> result = new ArrayList<>();
        for (String raw : Config.getModerationWarnEscalationRaw()) {
            if (raw == null) continue;
            String[] parts = raw.trim().split("\\s+");
            if (parts.length < 2) continue;
            int count;
            try {
                count = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                continue;
            }
            PunishmentType action = PunishmentType.byId(parts[1]);
            if (action == null || count <= 0) continue;
            long duration = parts.length >= 3 ? DurationParser.parseToMillis(parts[2]) : DurationParser.PERMANENT;
            if (duration == DurationParser.INVALID) duration = DurationParser.PERMANENT;
            result.add(new WarnThreshold(count, action, duration));
        }
        return result;
    }

    @Nullable
    private static WarnThreshold matchingThreshold(int count) {
        // Счётчик активных варнов растёт по одному, поэтому точного совпадения достаточно.
        // При warnDecayDays=0 каждый порог срабатывает единожды; при decay>0 после распада
        // варнов счётчик может снова дойти до порога и наказание применится повторно (ожидаемо).
        for (WarnThreshold t : parsedThresholds()) {
            if (t.count() == count) return t;
        }
        return null;
    }

    // ---------------------------------------------------------------- утилиты

    private static boolean isExpired(long expiresAtMs) {
        return expiresAtMs != DurationParser.PERMANENT && expiresAtMs <= System.currentTimeMillis();
    }

    private static Component banScreen(BanEntry ban) {
        return Component.empty()
                .append(Component.translatable("pjmbasemod.moderation.ban.title").withStyle(ChatFormatting.RED))
                .append("\n")
                .append(Component.translatable("pjmbasemod.moderation.ban.reason", ban.reason()).withStyle(ChatFormatting.GRAY))
                .append("\n")
                .append(ban.isPermanent()
                        ? Component.translatable("pjmbasemod.moderation.ban.permanent").withStyle(ChatFormatting.GRAY)
                        : Component.translatable("pjmbasemod.moderation.ban.expires",
                                DurationParser.format(ban.expiresAtMs() - System.currentTimeMillis())).withStyle(ChatFormatting.GRAY));
    }

    private static void broadcast(MinecraftServer server, Component msg) {
        if (!Config.isModerationBroadcast()) return;
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    private static void notifyTarget(MinecraftServer server, UUID target, Component message) {
        ServerPlayer online = server.getPlayerList().getPlayer(target);
        if (online != null) online.sendSystemMessage(message);
    }

    @Nullable
    private static UUID modId(@Nullable ServerPlayer moderator) {
        return moderator == null ? null : moderator.getUUID();
    }

    private static String modName(@Nullable ServerPlayer moderator) {
        return moderator == null ? CONSOLE : moderator.getGameProfile().getName();
    }

    private static String nameOf(ModerationSavedData data, UUID id) {
        ModerationProfile p = data.profile(id);
        return p == null ? "unknown" : p.lastKnownName();
    }

    /** Нормализация ника для сравнения (не используется напрямую, зарезервировано). */
    static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
