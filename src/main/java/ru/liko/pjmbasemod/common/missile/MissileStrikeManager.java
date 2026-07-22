package ru.liko.pjmbasemod.common.missile;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.basezone.BaseZoneManager;
import ru.liko.pjmbasemod.common.compat.SbwMissileCompat;
import ru.liko.pjmbasemod.common.entity.StrategicMissileEntity;
import ru.liko.pjmbasemod.common.faction.FactionCommanderService;
import ru.liko.pjmbasemod.common.init.PjmEntities;
import ru.liko.pjmbasemod.common.logging.LogCategory;
import ru.liko.pjmbasemod.common.logging.PjmActionLogger;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.MissileCatalogSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.MissileStrikeActionPacket;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;
import ru.liko.pjmbasemod.common.teams.Teams;
import ru.liko.pjmbasemod.common.warehouse.WarehousePoolCategory;
import ru.liko.pjmbasemod.common.warehouse.WarehouseSavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Авторитетный серверный интерфейс заказа стратегических ракетных ударов. */
public final class MissileStrikeManager {

    private static final double REQUIRED_OBSERVER_DISTANCE = 192.0;
    private static final double ENEMY_WARNING_DISTANCE = 256.0;
    private static final double BASE_SAFETY_BUFFER = 6.0;

    private MissileStrikeManager() {}

    public static void handleAction(ServerPlayer player, MissileStrikeActionPacket packet) {
        if (player == null || packet == null) return;
        if (packet.action() == MissileStrikeActionPacket.Action.REQUEST) {
            sendCatalog(player);
            return;
        }
        launch(player, packet.missileId(), packet.x(), packet.z());
    }

    public static void sendCatalog(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        String teamId = authorizedTeam(player);
        boolean authorized = teamId != null;
        boolean admin = player.hasPermissions(2);
        String warehouseId = authorized ? warehouseForTeam(teamId) : "";
        WarehouseSavedData stock = WarehouseSavedData.get(server);
        int supply = !warehouseId.isBlank() && stock.exists(warehouseId)
                ? stock.getPoints(warehouseId, WarehousePoolCategory.SUPPLY) : 0;
        long cooldown = authorized && !admin
                ? MissileStrikeSavedData.get(server).remainingSeconds(teamId, System.currentTimeMillis()) : 0L;
        boolean active = authorized && hasActiveStrike(server, teamId);

        List<MissileCatalogSyncPacket.Entry> entries = new ArrayList<>();
        for (MissileDefinition definition : MissileRegistry.get().all()) {
            if (!definition.enabled()) continue;
            entries.add(new MissileCatalogSyncPacket.Entry(
                    definition.id(), definition.displayName(), definition.translationKey(),
                    definition.supplyCost(), definition.cooldownSeconds(), definition.flightSeconds(),
                    definition.radius(),
                    definition.trajectoryType() == MissileDefinition.Trajectory.BALLISTIC));
        }
        PjmNetworking.sendToPlayer(player, new MissileCatalogSyncPacket(
                authorized, SbwMissileCompat.available(), active, cooldown,
                warehouseId, supply, List.copyOf(entries)));
    }

    private static void launch(ServerPlayer player, String missileId, int x, int z) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        String teamId = authorizedTeam(player);
        if (teamId == null) {
            reject(player, "gui.pjmbasemod.missile.error.permission");
            return;
        }
        if (!SbwMissileCompat.available()) {
            reject(player, "gui.pjmbasemod.missile.error.sbw");
            return;
        }

        MissileDefinition definition = MissileRegistry.get().find(missileId);
        if (definition == null || !definition.enabled()) {
            reject(player, "gui.pjmbasemod.missile.error.unknown");
            return;
        }
        if (hasActiveStrike(server, teamId)) {
            reject(player, "gui.pjmbasemod.missile.error.active");
            return;
        }

