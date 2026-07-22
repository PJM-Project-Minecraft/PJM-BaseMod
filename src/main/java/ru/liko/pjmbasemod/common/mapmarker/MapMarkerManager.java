package ru.liko.pjmbasemod.common.mapmarker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.common.faction.FactionCommanderService;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.MapMarkerActionPacket;
import ru.liko.pjmbasemod.common.network.packet.MapMarkerSyncPacket;
import ru.liko.pjmbasemod.common.teams.Teams;

/**
 * Тактические метки на карте: любой игрок команды ставит метки, видимые ТОЛЬКО
 * его команде. Убрать можно свою метку; командир фракции и OP — любую метку команды.
 * Лимит {@link #MAX_PER_PLAYER} на игрока — при превышении вытесняется его самая
 * старая метка. Состояние in-memory (как {@code RadioSpawnManager}) — метки
 * оперативные, рестарт сервера их сбрасывает.
 */
public final class MapMarkerManager {

    /** Допустимые типы меток; клиент маппит их на иконки textures/gui/map/marker_&lt;type&gt;.png. */
    public static final Set<String> TYPES = Set.of("arrow", "infantry", "vehicle", "danger");

    private static final int MAX_PER_PLAYER = 5;
    private static final int MAX_PER_TEAM = 64;

    /** teamId → (id метки → метка), LinkedHashMap — порядок постановки для вытеснения старых. */
    private static final Map<String, LinkedHashMap<UUID, MapMarkerSyncPacket.Entry>> MARKERS =
            new ConcurrentHashMap<>();

    private MapMarkerManager() {}

    public static void handleAction(ServerPlayer player, MapMarkerActionPacket p) {
        String teamId = Teams.resolvePlayerTeamId(player);
        if (teamId == null) return;
        LinkedHashMap<UUID, MapMarkerSyncPacket.Entry> map =
                MARKERS.computeIfAbsent(teamId, k -> new LinkedHashMap<>());
        switch (p.action()) {
            case PLACE -> {
                if (!TYPES.contains(p.markerType())) return;
                String owner = player.getGameProfile().getName();
                // лимит на игрока: вытесняем его самые старые метки
                while (countByOwner(map, owner) >= MAX_PER_PLAYER) {
                    UUID oldest = null;
                    for (MapMarkerSyncPacket.Entry e : map.values()) {
                        if (e.owner().equals(owner)) { oldest = e.id(); break; }
                    }
                    if (oldest == null) break;
                    map.remove(oldest);
                }
                if (map.size() >= MAX_PER_TEAM) { // страховочный лимит команды
                    var it = map.keySet().iterator();
                    it.next();
                    it.remove();
                }
                UUID id = UUID.randomUUID();
                map.put(id, new MapMarkerSyncPacket.Entry(id, p.markerType(), p.x(), p.z(),
                        p.x2(), p.z2(), player.level().dimension().location().toString(),
                        owner, FactionCommanderService.isActiveCommander(player)));
                broadcast(player, teamId, map);
            }
            case REMOVE -> {
                MapMarkerSyncPacket.Entry e = map.get(p.id());
                if (e == null) return;
                boolean own = e.owner().equals(player.getGameProfile().getName());
                if (own || FactionCommanderService.isActiveCommander(player) || player.hasPermissions(2)) {
                    map.remove(p.id());
                    broadcast(player, teamId, map);
                }
            }
            case REQUEST -> PjmNetworking.sendToPlayer(player, snapshot(map));
        }
    }

    private static int countByOwner(Map<UUID, MapMarkerSyncPacket.Entry> map, String owner) {
        return (int) map.values().stream().filter(e -> e.owner().equals(owner)).count();
    }

    private static void broadcast(ServerPlayer player, String teamId,
                                  Map<UUID, MapMarkerSyncPacket.Entry> map) {
        PjmNetworking.sendToTeam(player.server, teamId, snapshot(map));
    }

    private static MapMarkerSyncPacket snapshot(Map<UUID, MapMarkerSyncPacket.Entry> map) {
        return new MapMarkerSyncPacket(List.copyOf(map.values()));
    }
}
