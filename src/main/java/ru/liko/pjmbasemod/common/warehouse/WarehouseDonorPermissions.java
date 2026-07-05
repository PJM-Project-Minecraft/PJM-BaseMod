package ru.liko.pjmbasemod.common.warehouse;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.permission.PermissionReady;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Проверка донат-пермишена на выдачу предмета склада.
 *
 * <p>Ключ доната ({@link WarehouseItemDefinition#permission()}) маппится в permission-ноду
 * {@code pjmbasemod.warehouse.perm.<key>}, которую выдаёт донат-плагин/LuckPerms. Ноды
 * <b>динамические</b>: ключи берутся из {@code items.json}, загружаемого в
 * {@code ServerStartedEvent} — уже <i>после</i> {@code PermissionGatherEvent.Nodes}, поэтому
 * статически (как в {@link ru.liko.pjmbasemod.common.role.RolePermissions}) их зарегистрировать
 * нельзя. Ноды создаются лениво и кешируются по ключу; LuckPerms-backend резолвит право по
 * имени ноды без предварительной регистрации.</p>
 *
 * <p>Fallback «только OP»: без permission-бэкенда {@code DefaultPermissionHandler} вызывает
 * дефолтный resolver ноды ({@link ServerPlayer#hasPermissions(int) hasPermissions(2)}); до
 * {@code PlayerLoggedInEvent} capability LuckPerms ещё не готова — тоже откат к ванильному OP
 * через {@link PermissionReady}.</p>
 */
public final class WarehouseDonorPermissions {

    private static final Map<String, PermissionNode<Boolean>> NODES = new ConcurrentHashMap<>();

    private WarehouseDonorPermissions() {}

    /** Доступна ли выдача предмета игроку: {@code true}, если предмет не донатный ИЛИ есть нода. */
    public static boolean canAccess(ServerPlayer player, WarehouseItemDefinition def) {
        if (def == null || !def.donateRestricted()) return true;
        if (player == null) return false;
        // До PlayerLoggedInEvent capability LuckPerms ещё не инициализирована — откат к ванильному OP.
        if (!PermissionReady.isReady(player)) return player.hasPermissions(2);
        PermissionNode<Boolean> node = nodeFor(def.permission());
        return Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
    }

    private static PermissionNode<Boolean> nodeFor(String key) {
        return NODES.computeIfAbsent(key, k -> new PermissionNode<>(
                Pjmbasemod.MODID, "warehouse.perm." + k, PermissionTypes.BOOLEAN,
                (player, uuid, ctx) -> player != null && player.hasPermissions(2)));
    }
}
