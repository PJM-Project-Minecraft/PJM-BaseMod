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
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.ChangeChatModePacket;
import ru.liko.pjmbasemod.common.network.packet.HudConfigPacket;
import ru.liko.pjmbasemod.common.network.packet.ManageFactionDeputyPacket;
import ru.liko.pjmbasemod.common.network.packet.ManageFactionRolePacket;
import ru.liko.pjmbasemod.common.network.packet.RadioSwitchPacket;
import ru.liko.pjmbasemod.common.network.packet.RequestFactionManagementPacket;
import ru.liko.pjmbasemod.common.network.packet.RequestTargetRoleAccessPacket;
import ru.liko.pjmbasemod.common.network.packet.SelectRolePacket;
import ru.liko.pjmbasemod.common.network.packet.TargetRoleAccessPacket;
import ru.liko.pjmbasemod.common.network.packet.SelectCustomizationPacket;
import ru.liko.pjmbasemod.common.network.packet.SetFactionOrderPacket;
import ru.liko.pjmbasemod.common.network.packet.SubmitFactionSelectionPacket;
import ru.liko.pjmbasemod.common.network.packet.SyncPjmDataPacket;
import ru.liko.pjmbasemod.common.role.RoleService;
import ru.liko.pjmbasemod.common.voice.VoicechatBridge;

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

    public static void handleRequestTargetRoleAccess(RequestTargetRoleAccessPacket p, ServerPlayer player) {
        if (player == null || player.getServer() == null || p.targetId() == null) return;
        ServerPlayer target = player.getServer().getPlayerList().getPlayer(p.targetId());
        if (target == null) return;
        // Отвечаем только если запрашивающий вправе назначать роли этой цели (командир той же фракции / админ).
        if (!RoleService.canAssign(player, target)) return;
        PjmNetworking.sendToPlayer(player,
                new TargetRoleAccessPacket(p.targetId(), RoleService.assignableRoleIdsFor(target)));
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
}
