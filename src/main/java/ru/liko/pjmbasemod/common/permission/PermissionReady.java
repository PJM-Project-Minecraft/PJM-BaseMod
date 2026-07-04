package ru.liko.pjmbasemod.common.permission;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Трекинг «готовности» игрока к запросу прав через NeoForge PermissionAPI.
 *
 * <p>NeoForge при входе вызывает {@code PlayerList.placeNewPlayer → sendPlayerPermissionLevel →
 * sendCommands → CommandHelper.mergeCommandNode}, что вычисляет {@code requires()}-предикаты всех
 * узлов дерева команд. Это происходит <b>до</b> {@code PlayerLoggedInEvent}, где LuckPerms грузит
 * User и инициализирует capability. Запрос {@link net.neoforged.neoforge.server.permission.PermissionAPI#getPermission}
 * в этом окне бросает {@code IllegalStateException: Capability has not been initialised} — игрок
 * не может войти в мир.</p>
 *
 * <p>Решение: до {@code PlayerLoggedInEvent} права не запрашиваем у LuckPerms вообще —
 * {@link #isReady(ServerPlayer)} возвращает false, и {@code Permissions.can()} откатывается к
 * ванильному {@link ServerPlayer#hasPermissions(int)}. Это безопасно: {@code requires()} влияет
 * лишь на видимость команд клиенту, а реальная проверка прав повторяется при исполнении команды,
 * когда игрок уже «готов».</p>
 *
 * <p>Слушаем на {@link EventPriority#LOW} — после LuckPerms, чтобы capability гарантированно была
 * инициализирована к моменту добавления в {@link #readyPlayers}.</p>
 */
@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class PermissionReady {

    private static final Set<UUID> readyPlayers = ConcurrentHashMap.newKeySet();

    private PermissionReady() {}

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.LOW)
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            readyPlayers.add(sp.getUUID());
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            readyPlayers.remove(sp.getUUID());
        }
    }

    /** true, если для игрока уже отработал {@code PlayerLoggedInEvent} (capability LuckPerms готова). */
    public static boolean isReady(ServerPlayer player) {
        return player != null && readyPlayers.contains(player.getUUID());
    }

    /** Очистка при остановке сервера — на случай, если события logout не успели дойти. */
    public static void clear() {
        readyPlayers.clear();
    }
}
