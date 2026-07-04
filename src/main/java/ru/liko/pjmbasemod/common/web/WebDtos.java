package ru.liko.pjmbasemod.common.web;

import java.util.List;

/**
 * DTO веб-панели. Сериализуются Gson-ом; имена полей — контракт фронтенда
 * (web/src/api.ts). Только иммутабельные records — их безопасно читать
 * из HTTP-потоков.
 */
public final class WebDtos {

    private WebDtos() {}

    public record PlayerDto(String uuid, String name, String dim, double x, double y, double z,
                            int ping, String team, String role,
                            boolean banned, boolean voiceMuted, boolean textMuted, int warns) {}

    public record EntityDto(String uuid, String type, String name, String dim,
                            double x, double y, double z, String category) {}

    // ---- запросы действий (тела POST) ----

    public record ExchangeRequest(String code) {}
    public record KickRequest(String uuid, String reason) {}
    /** type ∈ {warn, ban, mute_voice, mute_text}; duration в формате DurationParser ("30m", "permanent"). */
    public record PunishRequest(String uuid, String name, String type, String duration, String reason) {}
    /** type ∈ {ban, mute_voice, mute_text} — что снимаем. */
    public record PardonRequest(String uuid, String name, String type) {}
    public record TeleportRequest(String uuid, String toPlayer, Double x, Double y, Double z, String dim) {}
    public record RemoveEntitiesRequest(List<String> uuids) {}
    public record BulkRemoveRequest(String type, String dim, Double x, Double z, Double radius) {}
}
