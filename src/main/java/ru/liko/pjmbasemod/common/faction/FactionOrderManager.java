package ru.liko.pjmbasemod.common.faction;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.teams.Teams;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.FactionOrderSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;

import java.util.Map;

/** Установка/снятие/рассылка приказа фракции и проверка истечения по gameTime. */
public final class FactionOrderManager {

    private static final long NOTIFY_DURATION_MS = 5000L;
    private static int tickCounter;

    private FactionOrderManager() {
    }

    public static void setOrder(ServerPlayer actor, String rawText, int ttlMinutes) {
        if (actor == null || actor.getServer() == null) return;
        MinecraftServer server = actor.getServer();
        FactionMenuService.Authority authority = FactionMenuService.authority(actor);
        if (!authority.valid() || !authority.canSetOrder()) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.no_access"), true);
            return;
        }
        String team = authority.teamId();

        String text = rawText == null ? "" : rawText.trim();
        if (text.isBlank()) {
            clearOrder(actor);
            return;
        }
        int maxLen = Config.getFactionOrderMaxLength();
        if (text.length() > maxLen) text = text.substring(0, maxLen);

        int ttl = Mth.clamp(ttlMinutes, 0, Config.getFactionOrderMaxTtlMinutes());
        long now = server.overworld().getGameTime();
        long expires = ttl <= 0 ? -1L : now + (long) ttl * 60L * 20L;

        FactionOrderSavedData.get(server).setOrder(team,
                new FactionOrderSavedData.OrderEntry(text, actor.getName().getString(), now, expires));

        broadcast(server, team);

        Component title = Component.translatable("gui.pjmbasemod.faction.order.notify_title");
        Component subtitle = Component.literal(text);
        int color = Teams.color(server, team);
        for (ServerPlayer member : server.getPlayerList().getPlayers()) {
            if (team.equals(Teams.resolvePlayerTeamId(member))) {
                PjmNetworking.sendToPlayer(member, new NotificationPacket(title, subtitle, color, NOTIFY_DURATION_MS));
            }
        }
        FactionMenuService.resync(actor);
    }

    public static void clearOrder(ServerPlayer actor) {
        if (actor == null || actor.getServer() == null) return;
        FactionMenuService.Authority authority = FactionMenuService.authority(actor);
        if (!authority.valid() || !authority.canSetOrder()) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.no_access"), true);
            return;
        }
        MinecraftServer server = actor.getServer();
        FactionOrderSavedData.get(server).clearOrder(authority.teamId());
        broadcast(server, authority.teamId());
        FactionMenuService.resync(actor);
    }

    /** Отправляет игроку актуальный приказ его команды (или «пусто»). Вызывается при логине. */
    public static void syncTo(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        String team = Teams.resolvePlayerTeamId(player);
        if (team == null || team.isBlank()) {
            PjmNetworking.sendToPlayer(player, new FactionOrderSyncPacket(false, "", "", 0xFFFFFF, 0));
            return;
        }
        PjmNetworking.sendToPlayer(player, buildPacket(player.getServer(), team));
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null) return;
        if (++tickCounter < 20) return;
        tickCounter = 0;
        FactionOrderSavedData data = FactionOrderSavedData.get(server);
        long now = server.overworld().getGameTime();
        for (Map.Entry<String, FactionOrderSavedData.OrderEntry> entry : data.orders().entrySet()) {
            FactionOrderSavedData.OrderEntry order = entry.getValue();
            if (order.expiresAtGameTime() >= 0 && now >= order.expiresAtGameTime()) {
                data.clearOrder(entry.getKey());
                broadcast(server, entry.getKey());
            }
        }
    }

    private static void broadcast(MinecraftServer server, String team) {
        PjmNetworking.sendToTeam(server, team, buildPacket(server, team));
    }

    private static FactionOrderSyncPacket buildPacket(MinecraftServer server, String team) {
        FactionOrderSavedData.OrderEntry order = FactionOrderSavedData.get(server).order(team);
        int color = Teams.color(server, team);
        long now = server.overworld().getGameTime();
        if (order == null || (order.expiresAtGameTime() >= 0 && now >= order.expiresAtGameTime())) {
            return new FactionOrderSyncPacket(false, "", "", color, 0);
        }
        int secs = order.expiresAtGameTime() < 0 ? -1
                : (int) Math.max(1, (order.expiresAtGameTime() - now) / 20);
        return new FactionOrderSyncPacket(true, order.text(), order.author(), color, secs);
    }
}
