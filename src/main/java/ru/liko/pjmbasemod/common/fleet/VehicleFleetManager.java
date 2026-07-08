package ru.liko.pjmbasemod.common.fleet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.garage.GarageType;

import java.util.UUID;

/**
 * Фасад подсистемы «парк техники»: лимиты одновременно активной техники (команда/игрок),
 * кулдаун спавна, регистрация/снятие записей и очистка брошенной техники (см. onServerTick).
 */
public final class VehicleFleetManager {

    /** NBT-ключ метки на сущности (в {@code entity.getPersistentData()}). */
    public static final String TAG = "PjmFleet";

    private VehicleFleetManager() {}

    private static int maxPerTeam(GarageType type) {
        return type == GarageType.AVIATION
                ? Config.getFleetAviationMaxActivePerTeam()
                : Config.getFleetMaxActivePerTeam();
    }

    private static int maxPerPlayer(GarageType type) {
        return type == GarageType.AVIATION
                ? Config.getFleetAviationMaxActivePerPlayer()
                : Config.getFleetMaxActivePerPlayer();
    }

    private static int countForOwner(VehicleFleetSavedData data, UUID owner, GarageType type) {
        int n = 0;
        for (FleetRecord r : data.all()) {
            if (r.type == type && r.ownerId.equals(owner)) n++;
        }
        return n;
    }

    private static int countForTeam(VehicleFleetSavedData data, String teamId, GarageType type) {
        int n = 0;
        for (FleetRecord r : data.all()) {
            if (r.type == type && r.teamId.equals(teamId)) n++;
        }
        return n;
    }

    public static boolean canSpawn(ServerPlayer player, GarageType type) {
        if (!Config.isFleetEnabled()) return true;
        VehicleFleetSavedData data = VehicleFleetSavedData.get(player.server);
        long now = player.server.overworld().getGameTime();

        // 1. Кулдаун игрока.
        int cooldownTicks = Config.getFleetSpawnCooldownSeconds() * 20;
        if (cooldownTicks > 0) {
            long elapsed = now - data.lastSpawn(player.getUUID());
            if (elapsed < cooldownTicks) {
                long remainingSec = (cooldownTicks - elapsed + 19) / 20;
                player.sendSystemMessage(Component.translatable("gui.pjmbasemod.fleet.cooldown", remainingSec));
                return false;
            }
        }

        // 2. Лимит игрока.
        int playerLimit = maxPerPlayer(type);
        if (playerLimit >= 0 && countForOwner(data, player.getUUID(), type) >= playerLimit) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.fleet.limit_player"));
            return false;
        }

        // 3. Лимит команды (только если игрок в команде).
        String team = FrontlineTeams.resolvePlayerTeamId(player);
        if (team != null && !team.isBlank()) {
            int teamLimit = maxPerTeam(type);
            if (teamLimit >= 0 && countForTeam(data, team, type) >= teamLimit) {
                player.sendSystemMessage(Component.translatable(
                        "gui.pjmbasemod.fleet.limit_team", countForTeam(data, team, type), teamLimit));
                return false;
            }
        }
        return true;
    }

    public static void register(Entity entity, ServerPlayer player, String defId, GarageType type) {
        if (!Config.isFleetEnabled()) return;
        MinecraftServer server = player.server;
        VehicleFleetSavedData data = VehicleFleetSavedData.get(server);
        long now = server.overworld().getGameTime();
        String team = FrontlineTeams.resolvePlayerTeamId(player);
        if (team == null) team = "";

        FleetRecord record = new FleetRecord(entity.getUUID(), player.getUUID(), team, defId, type,
                entity.level().dimension(), now, now, false);
        data.put(record);
        data.setLastSpawn(player.getUUID(), now);

        CompoundTag fleet = new CompoundTag();
        fleet.putUUID("Owner", player.getUUID());
        fleet.putString("Team", team);
        fleet.putString("Def", defId);
        fleet.putString("Type", type.name());
        fleet.putLong("Spawn", now);
        entity.getPersistentData().put(TAG, fleet);
    }

    public static void unregister(MinecraftServer server, UUID entityId) {
        if (server == null) return;
        VehicleFleetSavedData.get(server).remove(entityId);
    }
}
