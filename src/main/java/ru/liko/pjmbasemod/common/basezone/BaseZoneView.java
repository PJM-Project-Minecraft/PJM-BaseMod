package ru.liko.pjmbasemod.common.basezone;

/**
 * Клиентский снимок зоны базы для карты (2D — без Y). {@code ownerColor} уже разрешён на сервере
 * (у клиента нет доступа к scoreboard-командам сервера через {@code Teams}).
 */
public record BaseZoneView(
        String displayName,
        String dimension,
        String owner,
        int ownerColor,
        int minX,
        int minZ,
        int maxX,
        int maxZ
) {}
