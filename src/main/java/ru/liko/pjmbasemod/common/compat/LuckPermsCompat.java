package ru.liko.pjmbasemod.common.compat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.Optional;

/**
 * Прямой строковый запрос прав у LuckPerms — в обход реестра нод NeoForge PermissionAPI.
 *
 * <p>NeoForge регистрирует permission-ноды только один раз на {@code PermissionGatherEvent.Nodes}
 * (старт сервера). Донат-ключи склада берутся из {@code items.json}, поэтому ключ, добавленный
 * после старта (правка файла + {@code /pjm reload}), не имеет зарегистрированной ноды и через
 * {@code PermissionAPI.getPermission} недоступен. LuckPerms же резолвит <i>любую</i> строку прав
 * независимо от регистрации — этот класс даёт fallback, работающий без рестарта.</p>
 *
 * <p>Soft-dep: LuckPerms подключён {@code compileOnly}. Если мод не установлен —
 * {@link #check} возвращает {@link Optional#empty()}, и вызывающий код откатывается к ванильному OP.</p>
 */
public final class LuckPermsCompat {

    private LuckPermsCompat() {}

    private static Boolean loaded; // ленивая инициализация: ModList готов только после загрузки модов

    private static boolean isLoaded() {
        Boolean l = loaded;
        if (l == null) {
            l = ModList.get() != null && ModList.get().isLoaded("luckperms");
            loaded = l;
        }
        return l;
    }

    /**
     * Проверяет право игрока по строке напрямую у LuckPerms.
     *
     * @return {@code Optional.of(true/false)} с вердиктом LuckPerms, либо {@link Optional#empty()},
     *         если LuckPerms не установлен/недоступен (тогда вызывающий сам решает, что делать).
     */
    public static Optional<Boolean> check(ServerPlayer player, String permission) {
        if (player == null || permission == null || permission.isBlank() || !isLoaded()) {
            return Optional.empty();
        }
        try {
            LuckPerms lp = LuckPermsProvider.get();
            var user = lp.getPlayerAdapter(ServerPlayer.class).getUser(player);
            return Optional.of(user.getCachedData().getPermissionData()
                    .checkPermission(permission).asBoolean());
        } catch (Throwable t) {
            // LuckPerms ещё не готов или API изменилось — безопасный откат к OP на стороне вызывающего.
            return Optional.empty();
        }
    }
}
