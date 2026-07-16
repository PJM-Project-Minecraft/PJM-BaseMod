package ru.liko.pjmbasemod.common.warehouse;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.compat.LuckPermsCompat;
import ru.liko.pjmbasemod.common.permission.PermissionReady;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Проверка донат-пермишена на выдачу предмета склада.
 *
 * <p>Ключ доната ({@link WarehouseItemDefinition#permission()}) маппится в permission-ноду
 * {@code pjmbasemod.warehouse.perm.<key>}, которую выдаёт донат-плагин/LuckPerms.</p>
 *
 * <p><b>Регистрация нод.</b> NeoForge {@link PermissionAPI#getPermission} бросает
 * {@code UnregisteredPermissionException}, если нода не зарегистрирована в
 * {@link PermissionGatherEvent.Nodes} — <i>вне зависимости</i> от backend (в т.ч. LuckPerms).
 * Ключи доната берутся из {@code items.json}, но файл лежит на диске уже к моменту gather-события,
 * поэтому здесь мы читаем реестр и регистрируем ноду на каждый ключ. Ключи, добавленные
 * <i>после</i> старта (правка {@code items.json} + {@code /pjm reload warehouse}), зарегистрировать
 * поздно — для них {@link #canAccess} откатывается к ванильному OP (нужен рестарт сервера, чтобы
 * донат-нода заработала). Это предотвращает краш открытия склада на незарегистрированной ноде.</p>
 *
 * <p>Fallback «только OP»: до {@code PlayerLoggedInEvent} capability LuckPerms ещё не готова —
 * откат к {@link ServerPlayer#hasPermissions(int) hasPermissions(2)} через {@link PermissionReady}.</p>
 */
@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class WarehouseDonorPermissions {

    private static final Map<String, PermissionNode<Boolean>> NODES = new ConcurrentHashMap<>();

    private WarehouseDonorPermissions() {}

    /**
     * Регистрирует ноды {@code pjmbasemod.warehouse.perm.<key>} по ключам доната из {@code items.json}.
     * Реестр грузится в {@code ServerStartedEvent} (позже gather-события), поэтому читаем конфиг здесь
     * заранее — файл на диске уже доступен.
     */
    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        WarehouseItemRegistry.get().reload();
        Set<String> keys = new HashSet<>();
        for (WarehouseItemDefinition def : WarehouseItemRegistry.get().all()) {
            if (def.donateRestricted()) keys.add(def.permission());
        }
        for (String key : keys) {
            PermissionNode<Boolean> node = newNode(key);
            NODES.put(key, node);
            event.addNodes(node);
        }
    }

    /** Доступна ли выдача предмета игроку: {@code true}, если предмет не донатный ИЛИ есть право. */
    public static boolean canAccess(ServerPlayer player, WarehouseItemDefinition def) {
        if (def == null || !def.donateRestricted()) return true;
        if (player == null) return false;
        // До PlayerLoggedInEvent capability LuckPerms ещё не инициализирована — откат к ванильному OP.
        if (!PermissionReady.isReady(player)) return player.hasPermissions(2);
        PermissionNode<Boolean> node = NODES.get(def.permission());
        // Ключ добавлен после старта и не зарегистрирован в NeoForge (правка items.json + /pjm reload
        // без рестарта). getPermission на нём крашнул бы — спрашиваем LuckPerms по строке напрямую,
        // так донат-ключ работает сразу; без LuckPerms откатываемся к ванильному OP.
        if (node == null) {
            return LuckPermsCompat.check(player, Pjmbasemod.MODID + ".warehouse.perm." + def.permission())
                    .orElseGet(() -> player.hasPermissions(2));
        }
        return Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
    }

    private static PermissionNode<Boolean> newNode(String key) {
        return new PermissionNode<>(Pjmbasemod.MODID, "warehouse.perm." + key, PermissionTypes.BOOLEAN,
                (player, uuid, ctx) -> player != null && player.hasPermissions(2));
    }
}
