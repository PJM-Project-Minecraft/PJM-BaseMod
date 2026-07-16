package ru.liko.pjmbasemod.common.report;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenReportsPacket;
import ru.liko.pjmbasemod.common.network.packet.PlayerReportThreadPacket;
import ru.liko.pjmbasemod.common.network.packet.ReportSyncPacket;
import ru.liko.pjmbasemod.common.report.ReportSavedData.Report;

import java.util.ArrayList;
import java.util.List;

/**
 * Серверная логика обращений в администрацию — переписка игрок ↔ администрация.
 * Игрок ведёт одну активную переписку ({@link #submit}); администраторы (OP-2)
 * видят список обращений в GUI и отвечают в том же треде, телепортируются, закрывают.
 */
public final class ReportManager {

    private static final int MAX_TEXT = 512;

    private ReportManager() {}

    private static boolean isAdmin(ServerPlayer player) {
        return player != null && player.hasPermissions(2);
    }

    // ---------------------------------------------------------------- игрок

    /** Открыть игроку его переписку (или пустой тред — форму нового обращения). */
    public static void openMyReport(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        Report open = ReportSavedData.get(player.getServer()).openReportOf(player.getUUID());
        PjmNetworking.sendToPlayer(player, new PlayerReportThreadPacket(toThread(open)));
    }

    /**
     * Игрок отправляет сообщение. Если есть активное обращение — дописывает в него,
     * иначе создаёт новое с выбранной категорией.
     */
    public static void submit(ServerPlayer reporter, ReportCategory category, String rawText) {
        if (reporter == null || reporter.getServer() == null || category == null) return;
        String text = rawText == null ? "" : rawText.strip();
        if (text.isEmpty()) return;
        if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);

        MinecraftServer server = reporter.getServer();
        ReportSavedData data = ReportSavedData.get(server);
        long now = System.currentTimeMillis();
        Report open = data.openReportOf(reporter.getUUID());

        Report report;
        boolean isNew = open == null;
        if (isNew) {
            report = data.create(reporter.getUUID(), reporter.getGameProfile().getName(), category, text, now);
        } else {
            report = data.addMessage(open.id(), false, reporter.getGameProfile().getName(), text, now);
        }
        if (report == null) return;

        // Обновить открытый экран игрока.
        PjmNetworking.sendToPlayer(reporter, new PlayerReportThreadPacket(toThread(report)));

        // Уведомить онлайн-админов (в чат — только на новое обращение, чтобы не спамить).
        if (isNew) {
            Component catName = Component.translatable(category.langKey());
            Component ping = Component.translatable("chat.pjmbasemod.report.new",
                            report.id(), report.reporterName(), catName)
                    .withStyle(s -> s.withColor(ChatFormatting.GOLD)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pjm reports"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("chat.pjmbasemod.report.new.hover"))));
            for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
                if (isAdmin(admin)) admin.sendSystemMessage(ping);
            }
        }
        resyncAdmins(server);
    }

    // ---------------------------------------------------------------- админ

    /** Открыть админский GUI жалоб (после проверки прав). */
    public static void openAdmin(ServerPlayer admin) {
        if (admin == null || admin.getServer() == null) return;
        if (!isAdmin(admin)) {
            admin.displayClientMessage(Component.translatable("chat.pjmbasemod.report.no_permission")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        PjmNetworking.sendToPlayer(admin, new OpenReportsPacket(buildSnapshot(admin.getServer())));
    }

    /** Действие админа над обращением: {@code teleport} / {@code close} / {@code reply}. */
    public static void handleAction(ServerPlayer admin, int id, String action, String text) {
        if (admin == null || admin.getServer() == null || action == null) return;
        MinecraftServer server = admin.getServer();
        if (!isAdmin(admin)) {
            admin.displayClientMessage(Component.translatable("chat.pjmbasemod.report.no_permission")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ReportSavedData data = ReportSavedData.get(server);
        Report report = data.find(id);
        if (report == null) return;
        ServerPlayer reporter = server.getPlayerList().getPlayer(report.reporterId());

        switch (action.toLowerCase()) {
            case "teleport" -> {
                if (reporter == null) {
                    admin.displayClientMessage(Component.translatable("chat.pjmbasemod.report.offline",
                            report.reporterName()).withStyle(ChatFormatting.RED), true);
                    return;
                }
                admin.teleportTo(reporter.serverLevel(), reporter.getX(), reporter.getY(), reporter.getZ(),
                        reporter.getYRot(), reporter.getXRot());
            }
            case "reply" -> {
                if (text == null || text.isBlank()) return;
                String msg = text.strip();
                if (msg.length() > MAX_TEXT) msg = msg.substring(0, MAX_TEXT);
                Report updated = data.addMessage(id, true, admin.getGameProfile().getName(), msg,
                        System.currentTimeMillis());
                if (updated == null) return;
                if (reporter != null) {
                    PjmNetworking.sendToPlayer(reporter, new PlayerReportThreadPacket(toThread(updated)));
                    PjmNetworking.sendToPlayer(reporter, new NotificationPacket(
                            Component.translatable("gui.pjmbasemod.report.reply.title"),
                            Component.literal(msg),
                            0x4FC3F7, 6000));
                }
            }
            case "close" -> {
                if (!data.close(id)) return;
                if (reporter != null) {
                    Report closed = data.find(id);
                    PjmNetworking.sendToPlayer(reporter, new PlayerReportThreadPacket(toThread(closed)));
                    PjmNetworking.sendToPlayer(reporter, new NotificationPacket(
                            Component.translatable("gui.pjmbasemod.report.closed.title"),
                            Component.translatable("gui.pjmbasemod.report.closed.subtitle", id),
                            0x9B9B9B, 5000));
                }
            }
            default -> { return; }
        }
        resyncAdmins(server);
    }

    // ---------------------------------------------------------------- служебное

    /** Разослать актуальный снимок всем онлайн-админам (обновляет открытые GUI). */
    public static void resyncAdmins(MinecraftServer server) {
        if (server == null) return;
        ReportSnapshot snapshot = buildSnapshot(server);
        for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
            if (isAdmin(admin)) PjmNetworking.sendToPlayer(admin, new ReportSyncPacket(snapshot));
        }
    }

    private static ReportThread toThread(Report r) {
        if (r == null) return ReportThread.NONE;
        return new ReportThread(r.id(), r.category(), r.open(), List.copyOf(r.messages()));
    }

    private static ReportSnapshot buildSnapshot(MinecraftServer server) {
        List<ReportSnapshot.Entry> entries = new ArrayList<>();
        for (Report r : ReportSavedData.get(server).all()) {
            boolean online = server.getPlayerList().getPlayer(r.reporterId()) != null;
            entries.add(new ReportSnapshot.Entry(r.id(), r.reporterId(), r.reporterName(),
                    r.category(), r.createdAt(), r.open(), online, List.copyOf(r.messages())));
        }
        return new ReportSnapshot(entries);
    }
}
