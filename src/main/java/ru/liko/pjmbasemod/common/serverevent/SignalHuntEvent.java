package ru.liko.pjmbasemod.common.serverevent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;
import ru.liko.pjmbasemod.common.network.packet.SignalHuntHudPacket;
import ru.liko.pjmbasemod.common.rank.RankService;
import ru.liko.pjmbasemod.common.serverevent.SignalHuntDefinition.BeaconSnapshot;
import ru.liko.pjmbasemod.common.serverevent.SignalHuntDefinition.SignalHuntSettings;
import ru.liko.pjmbasemod.common.serverevent.SignalHuntDefinition.SignalHuntZone;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Событие «радиоразведка»: в зоне прячется N невидимых маяков. Игроки со
 * Радио-детектором в руке ищут их по индикации actionbar (направление + сила сигнала).
 * Найдя маяк (войдя в {@link SignalHuntSettings#captureRadius}), игрок запускает
 * перехват ПКМ по детектору; удержание позиции {@code captureSeconds} сек — маяк
 * перехвачен, XP начисляется именно этому игроку. Завершается, когда все маяки
 * перехвачены (или таймаут).
 *
 * <p>Маяк — невидимая точка в состоянии события (без entity/блока). Параметры
 * фиксируются снапшотом на старте — reload реестра не меняет идущую радиоразведку.</p>
 */
public final class SignalHuntEvent implements ServerEvent {

    public static final String TYPE_ID = "signal_hunt";

    private static final int START_COLOR = 0x55AAFF;
    private static final int END_COLOR = 0x55FF55;
    private static final int ABORT_COLOR = 0xFFAA22;
    private static final long NOTIFICATION_DURATION_MS = 6000L;

    // Снапшот зоны и параметров.
    private String zoneName = "";
    private String dimension = "minecraft:overworld";
    private double centerX;
    private double centerY;
    private double centerZ;
    private int radius = 200;
    private int beaconCount = 3;
    private int beaconSpread = 150;
    private int signalRadius = 20;
    private int signalMaxDistance = 400;
    private int captureRadius = 6;
    private int captureSeconds = 5;
    private int xpPerBeacon = 30;
    private int maxDurationSeconds = 1200;

    // Состояние.
    private final List<BeaconSnapshot> beacons = new ArrayList<>();
    private int elapsedSeconds;
    private int capturedCount;
    private boolean finished;

    // Активный канал перехвата: игрок + индекс маяка + накопленные секунды.
    @Nullable
    private UUID capturePlayerId;
    private int captureBeaconIndex = -1;
    private int captureProgressSeconds;

    private SignalHuntEvent() {
    }

    public static SignalHuntEvent create(SignalHuntZone zone, SignalHuntSettings settings) {
        SignalHuntEvent event = new SignalHuntEvent();
        event.zoneName = zone.name;
        event.dimension = zone.dimension;
        event.centerX = zone.centerX;
        event.centerY = zone.centerY;
        event.centerZ = zone.centerZ;
        event.radius = zone.radius;
        event.beaconCount = zone.beaconCount;
        event.beaconSpread = zone.beaconSpread;
        event.signalRadius = settings.signalRadius;
        event.signalMaxDistance = settings.signalMaxDistance;
        event.captureRadius = settings.captureRadius;
        event.captureSeconds = settings.captureSeconds;
        event.xpPerBeacon = settings.xpPerBeacon;
        event.maxDurationSeconds = settings.maxDurationMinutes * 60;
        return event;
    }

    @Override
    public String typeId() {
        return TYPE_ID;
    }

    @Override
    public void onStart(MinecraftServer server) {
        spawnBeacons();
        PjmNetworking.sendToAll(server, new NotificationPacket(
                Component.translatable("event.pjmbasemod.signal_hunt.start.title"),
                Component.translatable("event.pjmbasemod.signal_hunt.start.subtitle", zoneName, beaconCount),
                START_COLOR, NOTIFICATION_DURATION_MS));
        Pjmbasemod.LOGGER.info("Events: радиоразведка '{}' началась ({} маяков, зона r={}, разброс {}).",
                zoneName, beaconCount, radius, beaconSpread);
    }

    private void spawnBeacons() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < beaconCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(random.nextDouble()) * beaconSpread;
            BeaconSnapshot beacon = new BeaconSnapshot();
            beacon.x = centerX + Math.cos(angle) * dist;
            beacon.y = centerY;
            beacon.z = centerZ + Math.sin(angle) * dist;
            beacons.add(beacon);
        }
    }

    @Override
    public void tick(MinecraftServer server) {
        if (finished) return;
        elapsedSeconds++;

        // Прогресс канала захвата: проверяем, что игрок всё ещё в радиусе и держит детектор.
        if (capturePlayerId != null && captureBeaconIndex >= 0 && captureBeaconIndex < beacons.size()) {
            BeaconSnapshot beacon = beacons.get(captureBeaconIndex);
            if (beacon.captured) {
                resetCapture();
            } else {
                ServerPlayer player = server.getPlayerList().getPlayer(capturePlayerId);
                if (player == null || !isHoldingDetector(player)
                        || distanceTo(player, beacon) > captureRadius) {
                    resetCapture();
                } else {
                    captureProgressSeconds++;
                    if (captureProgressSeconds >= captureSeconds) {
                        captureBeacon(server, captureBeaconIndex, player);
                        resetCapture();
                    }
                }
            }
        }

        if (capturedCount >= beaconCount) {
            finished = true;
        } else if (elapsedSeconds >= maxDurationSeconds) {
            Pjmbasemod.LOGGER.info("Events: радиоразведка '{}' завершена по таймауту (найдено {}/{}).",
                    zoneName, capturedCount, beaconCount);
            finished = true;
        }
    }

    private void resetCapture() {
        capturePlayerId = null;
        captureBeaconIndex = -1;
        captureProgressSeconds = 0;
    }

    private void captureBeacon(MinecraftServer server, int index, ServerPlayer player) {
        if (index < 0 || index >= beacons.size()) return;
        BeaconSnapshot beacon = beacons.get(index);
        if (beacon.captured) return;
        beacon.captured = true;
        beacon.capturedBy = player.getUUID();
        capturedCount++;

        if (xpPerBeacon > 0) {
            RankService.addXp(player, xpPerBeacon, "event_signal_hunt");
        }
        PjmNetworking.sendToAll(server, new NotificationPacket(
                Component.translatable("event.pjmbasemod.signal_hunt.capture.title"),
                Component.translatable("event.pjmbasemod.signal_hunt.capture.subtitle",
                        player.getGameProfile().getName(), capturedCount, beaconCount),
                END_COLOR, NOTIFICATION_DURATION_MS));
        Pjmbasemod.LOGGER.info("Events: радиоразведка '{}' — игрок '{}' перехватил маяк {}/{}.",
                zoneName, player.getGameProfile().getName(), capturedCount, beaconCount);
    }

    /**
     * Запуск канала перехвата ПКМ по детектору. Вызывается из {@code RadioDetectorItem.use}.
     *
     * @return true — канал запущен/продолжен, false — нет маяка в радиусе захвата.
     */
    public boolean startCapture(ServerPlayer player) {
        if (finished) return false;
        int nearest = nearestUncapturedWithin(player, captureRadius);
        if (nearest < 0) {
            return false;
        }
        // Перезапуск канала на текущего игрока (конкуренция: кто последний нажал ПКМ).
        if (captureBeaconIndex != nearest) {
            captureBeaconIndex = nearest;
            captureProgressSeconds = 0;
        }
        capturePlayerId = player.getUUID();
        return true;
    }

    /**
     * Данные для actionbar-индикации игроку с детектором: ближайший неперехваченный маяк.
     * Сила сигнала: 1.0 внутри {@code signalRadius}, линейно затухает до 0 на {@code signalMaxDistance}.
     */
    @Nullable
    public SignalHuntHudPacket hudFor(ServerPlayer player) {
        if (finished) return SignalHuntHudPacket.inactive();
        int nearest = nearestUncapturedWithin(player, signalMaxDistance);
        if (nearest < 0) {
            return SignalHuntHudPacket.searching(beaconCount, capturedCount);
        }
        BeaconSnapshot beacon = beacons.get(nearest);
        double dist = distanceTo(player, beacon);
        double strength;
        if (dist <= signalRadius) {
            strength = 1.0;
        } else if (dist >= signalMaxDistance) {
            strength = 0.0;
        } else {
            strength = 1.0 - (dist - signalRadius) / (signalMaxDistance - signalRadius);
        }
        // Направление (yRot-стиль: 0=юг, увеличивается по часовой от запада).
        double dx = beacon.x - player.getX();
        double dz = beacon.z - player.getZ();
        float direction = (float) Math.toDegrees(Mth.atan2(-dx, dz));
        boolean captureReady = dist <= captureRadius;
        int captureProgress = (capturePlayerId != null && capturePlayerId.equals(player.getUUID())
                && captureBeaconIndex == nearest) ? captureProgressSeconds : 0;
        return new SignalHuntHudPacket(true, strength, direction, captureReady,
                captureProgress, captureSeconds, capturedCount, beaconCount);
    }

    private int nearestUncapturedWithin(ServerPlayer player, double maxDistance) {
        int best = -1;
        double bestDist = maxDistance;
        for (int i = 0; i < beacons.size(); i++) {
            BeaconSnapshot beacon = beacons.get(i);
            if (beacon.captured) continue;
            double d = distanceTo(player, beacon);
            if (d <= bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private static double distanceTo(ServerPlayer player, BeaconSnapshot beacon) {
        return Math.sqrt(player.distanceToSqr(beacon.x, beacon.y, beacon.z));
    }

    private static boolean isHoldingDetector(ServerPlayer player) {
        return player.getMainHandItem().is(ru.liko.pjmbasemod.common.init.PjmItems.RADIO_DETECTOR.get())
                || player.getOffhandItem().is(ru.liko.pjmbasemod.common.init.PjmItems.RADIO_DETECTOR.get());
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void onStop(MinecraftServer server, boolean aborted) {
        resetCapture();
        Component subtitle;
        int color;
        if (aborted) {
            subtitle = Component.translatable("event.pjmbasemod.signal_hunt.end.aborted.subtitle",
                    capturedCount, beaconCount);
            color = ABORT_COLOR;
        } else if (capturedCount >= beaconCount) {
            subtitle = Component.translatable("event.pjmbasemod.signal_hunt.end.success.subtitle",
                    capturedCount, beaconCount);
            color = END_COLOR;
        } else {
            subtitle = Component.translatable("event.pjmbasemod.signal_hunt.end.timeout.subtitle",
                    capturedCount, beaconCount);
            color = ABORT_COLOR;
        }
        PjmNetworking.sendToAll(server, new NotificationPacket(
                Component.translatable("event.pjmbasemod.signal_hunt.end.title"),
                subtitle, color, NOTIFICATION_DURATION_MS));
        Pjmbasemod.LOGGER.info("Events: радиоразведка '{}' окончена (aborted={}, найдено {}/{}).",
                zoneName, aborted, capturedCount, beaconCount);
    }

    @Override
    public String statusLine() {
        return String.format("радиоразведка '%s': маяков %d/%d, прошло %d сек",
                zoneName, capturedCount, beaconCount, elapsedSeconds);
    }

    @Override
    public EventZone zone() {
        return new EventZone(zoneName, dimension, (int) centerX, (int) centerY, (int) centerZ, radius);
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ZoneName", zoneName);
        tag.putString("Dimension", dimension);
        tag.putDouble("CenterX", centerX);
        tag.putDouble("CenterY", centerY);
        tag.putDouble("CenterZ", centerZ);
        tag.putInt("Radius", radius);
        tag.putInt("BeaconCount", beaconCount);
        tag.putInt("BeaconSpread", beaconSpread);
        tag.putInt("SignalRadius", signalRadius);
        tag.putInt("SignalMaxDistance", signalMaxDistance);
        tag.putInt("CaptureRadius", captureRadius);
        tag.putInt("CaptureSeconds", captureSeconds);
        tag.putInt("XpPerBeacon", xpPerBeacon);
        tag.putInt("MaxDurationSeconds", maxDurationSeconds);
        ListTag list = new ListTag();
        for (BeaconSnapshot beacon : beacons) {
            CompoundTag b = new CompoundTag();
            b.putDouble("X", beacon.x);
            b.putDouble("Y", beacon.y);
            b.putDouble("Z", beacon.z);
            b.putBoolean("Captured", beacon.captured);
            if (beacon.capturedBy != null) b.putUUID("CapturedBy", beacon.capturedBy);
            list.add(b);
        }
        tag.put("Beacons", list);
        tag.putInt("ElapsedSeconds", elapsedSeconds);
        tag.putInt("CapturedCount", capturedCount);
        tag.putBoolean("Finished", finished);
        if (capturePlayerId != null) tag.putUUID("CapturePlayerId", capturePlayerId);
        tag.putInt("CaptureBeaconIndex", captureBeaconIndex);
        tag.putInt("CaptureProgressSeconds", captureProgressSeconds);
        return tag;
    }

    public static SignalHuntEvent load(CompoundTag tag) {
        SignalHuntEvent event = new SignalHuntEvent();
        event.zoneName = tag.getString("ZoneName");
        event.dimension = tag.getString("Dimension");
        event.centerX = tag.getDouble("CenterX");
        event.centerY = tag.getDouble("CenterY");
        event.centerZ = tag.getDouble("CenterZ");
        event.radius = tag.getInt("Radius");
        event.beaconCount = tag.getInt("BeaconCount");
        event.beaconSpread = tag.getInt("BeaconSpread");
        event.signalRadius = tag.getInt("SignalRadius");
        event.signalMaxDistance = tag.getInt("SignalMaxDistance");
        event.captureRadius = tag.getInt("CaptureRadius");
        event.captureSeconds = tag.getInt("CaptureSeconds");
        event.xpPerBeacon = tag.getInt("XpPerBeacon");
        event.maxDurationSeconds = tag.getInt("MaxDurationSeconds");
        ListTag list = tag.getList("Beacons", Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag b)) continue;
            BeaconSnapshot beacon = new BeaconSnapshot();
            beacon.x = b.getDouble("X");
            beacon.y = b.getDouble("Y");
            beacon.z = b.getDouble("Z");
            beacon.captured = b.getBoolean("Captured");
            if (b.hasUUID("CapturedBy")) beacon.capturedBy = b.getUUID("CapturedBy");
            event.beacons.add(beacon);
        }
        event.elapsedSeconds = tag.getInt("ElapsedSeconds");
        event.capturedCount = tag.getInt("CapturedCount");
        event.finished = tag.getBoolean("Finished");
        if (tag.hasUUID("CapturePlayerId")) event.capturePlayerId = tag.getUUID("CapturePlayerId");
        event.captureBeaconIndex = tag.getInt("CaptureBeaconIndex");
        event.captureProgressSeconds = tag.getInt("CaptureProgressSeconds");
        return event;
    }
}
