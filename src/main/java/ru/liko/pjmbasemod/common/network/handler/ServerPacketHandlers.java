package ru.liko.pjmbasemod.common.network.handler;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.chat.ChatMode;
import ru.liko.pjmbasemod.common.faction.FactionMenuService;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.ChangeChatModePacket;
import ru.liko.pjmbasemod.common.network.packet.ManageFactionRolePacket;
import ru.liko.pjmbasemod.common.network.packet.RadioSwitchPacket;
import ru.liko.pjmbasemod.common.network.packet.RefillAmmunitionPacket;
import ru.liko.pjmbasemod.common.network.packet.SelectRolePacket;
import ru.liko.pjmbasemod.common.network.packet.SelectCustomizationPacket;
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

    public static void handleChangeChatMode(ChangeChatModePacket p, ServerPlayer player) {
        if (player == null) return;
        ChatMode mode = p.mode() == null ? ChatMode.GLOBAL : p.mode();
        CHAT_MODE.put(player.getUUID(), mode);
        PjmNetworking.sendToPlayer(player, new SyncPjmDataPacket(player.getUUID(), mode.getKey()));
        if (Config.isDebug()) Pjmbasemod.LOGGER.info("[CHAT] {} -> {}", player.getName().getString(), mode);
    }

    public static void handleSelectCustomization(SelectCustomizationPacket p, ServerPlayer player) {
        if (player == null) return;
        player.displayClientMessage(Component.translatable("gui.pjmbasemod.chat.customization_unavailable"), true);
    }

    public static void handleRefillAmmunition(RefillAmmunitionPacket p, ServerPlayer player) {
        if (player == null) return;
        player.displayClientMessage(Component.translatable("gui.pjmbasemod.chat.refill_unavailable"), true);
    }

    public static void handleSelectRole(SelectRolePacket p, ServerPlayer player) {
        if (player == null) return;
        RoleService.AssignmentResult result = RoleService.assignRoleById(player, p.targetId(), p.roleId());
        player.displayClientMessage(result.message(), true);
    }

    public static void handleSubmitFactionSelection(SubmitFactionSelectionPacket p, ServerPlayer player) {
        if (player == null) return;
        FactionMenuService.handleSelection(player, p.teamId(), p.roleId());
    }

    public static void handleManageFactionRole(ManageFactionRolePacket p, ServerPlayer player) {
        if (player == null) return;
        FactionMenuService.handleManageRole(player, p.targetId(), p.roleId());
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
