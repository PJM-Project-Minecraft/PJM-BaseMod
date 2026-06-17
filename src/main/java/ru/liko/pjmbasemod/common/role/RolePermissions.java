package ru.liko.pjmbasemod.common.role;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.EnumMap;
import java.util.Map;

@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class RolePermissions {

    public static final PermissionNode<Boolean> ADMIN = boolNode("role.admin",
            (player, uuid, ctx) -> player != null && player.hasPermissions(2));

    /** Нода владения каждой ролью: pjmbasemod.role.unlock.<id>. По умолчанию владеет только OP 2+. */
    private static final Map<CombatRole, PermissionNode<Boolean>> UNLOCK_NODES = new EnumMap<>(CombatRole.class);

    static {
        for (CombatRole role : CombatRole.values()) {
            UNLOCK_NODES.put(role, boolNode("role.unlock." + role.id(),
                    (player, uuid, ctx) -> player != null && player.hasPermissions(2)));
        }
    }

    private RolePermissions() {}

    private static PermissionNode<Boolean> boolNode(String path, PermissionNode.PermissionResolver<Boolean> resolver) {
        return new PermissionNode<>(Pjmbasemod.MODID, path, PermissionTypes.BOOLEAN, resolver);
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(ADMIN);
        for (PermissionNode<Boolean> node : UNLOCK_NODES.values()) {
            event.addNodes(node);
        }
    }

    public static boolean can(ServerPlayer player, PermissionNode<Boolean> node) {
        return player != null && Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
    }

    /** true, если роль бесплатная (по RoleAccessRegistry) ИЛИ у игрока есть нода разблокировки. */
    public static boolean canUseRole(ServerPlayer player, CombatRole role) {
        if (player == null || role == null) return false;
        if (!RoleAccessRegistry.get().isPaid(role)) return true;
        PermissionNode<Boolean> node = UNLOCK_NODES.get(role);
        return node != null && can(player, node);
    }
}
