package ru.liko.pjmbasemod.common.role;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import ru.liko.pjmbasemod.Pjmbasemod;

@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class RolePermissions {

    public static final PermissionNode<Boolean> ADMIN = boolNode("role.admin",
            (player, uuid, ctx) -> player != null && player.hasPermissions(2));

    private RolePermissions() {}

    private static PermissionNode<Boolean> boolNode(String path, PermissionNode.PermissionResolver<Boolean> resolver) {
        return new PermissionNode<>(Pjmbasemod.MODID, path, PermissionTypes.BOOLEAN, resolver);
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(ADMIN);
    }

    public static boolean can(ServerPlayer player, PermissionNode<Boolean> node) {
        return player != null && Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
    }
}
