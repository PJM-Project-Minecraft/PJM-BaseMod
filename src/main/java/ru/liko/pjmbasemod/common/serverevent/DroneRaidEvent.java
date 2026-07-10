package ru.liko.pjmbasemod.common.serverevent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.compat.WrbDronesCompat;
import ru.liko.pjmbasemod.common.teams.Teams;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;
import ru.liko.pjmbasemod.common.rank.RankService;
import ru.liko.pjmbasemod.common.serverevent.DroneRaidDefinition.RaidPoint;
import ru.liko.pjmbasemod.common.serverevent.DroneRaidDefinition.RaidSettings;
import ru.liko.pjmbasemod.common.serverevent.DroneRaidDefinition.WaveProfile;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Событие «налёт дронов»: волны Shahed-136 (WRBDrones) атакуют настроенную точку.
 * Поддерживает два состава: {@link DroneRaidDefinition#COMPOSITION_SHAHED_ONLY} — одинаковые
 * волны; {@link DroneRaidDefinition#COMPOSITION_COMBINED} — волны с разными параметрами
 * из {@link RaidSettings#combinedProfiles}. При доле дронов, упавших в цель, выше порога —
 * налёт считается проигранным: атакуемой команде списывается XP (пропорционально попаданиям).
 * Параметры фиксируются снапшотом на старте — reload реестра не меняет идущий налёт.
 */
public final class DroneRaidEvent implements ServerEvent {

    public static final String TYPE_ID = "drone_raid";
    /** Command-tag на «событийных» дронах: персистентен в NBT, по нему опознаётся сбитие/попадание. */
    public static final String EVENT_DRONE_TAG = "pjm_event_drone";

    private static final int START_COLOR = 0xFF4444;
    private static final int END_COLOR = 0x55FF55;
    private static final int LOSS_COLOR = 0xFF3322;
    private static final long NOTIFICATION_DURATION_MS = 6000L;

    // Снапшот точки и параметров.
    private String pointName = "";
    private String dimension = "minecraft:overworld";
    private double targetX;
    private double targetY;
    private double targetZ;
    private int radius = 80;
    private String teamId = "";
    private int waveCount;
    private int dronesPerWave;
    private int waveIntervalSeconds;
    private int spawnDistance;
    private int spawnAltitude;
    private float speed;
    private boolean terrainFollow;
    private int xpPerKill;
    private int maxDurationSeconds;
    private int xpLossPerHit;
    private double lossThreshold;
    private String composition = DroneRaidDefinition.COMPOSITION_SHAHED_ONLY;
    private final List<WaveProfile> combinedProfiles = new ArrayList<>();

    // Состояние.
    private int wavesSpawned;
    private int secondsToNextWave;
    private int elapsedSeconds;
    private int dronesShotDown;
    private int dronesHitTarget;
    private int dronesSpawned;
    private boolean finished;
    private boolean lost;
    private final Set<UUID> aliveDrones = new LinkedHashSet<>();

    private DroneRaidEvent() {
    }

    public static DroneRaidEvent create(RaidPoint point, RaidSettings settings) {
        DroneRaidEvent event = new DroneRaidEvent();
        event.pointName = point.name;
        event.dimension = point.dimension;
        event.targetX = point.x;
        event.targetY = point.y;
        event.targetZ = point.z;
        event.radius = point.radius;
        event.teamId = point.teamId;
        event.waveCount = settings.waveCount;
        event.dronesPerWave = settings.dronesPerWave;
        event.waveIntervalSeconds = settings.waveIntervalSeconds;
        event.spawnDistance = settings.spawnDistance;
        event.spawnAltitude = settings.spawnAltitude;
        event.speed = (float) settings.speed;
        event.terrainFollow = settings.terrainFollow;
        event.xpPerKill = settings.xpPerKill;
        event.maxDurationSeconds = settings.maxDurationMinutes * 60;
        event.xpLossPerHit = settings.xpLossPerHit;
        event.lossThreshold = settings.lossThreshold;

        // Выбор состава: комбинированный возможен только при наличии профилей волны.
        boolean canCombined = settings.allowCombined
                && settings.combinedProfiles != null
                && !settings.combinedProfiles.isEmpty();
        if (canCombined && ThreadLocalRandom.current().nextBoolean()) {
            event.composition = DroneRaidDefinition.COMPOSITION_COMBINED;
            if (settings.combinedProfiles != null) {
                event.combinedProfiles.addAll(settings.combinedProfiles);
            }
        } else {
            event.composition = DroneRaidDefinition.COMPOSITION_SHAHED_ONLY;
        }
        return event;
    }

    @Override
    public String typeId() {
        return TYPE_ID;
    }

    @Override
    public void onStart(MinecraftServer server) {
        String teamDisplay = teamId.isBlank() ? "" : Teams.displayName(server, teamId);
        PjmNetworking.sendToAll(server, new NotificationPacket(
                Component.translatable("event.pjmbasemod.drone_raid.start.title"),
                Component.translatable("event.pjmbasemod.drone_raid.start.subtitle", pointName, teamDisplay),
                START_COLOR, NOTIFICATION_DURATION_MS));
        Pjmbasemod.LOGGER.info("Events: налёт дронов ({}) на '{}' (команда '{}') начался ({} волн по {} дронов).",
                composition, pointName, teamId, waveCount, dronesPerWave);
    }

    @Override
    public void tick(MinecraftServer server) {
        if (finished) return;
        elapsedSeconds++;

        ServerLevel level = resolveLevel(server);
        if (level == null) {
            Pjmbasemod.LOGGER.warn("Events: измерение '{}' недоступно — налёт '{}' прерван.", dimension, pointName);
            finished = true;
            return;
        }

        // Учёт живых дронов (Shahed сам форсирует чанки, unload не теряем).
        aliveDrones.removeIf(uuid -> {
            Entity entity = level.getEntity(uuid);
            return entity == null || entity.isRemoved();
        });

        if (wavesSpawned < waveCount) {
            secondsToNextWave--;
            if (secondsToNextWave <= 0) {
                spawnWave(level);
                wavesSpawned++;
                secondsToNextWave = waveIntervalSeconds;
            }
        } else if (aliveDrones.isEmpty()) {
            finished = true;
        }

        if (!finished && elapsedSeconds >= maxDurationSeconds) {
            Pjmbasemod.LOGGER.info("Events: налёт '{}' завершён по таймауту ({} дронов ещё в воздухе).",
                    pointName, aliveDrones.size());
            finished = true;
        }
    }

    private void spawnWave(ServerLevel level) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        WaveProfile profile = currentWaveProfile();
        int count = resolve(profile.dronesPerWave, dronesPerWave);
        double waveSpeed = resolve(profile.speed, (double) speed);
        int waveAltitude = resolve(profile.spawnAltitude, spawnAltitude);
        int waveDistance = resolve(profile.spawnDistance, spawnDistance);
        boolean waveTerrain = resolve(profile.terrainFollow, terrainFollow);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double spawnAngle = random.nextDouble() * Math.PI * 2;
            double spawnY = Math.max(waveAltitude, targetY + 60);
            Vec3 spawnPos = new Vec3(
                    targetX + Math.cos(spawnAngle) * waveDistance,
                    spawnY,
                    targetZ + Math.sin(spawnAngle) * waveDistance);

            // Цель — случайная точка внутри зоны налёта.
            double offsetAngle = random.nextDouble() * Math.PI * 2;
            double offsetDist = Math.sqrt(random.nextDouble()) * radius;
            Vec3 target = new Vec3(
                    targetX + Math.cos(offsetAngle) * offsetDist,
                    targetY,
                    targetZ + Math.sin(offsetAngle) * offsetDist);

            // Курс на цель: запуск Shahed идёт по yRot.
            Vec3 toTarget = target.subtract(spawnPos);
            float yRot = (float) Math.toDegrees(Mth.atan2(-toTarget.x, toTarget.z));

            UUID uuid = WrbDronesCompat.spawnShahed(level, spawnPos, yRot, target,
                    (float) waveSpeed, (float) spawnY, waveTerrain, EVENT_DRONE_TAG);
            if (uuid != null) {
                aliveDrones.add(uuid);
                dronesSpawned++;
                spawned++;
            }
        }
        Pjmbasemod.LOGGER.info("Events: налёт '{}' — волна {}/{}, запущено {} дронов (speed={}км/ч, alt={}, dist={}).",
                pointName, wavesSpawned + 1, waveCount, spawned, waveSpeed, waveAltitude, waveDistance);
    }

    /** Профиль текущей волны (для комбинированного налёта) или пустой (для одинаковых волн). */
    private WaveProfile currentWaveProfile() {
        if (!DroneRaidDefinition.COMPOSITION_COMBINED.equals(composition) || combinedProfiles.isEmpty()) {
            return new WaveProfile();
        }
        return combinedProfiles.get(ThreadLocalRandom.current().nextInt(combinedProfiles.size()));
    }

    private static int resolve(@Nullable Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private static double resolve(@Nullable Double value, double fallback) {
        return value != null ? value : fallback;
    }

    private static boolean resolve(@Nullable Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    /** Засчитывает сбитие. @return true — дрон принадлежал событию и ещё не был учтён. */
    public boolean onDroneShotDown(UUID droneId) {
        if (aliveDrones.remove(droneId)) {
            dronesShotDown++;
            return true;
        }
        return false;
    }

    /** Засчитывает попадание дрона в цель (подрыв по терминальной цели). */
    public boolean onDroneImpacted(UUID droneId) {
        if (aliveDrones.remove(droneId)) {
            dronesHitTarget++;
            return true;
        }
        return false;
    }

    public int xpPerKill() {
        return xpPerKill;
    }

    /** Завершить налёт и рассчитать проигрыш/штраф. Вызывается менеджером из onStop. */
    public RaidOutcome evaluateOutcome() {
        if (dronesSpawned <= 0) {
            return new RaidOutcome(false, 0, 0, 0);
        }
        boolean lost = (double) dronesHitTarget / dronesSpawned > lossThreshold;
        return new RaidOutcome(lost, dronesSpawned, dronesHitTarget, dronesShotDown);
    }

    /** Применить штраф XP атакуемой команде при проигрыше. Вызывается из ServerEventManager.onStop. */
    public void applyLossPenalty(MinecraftServer server) {
        if (!teamId.isBlank() && Teams.isCombatTeam(teamId) && xpLossPerHit > 0 && dronesHitTarget > 0) {
            int totalLoss = xpLossPerHit * dronesHitTarget;
            RankService.penalizeTeam(server, teamId, totalLoss, "raid_loss");
            Pjmbasemod.LOGGER.info("Events: команда '{}' потеряла {} XP за проигранный налёт '{}' ({} попаданий).",
                    teamId, totalLoss, pointName, dronesHitTarget);
        }
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void onStop(MinecraftServer server, boolean aborted) {
        // Оставшиеся событийные дроны убираем — иначе после остановки они бесхозно кружат.
        ServerLevel level = resolveLevel(server);
        if (level != null) {
            for (UUID uuid : aliveDrones) {
                Entity entity = level.getEntity(uuid);
                if (entity != null && !entity.isRemoved()) {
                    entity.discard();
                }
            }
        }
        aliveDrones.clear();

        RaidOutcome outcome = evaluateOutcome();
        lost = !aborted && outcome.lost();
        if (lost) {
            applyLossPenalty(server);
        }

        Component subtitle;
        int color;
        if (aborted) {
            subtitle = Component.translatable("event.pjmbasemod.drone_raid.end.aborted.subtitle", dronesShotDown);
            color = START_COLOR;
        } else if (lost) {
            subtitle = Component.translatable("event.pjmbasemod.drone_raid.end.lost.subtitle",
                    dronesHitTarget, dronesSpawned);
            color = LOSS_COLOR;
        } else {
            subtitle = Component.translatable("event.pjmbasemod.drone_raid.end.subtitle", dronesShotDown);
            color = END_COLOR;
        }
        PjmNetworking.sendToAll(server, new NotificationPacket(
                Component.translatable("event.pjmbasemod.drone_raid.end.title"),
                subtitle, color, NOTIFICATION_DURATION_MS));
        Pjmbasemod.LOGGER.info("Events: налёт '{}' окончен (aborted={}, сбито {}, попало {}, всего {}).",
                pointName, aborted, dronesShotDown, dronesHitTarget, dronesSpawned);
    }

    @Override
    public String statusLine() {
        return String.format("налёт дронов '%s' [%s]: волна %d/%d, в воздухе %d, сбито %d, попало %d/%d, прошло %d сек",
                pointName, composition, wavesSpawned, waveCount, aliveDrones.size(),
                dronesShotDown, dronesHitTarget, dronesSpawned, elapsedSeconds);
    }

    @Override
    public EventZone zone() {
        return new EventZone(pointName, dimension, (int) targetX, (int) targetY, (int) targetZ, radius);
    }

    @Nullable
    private ServerLevel resolveLevel(MinecraftServer server) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) return null;
        return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("PointName", pointName);
        tag.putString("Dimension", dimension);
        tag.putDouble("TargetX", targetX);
        tag.putDouble("TargetY", targetY);
        tag.putDouble("TargetZ", targetZ);
        tag.putInt("Radius", radius);
        tag.putString("TeamId", teamId);
        tag.putInt("WaveCount", waveCount);
        tag.putInt("DronesPerWave", dronesPerWave);
        tag.putInt("WaveIntervalSeconds", waveIntervalSeconds);
        tag.putInt("SpawnDistance", spawnDistance);
        tag.putInt("SpawnAltitude", spawnAltitude);
        tag.putFloat("Speed", speed);
        tag.putBoolean("TerrainFollow", terrainFollow);
        tag.putInt("XpPerKill", xpPerKill);
        tag.putInt("MaxDurationSeconds", maxDurationSeconds);
        tag.putInt("XpLossPerHit", xpLossPerHit);
        tag.putDouble("LossThreshold", lossThreshold);
        tag.putString("Composition", composition);
        saveProfiles(tag);
        tag.putInt("WavesSpawned", wavesSpawned);
        tag.putInt("SecondsToNextWave", secondsToNextWave);
        tag.putInt("ElapsedSeconds", elapsedSeconds);
        tag.putInt("DronesShotDown", dronesShotDown);
        tag.putInt("DronesHitTarget", dronesHitTarget);
        tag.putInt("DronesSpawned", dronesSpawned);
        ListTag drones = new ListTag();
        for (UUID uuid : aliveDrones) {
            drones.add(NbtUtils.createUUID(uuid));
        }
        tag.put("AliveDrones", drones);
        return tag;
    }

    private void saveProfiles(CompoundTag tag) {
        ListTag list = new ListTag();
        for (WaveProfile profile : combinedProfiles) {
            CompoundTag p = new CompoundTag();
            if (profile.dronesPerWave != null) p.putInt("DronesPerWave", profile.dronesPerWave);
            if (profile.speed != null) p.putDouble("Speed", profile.speed);
            if (profile.spawnAltitude != null) p.putInt("SpawnAltitude", profile.spawnAltitude);
            if (profile.spawnDistance != null) p.putInt("SpawnDistance", profile.spawnDistance);
            if (profile.terrainFollow != null) p.putBoolean("TerrainFollow", profile.terrainFollow);
            list.add(p);
        }
        tag.put("CombinedProfiles", list);
    }

    public static DroneRaidEvent load(CompoundTag tag) {
        DroneRaidEvent event = new DroneRaidEvent();
        event.pointName = tag.getString("PointName");
        event.dimension = tag.getString("Dimension");
        event.targetX = tag.getDouble("TargetX");
        event.targetY = tag.getDouble("TargetY");
        event.targetZ = tag.getDouble("TargetZ");
        event.radius = tag.getInt("Radius");
        event.teamId = tag.getString("TeamId");
        event.waveCount = tag.getInt("WaveCount");
        event.dronesPerWave = tag.getInt("DronesPerWave");
        event.waveIntervalSeconds = tag.getInt("WaveIntervalSeconds");
        event.spawnDistance = tag.getInt("SpawnDistance");
        event.spawnAltitude = tag.getInt("SpawnAltitude");
        event.speed = tag.getFloat("Speed");
        event.terrainFollow = tag.getBoolean("TerrainFollow");
        event.xpPerKill = tag.getInt("XpPerKill");
        event.maxDurationSeconds = tag.getInt("MaxDurationSeconds");
        event.xpLossPerHit = tag.getInt("XpLossPerHit");
        event.lossThreshold = tag.getDouble("LossThreshold");
        event.composition = tag.getString("Composition");
        if (event.composition.isBlank()) {
            event.composition = DroneRaidDefinition.COMPOSITION_SHAHED_ONLY;
        }
        loadProfiles(tag, event);
        event.wavesSpawned = tag.getInt("WavesSpawned");
        event.secondsToNextWave = tag.getInt("SecondsToNextWave");
        event.elapsedSeconds = tag.getInt("ElapsedSeconds");
        event.dronesShotDown = tag.getInt("DronesShotDown");
        event.dronesHitTarget = tag.getInt("DronesHitTarget");
        event.dronesSpawned = tag.getInt("DronesSpawned");
        ListTag drones = tag.getList("AliveDrones", Tag.TAG_INT_ARRAY);
        for (Tag droneTag : drones) {
            event.aliveDrones.add(NbtUtils.loadUUID(droneTag));
        }
        return event;
    }

    private static void loadProfiles(CompoundTag tag, DroneRaidEvent event) {
        ListTag list = tag.getList("CombinedProfiles", Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag p)) continue;
            WaveProfile profile = new WaveProfile();
            if (p.contains("DronesPerWave")) profile.dronesPerWave = p.getInt("DronesPerWave");
            if (p.contains("Speed")) profile.speed = p.getDouble("Speed");
            if (p.contains("SpawnAltitude")) profile.spawnAltitude = p.getInt("SpawnAltitude");
            if (p.contains("SpawnDistance")) profile.spawnDistance = p.getInt("SpawnDistance");
            if (p.contains("TerrainFollow")) profile.terrainFollow = p.getBoolean("TerrainFollow");
            event.combinedProfiles.add(profile);
        }
    }

    /** Итог налёта: проигран ли, сколько дронов выпущено/попало/сбито. */
    public record RaidOutcome(boolean lost, int dronesSpawned, int dronesHitTarget, int dronesShotDown) {}
}
