package ru.liko.pjmbasemod.common.moderation;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.permission.PermissionReady;

/**
 * Права модерации (NeoForge PermissionAPI). Ноды {@code pjmbasemod.moderation.*}.
 * По умолчанию все действия доступны с OP-2; при наличии permission-backend
 * (LuckPerms и т.п.) управляются им автоматически.
 */
@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class ModerationPermissions {

    public static final PermissionNode<Boolean> WARN       = boolNode("moderation.warn");
    public static final PermissionNode<Boolean> BAN        = boolNode("moderation.ban");
    public static final PermissionNode<Boolean> KICK       = boolNode("moderation.kick");
    public static final PermissionNode<Boolean> MUTE_VOICE = boolNode("moderation.mute.voice");
    public static final PermissionNode<Boolean> MUTE_TEXT  = boolNode("moderation.mute.text");
    public static final PermissionNode<Boolean> GUI        = boolNode("moderation.gui");
    public static final PermissionNode<Boolean> HISTORY    = boolNode("moderation.history");

    private ModerationPermissions() {}

    private static PermissionNode<Boolean> boolNode(String path) {
        return new PermissionNode<>(Pjmbasemod.MODID, path, PermissionTypes.BOOLEAN,
                (player, uuid, ctx) -> player != null && player.hasPermissions(2));
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(WARN, BAN, KICK, MUTE_VOICE, MUTE_TEXT, GUI, HISTORY);
    }

    public static boolean can(ServerPlayer player, PermissionNode<Boolean> node) {
        if (player == null) return false;
        // До PlayerLoggedInEvent capability LuckPerms ещё не инициализирована — откат к ванильному OP.
        if (!PermissionReady.isReady(player)) return player.hasPermissions(2);
        return Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
    }

    /** Права из командного источника: игрок → нода, консоль/командный блок → OP-уровень источника. */
    public static PermissionNode<Boolean> nodeFor(PunishmentType type) {
        return switch (type) {
            case WARN -> WARN;
            case KICK -> KICK;
            case BAN, TEMPBAN -> BAN;
            case MUTE_VOICE -> MUTE_VOICE;
            case MUTE_TEXT -> MUTE_TEXT;
        };
    }
}
