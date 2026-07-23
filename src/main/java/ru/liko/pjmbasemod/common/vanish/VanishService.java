package ru.liko.pjmbasemod.common.vanish;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ваниш: игрок пропадает из TAB-списка и из мира для всех остальных.
 *
 * <p>Две половины скрытия:</p>
 * <ul>
 *   <li><b>Сущность</b> — {@code ServerPlayerMixin} возвращает {@code false} из
 *       {@code broadcastToPlayer}, и ванильный {@code ChunkMap} сам снимает парность
 *       на ближайшем тике (и восстановит её при снятии ваниша). Пакеты вручную не шлём.</li>
 *   <li><b>TAB</b> — рассылка {@link ClientboundPlayerInfoRemovePacket} остальным;
 *       новым игрокам скрываем ванишнутых в {@link #onPlayerLogin}, т.к. ванильный
 *       {@code PlayerList} отдаёт им полный список.</li>
 * </ul>
 *
 * <p>Состояние — in-memory (сессия) + флаг в персистентных данных игрока, чтобы ваниш
 * переживал релог и рестарт. Ванишнутый виден самому себе и админам ({@link #isAdmin}):
 * админ видит его сущность в мире и строку в TAB с приставкой {@code [V]}, поэтому к
 * ванишнутому работает обычный {@code /tp}.</p>
 */
public final class VanishService {

    private static final String NBT_KEY = "pjm_vanished";

    private static final Set<UUID> VANISHED = ConcurrentHashMap.newKeySet();

    private VanishService() {}

    public static boolean isVanished(Entity entity) {
        return entity instanceof ServerPlayer && VANISHED.contains(entity.getUUID());
    }

    /** Админ видит ванишнутых: тот же уровень прав, что и у {@code /pjm vanish}. */
    public static boolean isAdmin(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    /** @return новое состояние ваниша. */
    public static boolean toggle(ServerPlayer player) {
        boolean next = !VANISHED.contains(player.getUUID());
        set(player, next);
        return next;
    }

    public static void set(ServerPlayer player, boolean vanished) {
        if (vanished) {
            VANISHED.add(player.getUUID());
        } else {
            VANISHED.remove(player.getUUID());
        }
        persist(player, vanished);
        // Сначала обновляем имя ([V] появляется/уходит), иначе createPlayerInitializing ниже
        // возьмёт закэшированное имя со старой приставкой.
        player.refreshTabListName();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other == player || isAdmin(other)) continue; // себя и админов не трогаем
            other.connection.send(vanished
                    ? new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID()))
                    : ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
        }
    }

    /** Восстанавливает ваниш вошедшего и прячет от него уже ванишнутых. */
    public static void onPlayerLogin(ServerPlayer player) {
        if (persisted(player)) {
            VANISHED.add(player.getUUID());
        }
        boolean self = VANISHED.contains(player.getUUID());
        if (self) {
            player.refreshTabListName(); // строка уже разослана ванильным PlayerList — приставку добавляем вдогонку
        }
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other == player) continue;
            if (self && !isAdmin(other)) {
                other.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
            }
            if (VANISHED.contains(other.getUUID()) && !isAdmin(player)) {
                player.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(other.getUUID())));
            }
        }
    }

    /** Снимаем из сессионного набора: флаг в NBT вернёт ваниш при следующем входе. */
    public static void onPlayerLogout(ServerPlayer player) {
        VANISHED.remove(player.getUUID());
    }

    private static boolean persisted(ServerPlayer player) {
        return player.getPersistentData().getCompound(ServerPlayer.PERSISTED_NBT_TAG).getBoolean(NBT_KEY);
    }

    private static void persist(ServerPlayer player, boolean vanished) {
        CompoundTag persisted = player.getPersistentData().getCompound(ServerPlayer.PERSISTED_NBT_TAG);
        persisted.putBoolean(NBT_KEY, vanished);
        player.getPersistentData().put(ServerPlayer.PERSISTED_NBT_TAG, persisted);
    }
}
