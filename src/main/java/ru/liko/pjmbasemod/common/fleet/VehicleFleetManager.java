package ru.liko.pjmbasemod.common.fleet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.teams.Teams;
import ru.liko.pjmbasemod.common.garage.GarageType;
import ru.liko.pjmbasemod.common.garage.VehicleDefinition;
import ru.liko.pjmbasemod.common.garage.VehicleRegistry;
import ru.liko.pjmbasemod.common.logging.LogCategory;
import ru.liko.pjmbasemod.common.logging.PjmActionLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Фасад подсистемы «парк техники»: лимиты одновременно активной техники (команда/игрок),
 * кулдаун спавна, регистрация/снятие записей и очистка брошенной техники (см. onServerTick).
 */
public final class VehicleFleetManager {

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
        String team = Teams.resolvePlayerTeamId(player);
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
        String team = Teams.resolvePlayerTeamId(player);
        if (team == null) team = "";

        FleetRecord record = new FleetRecord(entity.getUUID(), player.getUUID(), team, defId, type,
                entity.level().dimension(), now, now, false, entity.blockPosition());
        data.put(record);
        data.setLastSpawn(player.getUUID(), now);
    }

    public static void unregister(MinecraftServer server, UUID entityId) {
        if (server == null) return;
        VehicleFleetSavedData.get(server).remove(entityId);
    }

    private static int tickCounter = 0;

    public static void onServerTick(MinecraftServer server) {
        if (!Config.isFleetEnabled()) return;
        if (++tickCounter < 20) return;
        tickCounter = 0;

        VehicleFleetSavedData data = VehicleFleetSavedData.get(server);
        long now = server.overworld().getGameTime();
        int timeoutTicks = Config.getFleetAbandonTimeoutSeconds() * 20;
        int warnTicks = Config.getFleetAbandonWarningSeconds() * 20;

        List<UUID> toRemove = new ArrayList<>();
        for (FleetRecord record : data.all()) {
            ServerLevel level = server.getLevel(record.dimension);
            Entity entity = level == null ? null : level.getEntity(record.entityId);

            // Сущность загружена, но удалена (уничтожена/сдана/kill) → снять запись.
            if (entity != null && entity.isRemoved()) {
                toRemove.add(record.entityId);
                continue;
            }
            // Сущности нет в индексе: либо действительно исчезла, либо её чанк выгружен.
            if (entity == null) {
                boolean chunkLoaded = level != null
                        && level.hasChunk(record.lastPos.getX() >> 4, record.lastPos.getZ() >> 4);
                if (chunkLoaded) {
                    // Чанк загружен, а сущности нет → техника действительно исчезла.
                    toRemove.add(record.entityId);
                }
                // Иначе чанк выгружен/измерение недоступно — слот честно занят, запись не трогаем.
                continue;
            }
            // Сущность жива — обновляем последнюю известную позицию.
            BlockPos pos = entity.blockPosition();
            if (!pos.equals(record.lastPos)) {
                record.lastPos = pos;
                data.setDirty();
            }
            // Есть водитель/пассажир → техника «живая», сбросить отсчёт и предупреждение.
            if (!entity.getPassengers().isEmpty()) {
                if (record.lastOccupiedGameTime != now || record.warned) {
                    record.lastOccupiedGameTime = now;
                    record.warned = false;
                    data.setDirty();
                }
                continue;
            }
            long idle = now - record.lastOccupiedGameTime;
            if (idle > timeoutTicks + warnTicks) {
                entity.discard();
                toRemove.add(record.entityId);
                PjmActionLogger.instance().logSubsystem(LogCategory.GARAGE,
                        "Брошенная техника удалена: " + displayName(record)
                                + " @ " + record.dimension.location());
            } else if (idle > timeoutTicks && !record.warned) {
                record.warned = true;
                data.setDirty();
                warnAbandon(server, entity, record, warnTicks);
            }
        }
        for (UUID id : toRemove) {
            data.remove(id);
        }
    }

    private static String displayName(FleetRecord record) {
        VehicleDefinition def = VehicleRegistry.get().get(record.defId);
        return def != null ? def.displayName() : record.defId;
    }

    private static void warnAbandon(MinecraftServer server, Entity entity, FleetRecord record, int warnTicks) {
        int seconds = Math.max(1, warnTicks / 20);
        String name = displayName(record);
        // Владельцу — системное сообщение, если онлайн.
        ServerPlayer owner = server.getPlayerList().getPlayer(record.ownerId);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("gui.pjmbasemod.fleet.abandon_warning", name, seconds));
        }
        // Игрокам рядом — actionbar.
        if (entity.level() instanceof ServerLevel level) {
            for (ServerPlayer near : level.getPlayers(p -> p.distanceToSqr(entity) <= 32.0 * 32.0)) {
                near.displayClientMessage(
                        Component.translatable("gui.pjmbasemod.fleet.abandon_warning", name, seconds), true);
            }
        }
    }
}
