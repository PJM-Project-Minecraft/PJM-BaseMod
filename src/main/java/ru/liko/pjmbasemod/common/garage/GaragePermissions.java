package ru.liko.pjmbasemod.common.garage;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * Ноды прав системы гаража. Работает с любым backend'ом PermissionAPI (например LuckPerms);
 * при отсутствии backend'а применяется default-резолвер (фолбэк на OP-уровни).
 *
 * <ul>
 *   <li>{@code pjmbasemod.garage.craft} — сборка техники (по умолчанию всем);</li>
 *   <li>{@code pjmbasemod.garage.spawn} — спавн техники из гаража (по умолчанию всем);</li>
 *   <li>{@code pjmbasemod.garage.store} — убрать технику обратно в гараж (по умолчанию всем);</li>
 *   <li>{@code pjmbasemod.garage.admin} — управление каталогом и чужими гаражами (по умолчанию OP&nbsp;2).</li>
 * </ul>
 */
@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class GaragePermissions {

    public static final PermissionNode<Boolean> CRAFT = boolNode("garage.craft", (player, uuid, ctx) -> true);
    public static final PermissionNode<Boolean> SPAWN = boolNode("garage.spawn", (player, uuid, ctx) -> true);
    public static final PermissionNode<Boolean> STORE = boolNode("garage.store", (player, uuid, ctx) -> true);
    public static final PermissionNode<Boolean> ADMIN = boolNode("garage.admin",
            (player, uuid, ctx) -> player != null && player.hasPermissions(2));

    private GaragePermissions() {}

    private static PermissionNode<Boolean> boolNode(String path, PermissionNode.PermissionResolver<Boolean> resolver) {
        return new PermissionNode<>(Pjmbasemod.MODID, path, PermissionTypes.BOOLEAN, resolver);
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(CRAFT, SPAWN, STORE, ADMIN);
    }

    public static boolean can(ServerPlayer player, PermissionNode<Boolean> node) {
        return player != null && Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
    }
}
