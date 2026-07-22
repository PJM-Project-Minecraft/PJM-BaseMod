package ru.liko.pjmbasemod.common.mapmarker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
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
 * старая метка. Хранение — {@link MapMarkerSavedData} (переживает рестарт);
 * чистится при вайпе кампании и при смене/выходе игрока из фракции.
 */
public final class MapMarkerManager {

    /** Допустимые типы меток; клиент маппит их на иконки textures/gui/map/marker_&lt;type&gt;.png. */
    public static final Set<String> TYPES = Set.of("arrow", "infantry", "vehicle", "danger");

    private static final int MAX_PER_PLAYER = 5;
    private static final int MAX_PER_TEAM = 64;

    private MapMarkerManager() {}

    public static void handleAction(ServerPlayer player, MapMarkerActionPacket p) {
        String teamId = Teams.resolvePlayerTeamId(player);
        if (teamId == null) return;
        MapMarkerSavedData data = MapMarkerSavedData.get(player.server);
        LinkedHashMap<UUID, MapMarkerSyncPacket.Entry> map = data.team(teamId);
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
                data.setDirty();
                broadcast(player.server, teamId, map);
            }
            case REMOVE -> {
                MapMarkerSyncPacket.Entry e = map.get(p.id());
                if (e == null) return;
                boolean own = e.owner().equals(player.getGameProfile().getName());
                if (own || FactionCommanderService.isActiveCommander(player) || player.hasPermissions(2)) {
                    map.remove(p.id());
                    data.setDirty();
                    broadcast(player.server, teamId, map);
                }
            }
            case REQUEST -> PjmNetworking.sendToPlayer(player, snapshot(map));
        }
    }

    /**
     * Убрать все метки игрока во всех командах — вызывается при смене фракции,
     * кике из фракции и т.п. Затронутым командам рассылается обновление.
     */
    public static void removeByOwner(MinecraftServer server, String playerName) {
        MapMarkerSavedData data = MapMarkerSavedData.get(server);
        boolean dirty = false;
        for (Map.Entry<String, LinkedHashMap<UUID, MapMarkerSyncPacket.Entry>> team : data.all().entrySet()) {
            if (team.getValue().values().removeIf(e -> e.owner().equals(playerName))) {
                dirty = true;
                broadcast(server, team.getKey(), team.getValue());
            }
        }
        if (dirty) data.setDirty();
    }

    /** Полная очистка (вайп кампании): все команды получают пустой список. */
    public static void clearAll(MinecraftServer server) {
        MapMarkerSavedData.get(server).clearAll();
        PjmNetworking.sendToAll(server, new MapMarkerSyncPacket(List.of()));
    }

    private static int countByOwner(Map<UUID, MapMarkerSyncPacket.Entry> map, String owner) {
        return (int) map.values().stream().filter(e -> e.owner().equals(owner)).count();
    }

    private static void broadcast(MinecraftServer server, String teamId,
                                  Map<UUID, MapMarkerSyncPacket.Entry> map) {
        PjmNetworking.sendToTeam(server, teamId, snapshot(map));
    }

    private static MapMarkerSyncPacket snapshot(Map<UUID, MapMarkerSyncPacket.Entry> map) {
        return new MapMarkerSyncPacket(List.copyOf(map.values()));
    }
}
