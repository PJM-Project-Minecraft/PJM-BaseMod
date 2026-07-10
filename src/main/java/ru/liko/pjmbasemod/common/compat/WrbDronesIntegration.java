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
                            float speedKmh, float altitude, boolean terrainFollow, String entityTag) {
        // Точка спавна обычно далеко от игроков (spawnDistance ~600 блоков) — чанк выгружен и
        // без тикета не прогрузится. addFreshEntity размещает сущность, но она не тикает, пока
        // чанк не загружен, а handleChunkLoading (который форсит 3x3) сам вызывается лишь из
        // тика. Поэтому одного getChunk мало: чанк грузится синхронно, но без forced-тикета
        // менеджер чанков выгружает его до первого тика дрона — дрон застывает навсегда.
        // Ставим forced-тикет (setChunkForced) + синхронно грузим (getChunk) для немедленного
        // размещения. На первом тике handleChunkLoading форсит свой 3x3 вокруг спавна — он
        // включает этот чанк, и когда дрон уходит, unloadChunks() снимает форсаж (чанк уже в
        // loadedChunks). Утечки нет: единственный риск — discard до первого тика (loadedChunks
        // ещё null), но на практике спавн и остановка события не случаются в один тик.
        int cx = net.minecraft.util.Mth.floor(spawnPos.x) >> 4;
        int cz = net.minecraft.util.Mth.floor(spawnPos.z) >> 4;
        level.setChunkForced(cx, cz, true);
        level.getChunk(cx, cz);

        // Скорость/высота — в км/ч / блоках (как в конфиге WRBDrones). Реальный радио-запуск
        // (LaunchShahedPacket) клампит к [min_speed_kmh, max_speed_kmh] / [min/max_altitude] из
        // ServerConfig и переводит км/ч во внутреннюю единицу (/72). Делаем так же — иначе при
        // setSpeed ниже ~2.08 (min 150кмч/72) динамика вырождается: дрон почти висит и не пикирует.
        float minSpeedKmh = ru.liko.wrbdrones.config.ServerConfig.SHAHED136_MIN_SPEED_KMH.get().floatValue();
        float maxSpeedKmh = ru.liko.wrbdrones.config.ServerConfig.SHAHED136_MAX_SPEED_KMH.get().floatValue();
        float clampedSpeedKmh = net.minecraft.util.Mth.clamp(speedKmh, minSpeedKmh, maxSpeedKmh);
        float minAlt = ru.liko.wrbdrones.config.ServerConfig.SHAHED136_MIN_ALTITUDE.get().floatValue();
        float maxAlt = ru.liko.wrbdrones.config.ServerConfig.SHAHED136_MAX_ALTITUDE.get().floatValue();
        float clampedAlt = net.minecraft.util.Mth.clamp(altitude, minAlt, maxAlt);

        Shahed136Entity shahed = new Shahed136Entity(ModEntityTypes.SHAHED136.get(), level);
        shahed.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        shahed.setYRot(yRot);
        shahed.setTargetPos(target.x, target.y, target.z);
        shahed.setSetSpeed(clampedSpeedKmh / 72.0f);
        shahed.setSetAltitude(clampedAlt);
        shahed.setTerrainFollow(terrainFollow);
        shahed.addTag(entityTag);
        if (!level.addFreshEntity(shahed)) {
            // Сущность не добавилась — откатываем форсированный чанк спавна, иначе утечка.
            level.setChunkForced(cx, cz, false);
            return null;
        }
        shahed.launch();
        return shahed.getUUID();
    }
}
