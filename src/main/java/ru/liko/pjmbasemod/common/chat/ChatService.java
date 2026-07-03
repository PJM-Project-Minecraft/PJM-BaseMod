package ru.liko.pjmbasemod.common.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.rank.RankService;

import java.util.ArrayList;
import java.util.List;

public final class ChatService {

    private static final double LOCAL_RADIUS = 80.0;
    private static final double LOCAL_RADIUS_SQ = LOCAL_RADIUS * LOCAL_RADIUS;

    private ChatService() {}

    public static List<ServerPlayer> filterRecipients(ServerPlayer sender, ChatMode mode) {
        if (sender == null || sender.getServer() == null) return List.of();
        var all = sender.getServer().getPlayerList().getPlayers();
        if (mode == null || mode == ChatMode.GLOBAL) return new ArrayList<>(all);

        List<ServerPlayer> out = new ArrayList<>();
        if (mode == ChatMode.LOCAL) {
            Vec3 origin = sender.position();
            var senderLevel = sender.level();
            for (ServerPlayer p : all) {
                if (p.level() != senderLevel) {
                    if (p == sender) out.add(p);
                    continue;
                }
                if (p == sender || p.position().distanceToSqr(origin) <= LOCAL_RADIUS_SQ) out.add(p);
            }
            return out;
        }

        // TEAM mode removed with team system; fallback to GLOBAL.
        return new ArrayList<>(all);
    }

    public static Component decorate(ServerPlayer sender, ChatMode mode, Component raw) {
        final ChatMode m = mode == null ? ChatMode.GLOBAL : mode;
        String tag = switch (m) {
            case LOCAL  -> "[L]";
            case TEAM   -> "[T]";
            case GLOBAL -> "[G]";
        };
        MutableComponent line = Component.literal(tag + " ")
                .withStyle(s -> s.withColor(m.getColor()));
        Component rankBadge = RankService.chatBadge(sender);
        if (rankBadge != null) {
            line.append(rankBadge);
        }
        return line
                .append(Component.literal(sender.getName().getString() + ": "))
                .append(raw);
    }

    public static void deliver(ServerPlayer sender, ChatMode mode, Component message) {
        List<ServerPlayer> recipients = filterRecipients(sender, mode);
        Component decorated = decorate(sender, mode, message);
        for (ServerPlayer p : recipients) {
            p.displayClientMessage(decorated, false);
        }
        Pjmbasemod.LOGGER.info("[CHAT/{}] {}: {}", mode, sender.getName().getString(), message.getString());
    }
}
