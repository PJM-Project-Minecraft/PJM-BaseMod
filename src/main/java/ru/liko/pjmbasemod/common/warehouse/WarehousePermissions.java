package ru.liko.pjmbasemod.common.warehouse;

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
 * Ноды прав системы склада.
 *
 * <ul>
 *   <li>{@code pjmbasemod.warehouse.withdraw} — получать предметы со склада через NPC (по умолчанию всем);</li>
 *   <li>{@code pjmbasemod.warehouse.admin} — управление складами, зонами и NPC (по умолчанию OP&nbsp;2).</li>
 * </ul>
 */
@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class WarehousePermissions {

    public static final PermissionNode<Boolean> WITHDRAW = boolNode("warehouse.withdraw", (player, uuid, ctx) -> true);
    public static final PermissionNode<Boolean> ADMIN = boolNode("warehouse.admin",
            (player, uuid, ctx) -> player != null && player.hasPermissions(2));

    private WarehousePermissions() {}

    private static PermissionNode<Boolean> boolNode(String path, PermissionNode.PermissionResolver<Boolean> resolver) {
        return new PermissionNode<>(Pjmbasemod.MODID, path, PermissionTypes.BOOLEAN, resolver);
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(WITHDRAW, ADMIN);
    }

    public static boolean can(ServerPlayer player, PermissionNode<Boolean> node) {
        if (player == null) return false;
        // До PlayerLoggedInEvent capability LuckPerms ещё не инициализирована — откат к дефолтному resolver'у ноды.
        if (!PermissionReady.isReady(player)) return defaultResolver(node, player);
        return Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
    }

    private static boolean defaultResolver(PermissionNode<Boolean> node, ServerPlayer player) {
        return Boolean.TRUE.equals(node.getDefaultResolver().resolve(player, player.getUUID()));
    }
}
