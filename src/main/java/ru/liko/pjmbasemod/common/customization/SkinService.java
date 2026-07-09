package ru.liko.pjmbasemod.common.customization;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.common.teams.Teams;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.PlayerSkinSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.SkinSelectionSyncPacket;

import java.util.List;
import java.util.UUID;

/** Оркестрация командных скинов: авто-назначение по команде, выбор из пула, синхронизация. */
public final class SkinService {

    private SkinService() {
    }

    /** Текущий скин игрока: сохранённый (если валиден) или дефолт команды; может быть пустым. */
    public static String currentSkin(ServerPlayer player) {
        if (player == null || player.getServer() == null) return "";
        String stored = SkinSavedData.get(player.getServer()).getSkin(player.getUUID());
        String team = Teams.resolvePlayerTeamId(player);
        if (stored != null && !stored.isBlank()
                && (team == null || SkinRegistry.get().isAllowed(team, stored))) {
            return stored;
        }
        return team == null ? "" : SkinRegistry.get().defaultForTeam(team);
    }

    /** Приводит сохранённый скин к валидному для команды значению. {@code true} — если изменилось. */
    public static boolean ensureValid(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        SkinSavedData data = SkinSavedData.get(player.getServer());
        String stored = data.getSkin(player.getUUID());
        String team = Teams.resolvePlayerTeamId(player);

        if (team == null) {
            // Нет команды — скин не навязываем, чистим устаревший выбор.
            if (stored != null) {
                data.clear(player.getUUID());
                return true;
            }
            return false;
        }
        if (stored != null && SkinRegistry.get().isAllowed(team, stored)) {
            return false;
        }
        String def = SkinRegistry.get().defaultForTeam(team);
        if (def.isBlank()) {
            if (stored != null) {
                data.clear(player.getUUID());
                return true;
            }
            return false;
        }
        data.setSkin(player.getUUID(), def);
        return true;
    }

    public static void onPlayerLogin(ServerPlayer player) {
        ensureValid(player);
        sendInitialSync(player);
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        if (player.serverLevel().getGameTime() % 40L != 0L) return;
        if (ensureValid(player)) {
            broadcast(player.getServer(), player.getUUID(), currentSkin(player));
            syncSelectionTo(player);
        }
    }

    /** Выбор скина игроком из пула своей команды. */
    public static void select(ServerPlayer player, String skinId) {
        if (player == null || player.getServer() == null) return;
        String team = Teams.resolvePlayerTeamId(player);
        String id = SkinRegistry.sanitize(skinId);
        if (team == null || !SkinRegistry.get().isAllowed(team, id)) {
            player.displayClientMessage(
                    Component.translatable("gui.pjmbasemod.skin.not_allowed"), true);
            return;
        }
        SkinSavedData.get(player.getServer()).setSkin(player.getUUID(), id);
        broadcast(player.getServer(), player.getUUID(), id);
        syncSelectionTo(player);
    }

    /** Полная синхронизация при входе: скины всех онлайн этому игроку + его скин всем + меню. */
    public static void sendInitialSync(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        MinecraftServer server = player.getServer();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            PjmNetworking.sendToPlayer(player,
                    new PlayerSkinSyncPacket(other.getUUID(), currentSkin(other)));
        }
        String own = currentSkin(player);
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == player) continue;
            PjmNetworking.sendToPlayer(other, new PlayerSkinSyncPacket(player.getUUID(), own));
        }
        syncSelectionTo(player);
    }

    public static void broadcast(MinecraftServer server, UUID playerId, String skinId) {
        if (server == null || playerId == null) return;
        PjmNetworking.sendToAll(server, new PlayerSkinSyncPacket(playerId, skinId == null ? "" : skinId));
    }

    public static void syncSelectionTo(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        String team = Teams.resolvePlayerTeamId(player);
        List<String> allowed = team == null ? List.of() : SkinRegistry.get().skinsForTeam(team);
        PjmNetworking.sendToPlayer(player, new SkinSelectionSyncPacket(allowed, currentSkin(player)));
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ensureValid(player);
            broadcast(server, player.getUUID(), currentSkin(player));
            syncSelectionTo(player);
        }
    }
}
