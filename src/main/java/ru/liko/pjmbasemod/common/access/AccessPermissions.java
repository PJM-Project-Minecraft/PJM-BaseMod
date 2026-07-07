package ru.liko.pjmbasemod.common.access;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.permission.PermissionReady;

import java.util.EnumMap;
import java.util.Map;

/**
 * Проверка донат-нод «Доступов». Каждый {@link AccessType} имеет статическую ноду
 * {@code pjmbasemod.access.<id>} (по умолчанию — только OP 2+), выдаваемую донат-плагином /
 * LuckPerms. Набор Доступов известен в коде, поэтому ноды регистрируются статически в
 * {@link PermissionGatherEvent.Nodes} (в отличие от динамических warehouse-нод
 * {@link ru.liko.pjmbasemod.common.warehouse.WarehouseDonorPermissions}).
 */
@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class AccessPermissions {

    private static final Map<AccessType, PermissionNode<Boolean>> NODES = new EnumMap<>(AccessType.class);

    static {
        for (AccessType access : AccessType.values()) {
            NODES.put(access, boolNode("access." + access.id(),
                    (player, uuid, ctx) -> player != null && player.hasPermissions(2)));
        }
    }

    private AccessPermissions() {}

    private static PermissionNode<Boolean> boolNode(String path, PermissionNode.PermissionResolver<Boolean> resolver) {
        return new PermissionNode<>(Pjmbasemod.MODID, path, PermissionTypes.BOOLEAN, resolver);
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        for (PermissionNode<Boolean> node : NODES.values()) {
            event.addNodes(node);
        }
    }

    /** Есть ли у игрока Доступ. {@code null}-Доступ трактуется как отсутствие права. */
    public static boolean has(ServerPlayer player, AccessType access) {
        if (player == null || access == null) return false;
        PermissionNode<Boolean> node = NODES.get(access);
        if (node == null) return false;
        // До PlayerLoggedInEvent capability LuckPerms ещё не инициализирована — откат к ванильному OP.
        if (!PermissionReady.isReady(player)) return player.hasPermissions(2);
        return Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
    }

    /** Перегрузка по id/алиасу Доступа. Неизвестный id → {@code false}. */
    public static boolean has(ServerPlayer player, String accessId) {
        return has(player, AccessType.byIdOrAlias(accessId));
    }
}
