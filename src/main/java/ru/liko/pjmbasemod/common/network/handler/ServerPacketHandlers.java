package ru.liko.pjmbasemod.common.network.handler;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.chat.ChatMode;
import ru.liko.pjmbasemod.common.customization.CustomizationType;
import ru.liko.pjmbasemod.common.customization.SkinService;
import ru.liko.pjmbasemod.common.faction.FactionMenuService;
import ru.liko.pjmbasemod.common.faction.FactionOrderManager;
import ru.liko.pjmbasemod.common.moderation.ModerationPermissions;
import ru.liko.pjmbasemod.common.moderation.ModerationSavedData;
import ru.liko.pjmbasemod.common.moderation.ModerationService;
import ru.liko.pjmbasemod.common.moderation.ModerationSnapshot;
import ru.liko.pjmbasemod.common.moderation.PunishmentType;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.ChangeChatModePacket;
import ru.liko.pjmbasemod.common.network.packet.ModerationActionPacket;
import ru.liko.pjmbasemod.common.network.packet.ModerationSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenModerationPacket;
import ru.liko.pjmbasemod.common.network.packet.RequestModerationPacket;
import ru.liko.pjmbasemod.common.network.packet.HudConfigPacket;
import ru.liko.pjmbasemod.common.network.packet.ManageFactionDeputyPacket;
import ru.liko.pjmbasemod.common.network.packet.ManageFactionInvitePacket;
import ru.liko.pjmbasemod.common.network.packet.ManageFactionKickPacket;
import ru.liko.pjmbasemod.common.network.packet.ManageFactionRolePacket;
import ru.liko.pjmbasemod.common.network.packet.RadioSwitchPacket;
import ru.liko.pjmbasemod.common.network.packet.RequestFactionManagementPacket;
import ru.liko.pjmbasemod.common.network.packet.SelectRolePacket;
import ru.liko.pjmbasemod.common.network.packet.SelectCustomizationPacket;
import ru.liko.pjmbasemod.common.network.packet.SetFactionOrderPacket;
import ru.liko.pjmbasemod.common.network.packet.SubmitFactionSelectionPacket;
import ru.liko.pjmbasemod.common.network.packet.SyncPjmDataPacket;
import ru.liko.pjmbasemod.common.role.RoleService;
import ru.liko.pjmbasemod.common.voice.VoicechatBridge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerPacketHandlers {

    private static final Map<UUID, ChatMode> CHAT_MODE = new ConcurrentHashMap<>();

    private ServerPacketHandlers() {}

    public static ChatMode getChatMode(ServerPlayer player) {
        if (player == null) return ChatMode.GLOBAL;
        return CHAT_MODE.getOrDefault(player.getUUID(), ChatMode.GLOBAL);
    }

    /** Отправляет клиенту серверные HUD-флаги (скрытие полосок голода/брони). */
    public static void sendHudConfig(ServerPlayer player) {
        if (player == null) return;
        PjmNetworking.sendToPlayer(player, new HudConfigPacket(Config.isDisableHunger(), Config.isDisableArmor()));
    }

    public static void sendHudConfigAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendHudConfig(p);
        }
    }

    public static void handleChangeChatMode(ChangeChatModePacket p, ServerPlayer player) {
        if (player == null) return;
        ChatMode mode = p.mode() == null ? ChatMode.GLOBAL : p.mode();
        CHAT_MODE.put(player.getUUID(), mode);
        PjmNetworking.sendToPlayer(player, new SyncPjmDataPacket(player.getUUID(), mode.getKey()));
        if (Config.isDebug()) Pjmbasemod.LOGGER.info("[CHAT] {} -> {}", player.getName().getString(), mode);
    }

    public static void handleSelectCustomization(SelectCustomizationPacket p, ServerPlayer player) {
        if (player == null) return;
        if (p.customizationType() == CustomizationType.PLAYER_SKIN) {
            SkinService.select(player, p.optionId());
            return;
        }
        player.displayClientMessage(Component.translatable("gui.pjmbasemod.chat.customization_unavailable"), true);
    }

    public static void handleSelectRole(SelectRolePacket p, ServerPlayer player) {
        if (player == null) return;
        RoleService.AssignmentResult result = RoleService.assignRoleById(player, p.targetId(), p.roleId());
        player.displayClientMessage(result.message(), true);
    }

    public static void handleRequestFactionManagement(RequestFactionManagementPacket p, ServerPlayer player) {
        if (player == null) return;
        // Права (командир/зам) проверяет сам сервис; при отказе шлёт игроку сообщение.
        FactionMenuService.openManagement(player);
    }

    public static void handleSubmitFactionSelection(SubmitFactionSelectionPacket p, ServerPlayer player) {
        if (player == null) return;
        FactionMenuService.handleSelection(player, p.teamId(), p.roleId());
    }

    public static void handleManageFactionRole(ManageFactionRolePacket p, ServerPlayer player) {
        if (player == null) return;
        FactionMenuService.handleManageRole(player, p.targetId(), p.roleId());
    }

    public static void handleManageFactionDeputy(ManageFactionDeputyPacket p, ServerPlayer player) {
        if (player == null) return;
        FactionMenuService.handleManageDeputy(player, p.targetId(), p.deputy(), p.perms());
    }

    public static void handleManageFactionInvite(ManageFactionInvitePacket p, ServerPlayer player) {
        if (player == null) return;
        FactionMenuService.handleManageInvite(player, p.playerName(), p.invite());
    }

    public static void handleManageFactionKick(ManageFactionKickPacket p, ServerPlayer player) {
        if (player == null) return;
        FactionMenuService.handleManageKick(player, p.targetId());
    }

    public static void handleSetFactionOrder(SetFactionOrderPacket p, ServerPlayer player) {
        if (player == null) return;
        FactionOrderManager.setOrder(player, p.text(), p.ttlMinutes());
    }

    public static void handleRadioSwitch(RadioSwitchPacket p, ServerPlayer player) {
        if (player == null) return;
        if (p.isPressed()) {
            if (!VoicechatBridge.canUseTeamRadio(player)) {
                player.displayClientMessage(Component.translatable("gui.pjmbasemod.radio.error.no_team"), true);
                return;
            }
            VoicechatBridge.onPlayerStartRadio(player);
            return;
        }
        VoicechatBridge.onPlayerStopRadio(player);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        if (player == null) return;
        CHAT_MODE.remove(player.getUUID());
        VoicechatBridge.onPlayerStopRadio(player);
    }

    // ---------------------------------------------------------------- модерация

    /** Открыть экран модерации игроку (после проверки права GUI). */
    public static void sendModerationScreen(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        if (!ModerationPermissions.can(player, ModerationPermissions.GUI)) {
            player.displayClientMessage(Component.literal("§cНедостаточно прав для экрана модерации"), true);
            return;
        }
        PjmNetworking.sendToPlayer(player, new OpenModerationPacket(buildModerationSnapshot(player.getServer())));
    }

    public static void handleRequestModeration(RequestModerationPacket p, ServerPlayer player) {
        sendModerationScreen(player);
    }

    public static void handleModerationAction(ModerationActionPacket p, ServerPlayer player) {
        if (player == null || player.getServer() == null || p == null || p.targetId() == null) return;
        MinecraftServer server = player.getServer();
        boolean apply = "apply".equalsIgnoreCase(p.action());
        PunishmentType type = p.punishment();

        // Проверка прав по типу действия.
        if (!ModerationPermissions.can(player, ModerationPermissions.nodeFor(type))) {
            player.displayClientMessage(Component.literal("§cНедостаточно прав для этого действия"), true);
            return;
        }

        String name = resolveName(server, p.targetId());
        String reason = p.reason() == null || p.reason().isBlank() ? "Без причины" : p.reason();
        switch (type) {
            case WARN -> { if (apply) ModerationService.warn(server, p.targetId(), name, reason, player); }
            case KICK -> {
                ServerPlayer online = server.getPlayerList().getPlayer(p.targetId());
                if (online != null) ModerationService.kick(online, reason, player);
            }
            case BAN, TEMPBAN -> {
                if (apply) ModerationService.applyBan(server, p.targetId(), name, p.durationMs(), reason, player);
                else ModerationService.pardon(server, p.targetId(), name, player);
            }
            case MUTE_VOICE -> {
                if (apply) ModerationService.muteVoice(server, p.targetId(), name, p.durationMs(), reason, player);
                else ModerationService.unmuteVoice(server, p.targetId(), name, player);
            }
            case MUTE_TEXT -> {
                if (apply) ModerationService.muteText(server, p.targetId(), name, p.durationMs(), reason, player);
                else ModerationService.unmuteText(server, p.targetId(), name, player);
            }
        }
        // Ресинк открытого экрана инициатору.
        PjmNetworking.sendToPlayer(player, new ModerationSyncPacket(buildModerationSnapshot(server)));
    }

    private static String resolveName(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        ModerationSavedData.ModerationProfile p = ModerationSavedData.get(server).profile(id);
        return p == null ? "unknown" : p.lastKnownName();
    }

    private static ModerationSnapshot buildModerationSnapshot(MinecraftServer server) {
        ModerationSavedData data = ModerationSavedData.get(server);
        Map<UUID, ModerationSnapshot.PlayerModEntry> byId = new LinkedHashMap<>();
        // Онлайн-игроки.
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            byId.put(p.getUUID(), snapshotEntry(server, p.getUUID(), p.getGameProfile().getName(), true));
        }
        // Оффлайн-игроки из SavedData.
        for (UUID id : data.entries().keySet()) {
            if (byId.containsKey(id)) continue;
            ModerationSavedData.ModerationProfile prof = data.profile(id);
            byId.put(id, snapshotEntry(server, id, prof == null ? "unknown" : prof.lastKnownName(), false));
        }
        return new ModerationSnapshot(List.copyOf(byId.values()));
    }

    /**
     * Строка снапшота с ленивым истечением через ModerationService — одинаково для онлайн и оффлайн,
     * поэтому истёкшие, но ещё не снятые наказания не показываются активными.
     */
    private static ModerationSnapshot.PlayerModEntry snapshotEntry(MinecraftServer server, UUID id, String name, boolean online) {
        boolean banned = ModerationService.isBanned(server, id);
        ModerationSavedData.ModerationProfile prof = ModerationSavedData.get(server).profile(id);
        return new ModerationSnapshot.PlayerModEntry(
                id, name, online, banned,
                ModerationService.isVoiceMuted(server, id),
                ModerationService.isTextMuted(server, id),
                prof == null ? 0 : prof.warnCount(),
                banned ? banExpires(prof) : 0L,
                banned ? banReason(prof) : "");
    }

    private static long banExpires(ModerationSavedData.ModerationProfile prof) {
        return prof == null || prof.activeBan() == null ? 0L : prof.activeBan().expiresAtMs();
    }

    private static String banReason(ModerationSavedData.ModerationProfile prof) {
        return prof == null || prof.activeBan() == null ? "" : prof.activeBan().reason();
    }
}
