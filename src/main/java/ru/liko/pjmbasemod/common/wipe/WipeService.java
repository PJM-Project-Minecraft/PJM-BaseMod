package ru.liko.pjmbasemod.common.wipe;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.scores.PlayerTeam;
import ru.liko.pjmbasemod.common.faction.FactionCommanderSavedData;
import ru.liko.pjmbasemod.common.faction.FactionCommanderService;
import ru.liko.pjmbasemod.common.faction.FactionDeputySavedData;
import ru.liko.pjmbasemod.common.faction.FactionMenuService;
import ru.liko.pjmbasemod.common.faction.FactionOrderManager;
import ru.liko.pjmbasemod.common.faction.FactionOrderSavedData;
import ru.liko.pjmbasemod.common.faction.FactionSelectionSavedData;
import ru.liko.pjmbasemod.common.fleet.VehicleFleetSavedData;
import ru.liko.pjmbasemod.common.garage.GarageSavedData;
import ru.liko.pjmbasemod.common.rank.RankSavedData;
import ru.liko.pjmbasemod.common.rank.RankService;
import ru.liko.pjmbasemod.common.role.RoleSavedData;
import ru.liko.pjmbasemod.common.role.RoleService;
import ru.liko.pjmbasemod.common.warehouse.WarehousePersonalBudgetSavedData;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Оркестратор вайпа сезонного прогресса игроков. Не трогает админ-разметку
 * (регионы, зоны базы, настройки склада, терминалы, скины, баны) и накопленные
 * очки складов — склад считается инфраструктурой фракции и переживает вайп.
 */
public final class WipeService {

    private WipeService() {}

    /** Сброс званий (XP) у всех игроков. */
    public static void wipeRanks(MinecraftServer server) {
        RankSavedData.get(server).clearAll();
        resyncAll(server);
    }

    /** Сброс званий (XP) у указанного набора игроков (членов команды). */
    public static void wipeRanksForTeam(MinecraftServer server, Set<UUID> members) {
        RankSavedData.get(server).clearPlayers(members);
        resyncAll(server);
    }

    /**
     * Полный вайп прогресса игроков. Админ-разметка сохраняется.
     *
     * <p>Накопленные очки складов ({@code WarehouseSavedData}) намеренно НЕ чистятся:
     * склад — инфраструктура фракции, а не личный прогресс. Личные бюджеты сбрасываются,
     * иначе в новом сезоне игрок стартовал бы с израсходованным лимитом прошлого.</p>
     */
    public static void wipeAll(MinecraftServer server) {
        RankSavedData.get(server).clearAll();
        WarehousePersonalBudgetSavedData.get(server).clearAll();
        GarageSavedData.get(server).clearVehicles();
        VehicleFleetSavedData.get(server).clearAll();
        FactionSelectionSavedData.get(server).clearAll();
        FactionCommanderSavedData.get(server).clearAll();
        FactionDeputySavedData.get(server).clearAll();
        FactionOrderSavedData.get(server).clearAll();
        RoleSavedData.get(server).clearAll();
        ru.liko.pjmbasemod.common.mapmarker.MapMarkerManager.clearAll(server);
        ru.liko.pjmbasemod.common.missile.MissileStrikeManager.clearAll(server);
        resyncAll(server);
    }

    /**
     * Резолв UUID членов scoreboard-команды: онлайн — напрямую, офлайн — через
     * кэш профилей сервера. Имена, которых нет в кэше, молча пропускаются
     * (вызывающий может сравнить размеры и сообщить о пропущенных).
     */
    public static Set<UUID> resolveTeamMemberUuids(MinecraftServer server, String teamId) {
        Set<UUID> result = new LinkedHashSet<>();
        PlayerTeam team = server.getScoreboard().getPlayerTeam(teamId);
        if (team == null) return result;
        GameProfileCache cache = server.getProfileCache();
        for (String name : team.getPlayers()) {
            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
            if (online != null) {
                result.add(online.getUUID());
                continue;
            }
            if (cache != null) {
                cache.get(name).ifPresent(profile -> result.add(profile.getId()));
            }
        }
        return result;
    }

    /** Мгновенно обновляет клиентов онлайн-игроков после вайпа — без релога. */
    private static void resyncAll(MinecraftServer server) {
        RankService.syncAll(server);
        RoleService.syncAll(server);
        FactionCommanderService.syncAll(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            FactionOrderManager.syncTo(player);
            FactionMenuService.onPlayerLogin(player);
        }
    }
}
