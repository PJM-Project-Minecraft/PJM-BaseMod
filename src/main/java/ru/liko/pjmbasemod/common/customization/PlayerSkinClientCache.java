package ru.liko.pjmbasemod.common.customization;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиентский кэш скинов игроков для рендера (UUID → skinId).
 *
 * <p>Намеренно лежит в common ({@code src/main}), а не в client: его читает
 * {@code PlayerRendererMixin}, который тоже находится в {@code src/main} и не может
 * импортировать классы из {@code src/client}. Пишется клиентским обработчиком пакетов
 * ({@code ClientPacketHandlersImpl}), на dedicated-сервере остаётся пустым.</p>
 */
public final class PlayerSkinClientCache {

    private static final Map<UUID, String> SKINS = new ConcurrentHashMap<>();

    private PlayerSkinClientCache() {
    }

    public static void put(UUID playerId, String skinId) {
        if (playerId == null) return;
        if (skinId == null || skinId.isBlank()) {
            SKINS.remove(playerId);
            return;
        }
        SKINS.put(playerId, skinId);
    }

    @Nullable
    public static String get(UUID playerId) {
        return playerId == null ? null : SKINS.get(playerId);
    }

    public static void remove(UUID playerId) {
        if (playerId != null) SKINS.remove(playerId);
    }
}
