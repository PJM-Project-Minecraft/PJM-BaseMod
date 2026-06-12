package ru.liko.pjmbasemod.common.faction;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import ru.liko.pjmbasemod.Pjmbasemod;

@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class FactionPermissions {

    public static final PermissionNode<Boolean> COMMANDER_ADMIN = boolNode("faction.commander.admin",
            (player, uuid, ctx) -> player != null && player.hasPermissions(2));

    private FactionPermissions() {
    }

    private static PermissionNode<Boolean> boolNode(String path, PermissionNode.PermissionResolver<Boolean> resolver) {
        return new PermissionNode<>(Pjmbasemod.MODID, path, PermissionTypes.BOOLEAN, resolver);
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(COMMANDER_ADMIN);
    }

    public static boolean can(ServerPlayer player, PermissionNode<Boolean> node) {
        return player != null && Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
    }
}
