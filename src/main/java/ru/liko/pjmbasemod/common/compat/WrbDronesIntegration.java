package ru.liko.pjmbasemod.common.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import ru.liko.pjmbasemod.common.serverevent.DroneRaidEvent;
import ru.liko.pjmbasemod.common.serverevent.ServerEventManager;
import ru.liko.wrbdrones.api.event.ShahedImpactEvent;
import ru.liko.wrbdrones.api.event.ShahedShotDownEvent;
import ru.liko.wrbdrones.entity.Shahed136Entity;
import ru.liko.wrbdrones.registry.ModEntityTypes;

import javax.annotation.Nullable;
import java.util.UUID;

/** Прямая интеграция с wrbdrones. Не класслоадить без проверки {@link WrbDronesCompat#isLoaded()}. */
final class WrbDronesIntegration {

    private WrbDronesIntegration() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(WrbDronesIntegration::onShahedShotDown);
        NeoForge.EVENT_BUS.addListener(WrbDronesIntegration::onShahedImpacted);
    }

    private static void onShahedShotDown(ShahedShotDownEvent event) {
        Shahed136Entity drone = event.getDrone();
        if (!drone.getTags().contains(DroneRaidEvent.EVENT_DRONE_TAG)) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            ServerEventManager.onEventDroneShotDown(player, drone.getUUID());
        }
    }

    private static void onShahedImpacted(ShahedImpactEvent event) {
        Shahed136Entity drone = event.getDrone();
        if (!drone.getTags().contains(DroneRaidEvent.EVENT_DRONE_TAG)) {
            return;
        }
        ServerEventManager.onEventDroneImpacted(drone.getUUID());
    }

    @Nullable
    static UUID spawnShahed(ServerLevel level, Vec3 spawnPos, float yRot, Vec3 target,
                            float speed, float altitude, boolean terrainFollow, String entityTag) {
        Shahed136Entity shahed = new Shahed136Entity(ModEntityTypes.SHAHED136.get(), level);
        shahed.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        shahed.setYRot(yRot);
        shahed.setTargetPos(target.x, target.y, target.z);
        shahed.setSetSpeed(speed);
        shahed.setSetAltitude(altitude);
        shahed.setTerrainFollow(terrainFollow);
        shahed.addTag(entityTag);
        if (!level.addFreshEntity(shahed)) {
            return null;
        }
        shahed.launch();
        return shahed.getUUID();
    }
}