        boolean admin = player.hasPermissions(2);
        long remaining = MissileStrikeSavedData.get(server)
                .remainingSeconds(teamId, System.currentTimeMillis());
        if (!admin && remaining > 0) {
            player.displayClientMessage(Component.translatable(
                    "gui.pjmbasemod.missile.error.cooldown", remaining), true);
            sendCatalog(player);
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos column = new BlockPos(x, level.getMinBuildHeight(), z);
        if (!level.getWorldBorder().isWithinBounds(column)) {
            reject(player, "gui.pjmbasemod.missile.error.border");
            return;
        }
        if (!level.hasChunkAt(column)) {
            reject(player, "gui.pjmbasemod.missile.error.unloaded");
            return;
        }
        int targetY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        Vec3 target = new Vec3(x + 0.5, targetY + 0.2, z + 0.5);
        if (!admin && !hasObserverNear(level, target)) {
            reject(player, "gui.pjmbasemod.missile.error.no_observer");
            return;
        }

        String dimension = level.dimension().location().toString();
        double protectedRadius = definition.radius() * 2.0 + BASE_SAFETY_BUFFER;
        if (BaseZoneManager.intersectsProtectedStrikeArea(
                server, dimension, target.x, target.z, protectedRadius)) {
            reject(player, "gui.pjmbasemod.missile.error.basezone");
            return;
        }

        WarehouseSavedData stock = WarehouseSavedData.get(server);
        String warehouseId = warehouseForTeam(teamId);
        int cost = definition.supplyCost();
        boolean charged = false;
        if (!admin && cost > 0) {
            if (warehouseId.isBlank() || !stock.exists(warehouseId)) {
                reject(player, "gui.pjmbasemod.missile.error.warehouse");
                return;
            }
            if (!stock.trySpend(warehouseId, WarehousePoolCategory.SUPPLY, cost)) {
                player.displayClientMessage(Component.translatable(
                        "gui.pjmbasemod.missile.error.supply", cost), true);
                sendCatalog(player);
                return;
            }
            charged = true;
        }

        StrategicMissileEntity missile = PjmEntities.STRATEGIC_MISSILE.get().create(level);
        if (missile == null) {
            refund(stock, warehouseId, cost, charged);
            reject(player, "gui.pjmbasemod.missile.error.spawn");
            return;
        }

        Vec3 start = chooseStart(level, target, definition);
        // addFreshEntity требует загруженный стартовый чанк; дальше сущность ведёт короткий runtime-ticket.
        level.getChunk(BlockPos.containing(start));
        missile.configure(definition, teamId, player.getUUID(), start, target);
        if (!level.addFreshEntity(missile)) {
            refund(stock, warehouseId, cost, charged);
            reject(player, "gui.pjmbasemod.missile.error.spawn");
            return;
        }

        MissileStrikeSavedData.get(server).startCooldown(
                teamId, definition.cooldownSeconds(), System.currentTimeMillis());
        notifyLaunch(server, level, player, teamId, definition, target);
        PjmActionLogger.instance().logSubsystem(LogCategory.FACTION,
                "Ракетный удар: " + player.getScoreboardName() + " [" + teamId + "] запустил "
                        + definition.id() + " по " + x + "," + z + " в " + dimension);
        sendCatalog(player);
    }

    private static Vec3 chooseStart(ServerLevel level, Vec3 target, MissileDefinition definition) {
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double x = target.x + Math.cos(angle) * definition.spawnDistance();
        double z = target.z + Math.sin(angle) * definition.spawnDistance();
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(x), (int) Math.floor(z));
        double y;
        if (definition.trajectoryType() == MissileDefinition.Trajectory.BALLISTIC) {
            y = Math.min(level.getMaxBuildHeight() - 16.0, target.y + 40.0);
        } else {
            y = Math.min(level.getMaxBuildHeight() - 8.0, surface + definition.cruiseHeight());
        }
        return new Vec3(x, y, z);
    }

    private static void notifyLaunch(MinecraftServer server, ServerLevel level, ServerPlayer commander,
                                     String teamId, MissileDefinition definition, Vec3 target) {
        Component missileName = displayName(definition);
        PjmNetworking.sendToTeam(server, teamId, new NotificationPacket(
                Component.translatable("gui.pjmbasemod.missile.launch.title"),
                Component.translatable("gui.pjmbasemod.missile.launch.subtitle", missileName,
                        (int) target.x, (int) target.z, definition.flightSeconds()),
                0xE6A640, 5000L));

        double warningSq = ENEMY_WARNING_DISTANCE * ENEMY_WARNING_DISTANCE;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == commander || other.serverLevel() != level) continue;
            if (teamId.equals(Teams.resolvePlayerTeamId(other))) continue;
            if (other.distanceToSqr(target) > warningSq) continue;
            PjmNetworking.sendToPlayer(other, new NotificationPacket(
                    Component.translatable("gui.pjmbasemod.missile.warning.title"),
                    Component.translatable("gui.pjmbasemod.missile.warning.subtitle"),
                    0xD6453D, 5000L));
        }
    }

    private static Component displayName(MissileDefinition definition) {
        return definition.translationKey().isBlank()
                ? Component.literal(definition.displayName())
                : Component.translatableWithFallback(definition.translationKey(), definition.displayName());
    }

    private static boolean hasObserverNear(ServerLevel level, Vec3 target) {
        double maxSq = REQUIRED_OBSERVER_DISTANCE * REQUIRED_OBSERVER_DISTANCE;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(target) <= maxSq) return true;
        }
        return false;
    }

    private static boolean hasActiveStrike(MinecraftServer server, String teamId) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof StrategicMissileEntity missile
                        && !missile.isRemoved() && teamId.equals(missile.getTeamId())) return true;
            }
        }
        return false;
    }

    public static void clearAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            List<StrategicMissileEntity> missiles = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof StrategicMissileEntity missile) missiles.add(missile);
            }
            missiles.forEach(Entity::discard);
        }
        MissileStrikeSavedData.get(server).clearAll();
    }

    @Nullable
    private static String authorizedTeam(ServerPlayer player) {
        String commanderTeam = FactionCommanderService.activeCommanderTeam(player);
        if (commanderTeam != null) return commanderTeam;
        return player.hasPermissions(2) ? Teams.resolvePlayerTeamId(player) : null;
    }

    private static String warehouseForTeam(String teamId) {
        if (teamId == null) return "";
        return Config.getCapturePointWarehouseByTeam()
                .getOrDefault(teamId.toLowerCase(Locale.ROOT), "");
    }

    private static void refund(WarehouseSavedData stock, String warehouseId, int cost, boolean charged) {
        if (charged) stock.addPoints(warehouseId, WarehousePoolCategory.SUPPLY, cost);
    }

    private static void reject(ServerPlayer player, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey), true);
        sendCatalog(player);
    }
}
