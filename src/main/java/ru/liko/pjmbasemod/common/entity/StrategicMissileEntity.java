package ru.liko.pjmbasemod.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import ru.liko.pjmbasemod.common.compat.SbwMissileCompat;
import ru.liko.pjmbasemod.common.missile.BallisticTrajectory;
import ru.liko.pjmbasemod.common.missile.MissileDefinition;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import net.minecraft.network.chat.Component;
import ru.liko.pjmbasemod.common.network.packet.MissileAlertPacket;
import ru.liko.pjmbasemod.common.network.packet.MissileAudioSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.MissileImpactPacket;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;
import ru.liko.pjmbasemod.common.teams.Teams;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.UUID;

/** Одна физическая GeckoLib-сущность для всех JSON-профилей стратегических ракет. */
public final class StrategicMissileEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<String> MISSILE_ID = SynchedEntityData.defineId(
            StrategicMissileEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> BALLISTIC = SynchedEntityData.defineId(
            StrategicMissileEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int[] LOOKAHEAD_TICKS = {10, 20};
    private static final double MAX_CLIMB_PER_TICK = 2.5;
    private static final double MAX_DESCENT_PER_TICK = 1.5;
    private static final double MAX_VERTICAL_ACCEL = 0.15;

    private static final int TICKET_RADIUS = 3;
    private static final int TICKET_LINGER_TICKS = 100;
    private static final TicketType<UUID> FLIGHT_TICKET = TicketType.create(
            "pjmbasemod_strategic_missile", Comparator.<UUID>naturalOrder(), TICKET_LINGER_TICKS);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private boolean configured;
    private String teamId = "";
    @Nullable private UUID commanderId;
    private double startX, startY, startZ;
    private double targetX, targetY, targetZ;
    private int flightTicks = 200;
    private int elapsedTicks;
    private int cruiseHeight = 24;
    private int terminalDiveDistance = 36;
    private int ballisticApex = 160;
    private float weaveAmplitude;
    private float weaveCycles = 1.0f;
    private float explosionDamage = 120.0f;
    private float explosionRadius = 8.0f;
    private float health = 35.0f;
    private float shotDownPower = 0.35f;
    private boolean destroyBlocks;
    private boolean exploding;
    private boolean enemyAlertSent;
    private double smoothedCruiseY = Double.NaN;
    private double cruiseClimbRate;
    @Nullable private ChunkPos ticketCenter;

    // Клиентская интерполяция: плавный 3-шаговый лерп в tick(); если тик сущности
    // заморожен (EntityCulling скипает невидимых), пакет применяет позицию мгновенно.
    private static final int LERP_STEPS = 3;
    private double lerpX, lerpY, lerpZ;
    private float lerpYRot, lerpXRot;
    private int lerpSteps;
    private long lastClientTickTime = Long.MIN_VALUE;

    public StrategicMissileEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    public void configure(MissileDefinition definition, String teamId, UUID commanderId,
                          Vec3 start, Vec3 target) {
        this.entityData.set(MISSILE_ID, definition.id());
        this.entityData.set(BALLISTIC, definition.trajectoryType() == MissileDefinition.Trajectory.BALLISTIC);
        this.teamId = teamId;
        this.commanderId = commanderId;
        this.startX = start.x;
        this.startY = start.y;
        this.startZ = start.z;
        this.targetX = target.x;
        this.targetY = target.y;
        this.targetZ = target.z;
        this.flightTicks = Math.max(40, definition.flightSeconds() * 20);
        this.cruiseHeight = definition.cruiseHeight();
        this.terminalDiveDistance = definition.terminalDiveDistance();
        this.ballisticApex = definition.ballisticApex();
        this.weaveAmplitude = definition.weaveAmplitude();
        this.weaveCycles = definition.weaveCycles();
        this.explosionDamage = definition.damage();
        this.explosionRadius = definition.radius();
        this.health = definition.hitPoints();
        this.shotDownPower = definition.shotDownPower();
        this.destroyBlocks = definition.destroyBlocks();
        this.configured = true;
        setPos(start);
        // Ticket сразу при конфигурации: чанк спавна за тысячи блоков не entity-ticking,
        // и без него первый tick() никогда не случится — ракета зависнет навечно.
        if (level() instanceof ServerLevel serverLevel) refreshChunkTicket(serverLevel);
    }

    public String getMissileId() { return entityData.get(MISSILE_ID); }
    public boolean isBallistic() { return entityData.get(BALLISTIC); }
    public String getTeamId() { return teamId; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MISSILE_ID, "missile");
        builder.define(BALLISTIC, false);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = LERP_STEPS;
        // Тик не идёт (сущность закаллена или только появилась) — применяем сразу,
        // иначе позиция замёрзнет и модель никогда не покажется.
        if (level().getGameTime() - lastClientTickTime > 2) {
            this.lerpSteps = 0;
            setPos(x, y, z);
            setRot(yRot, xRot);
        }
    }

    private void tickLerp() {
        lastClientTickTime = level().getGameTime();
        if (lerpSteps <= 0) return;
        double nx = getX() + (lerpX - getX()) / lerpSteps;
        double ny = getY() + (lerpY - getY()) / lerpSteps;
        double nz = getZ() + (lerpZ - getZ()) / lerpSteps;
        float yaw = Mth.rotLerp(1.0f / lerpSteps, getYRot(), lerpYRot);
        float pitch = Mth.lerp(1.0f / lerpSteps, getXRot(), lerpXRot);
        lerpSteps--;
        setPos(nx, ny, nz);
        setRot(yaw, pitch);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            tickLerp();
            spawnClientTrail();
            return;
        }
        if (!configured || exploding) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;

        refreshChunkTicket(serverLevel);
        if (++elapsedTicks > flightTicks + 40) {
            detonate(false, new Vec3(targetX, targetY, targetZ));
            return;
        }

        double t = Mth.clamp(elapsedTicks / (double) flightTicks, 0.0, 1.0);
        Vec3 next = isBallistic() ? ballisticPosition(t) : cruisePosition(serverLevel, t);
        Vec3 previous = position();
        BlockHitResult blockHit = serverLevel.clip(new ClipContext(
                previous, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : next;
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(serverLevel, this, previous, end,
                getBoundingBox().expandTowards(end.subtract(previous)).inflate(1.0),
                other -> other != this && other.isAlive() && other.isPickable() && !other.isSpectator());
        if (entityHit != null) {
            setPos(entityHit.getLocation());
            detonate(false, entityHit.getLocation());
            return;
        }
        if (blockHit.getType() != HitResult.Type.MISS) {
            setPos(end);
            detonate(false, end);
            return;
        }

        Vec3 movement = next.subtract(previous);
        setDeltaMovement(movement);
        updateRotation(movement);
        setPos(next);
        spawnTrail(serverLevel, next, movement);
        if (tickCount % 2 == 0) sendAudioSync(serverLevel, true);
        if (!enemyAlertSent && elapsedTicks >= ENEMY_ALERT_DELAY_TICKS) {
            enemyAlertSent = true;
            sendEnemyAlert(serverLevel);
        }
        if (t >= 1.0 || next.distanceToSqr(targetX, targetY, targetZ) <= 1.0) {
            detonate(false, new Vec3(targetX, targetY, targetZ));
        }
    }

    private static final double TRAIL_VIEW_DISTANCE_SQ = 400.0 * 400.0;
    /** Ближе этого след рисует сам клиент от отрисованной модели (совпадает идеально). */
    private static final double TRAIL_CLIENT_HANDLED_SQ = 192.0 * 192.0;

    /**
     * Дальний след для тех, кто вне зоны трекинга сущности (модель им всё равно не видна):
     * по нему ракету можно заметить издалека. Ближним след рисует клиент в {@link #spawnClientTrail}.
     */
    private void spawnTrail(ServerLevel level, Vec3 position, Vec3 movement) {
        if (movement.lengthSqr() < 1.0E-6) return;
        Vec3 tail = position.subtract(movement.normalize().scale(2.0));
        for (ServerPlayer viewer : level.players()) {
            double distSq = viewer.distanceToSqr(tail.x, tail.y, tail.z);
            if (distSq > TRAIL_VIEW_DISTANCE_SQ || distSq < TRAIL_CLIENT_HANDLED_SQ) continue;
            level.sendParticles(viewer, ParticleTypes.FLAME, true, tail.x, tail.y, tail.z, 2, 0.1, 0.1, 0.1, 0.01);
            level.sendParticles(viewer, ParticleTypes.LARGE_SMOKE, true, tail.x, tail.y, tail.z, 3, 0.3, 0.3, 0.3, 0.02);
            if (tickCount % 2 == 0) {
                level.sendParticles(viewer, ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                        tail.x, tail.y, tail.z, 1, 0.2, 0.2, 0.2, 0.005);
            }
        }
    }

    /** Клиентский след от отрисованной позиции модели — партиклы идеально совпадают с ракетой. */
    private void spawnClientTrail() {
        Vec3 dir = Vec3.directionFromRotation(getXRot(), getYRot());
        Vec3 tail = position().subtract(dir.scale(2.5));
        var random = level().random;
        for (int i = 0; i < 2; i++) {
            level().addParticle(ParticleTypes.FLAME,
                    tail.x + random.nextGaussian() * 0.1, tail.y + random.nextGaussian() * 0.1,
                    tail.z + random.nextGaussian() * 0.1, 0.0, 0.0, 0.0);
            level().addParticle(ParticleTypes.LARGE_SMOKE,
                    tail.x + random.nextGaussian() * 0.25, tail.y + random.nextGaussian() * 0.25,
                    tail.z + random.nextGaussian() * 0.25, 0.0, 0.0, 0.0);
        }
        if (tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    tail.x, tail.y, tail.z, 0.0, 0.0, 0.0);
        }
    }

    private Vec3 ballisticPosition(double t) {
        double x = Mth.lerp(t, startX, targetX);
        double z = Mth.lerp(t, startZ, targetZ);
        double horizontalDistance = Math.hypot(targetX - startX, targetZ - startZ);
        double y = BallisticTrajectory.altitudeAt(
                t, startY, targetY, horizontalDistance, ballisticApex);
        return new Vec3(x, y, z);
    }

    private Vec3 cruisePosition(ServerLevel level, double t) {
        double x = Mth.lerp(t, startX, targetX);
        double z = Mth.lerp(t, startZ, targetZ);
        double routeX = targetX - startX;
        double routeZ = targetZ - startZ;
        double routeLength = Math.hypot(routeX, routeZ);
        if (routeLength > 1.0E-6 && weaveAmplitude > 0.0f) {
            double offset = weaveAmplitude
                    * Math.sin(Math.PI * 2.0 * weaveCycles * t)
                    * Math.sin(Math.PI * t);
            x += -routeZ / routeLength * offset;
            z += routeX / routeLength * offset;
        }
        double horizontalRemaining = Math.hypot(targetX - x, targetZ - z);
        double cruiseTarget = Math.min(level.getMaxBuildHeight() - 8.0,
                lookAheadTerrain(level, x, z, routeLength) + cruiseHeight);
        // Крейсерская высота — сглаживание второго порядка: плавно меняется не только
        // высота, но и вертикальная скорость (лимит ускорения), без изломов траектории.
        // Лимиты масштабируются от скорости: угол набора ~26°/снижения ~19° постоянен.
        double speed = routeLength / Math.max(1, flightTicks);
        double maxClimb = Math.max(MAX_CLIMB_PER_TICK, speed * 0.5);
        double maxDescent = Math.max(MAX_DESCENT_PER_TICK, speed * 0.35);
        double maxAccel = Math.max(MAX_VERTICAL_ACCEL, speed * 0.06);
        if (Double.isNaN(smoothedCruiseY)) smoothedCruiseY = Math.max(getY(), cruiseTarget);
        double desiredRate = Mth.clamp((cruiseTarget - smoothedCruiseY) * 0.12, -maxDescent, maxClimb);
        cruiseClimbRate += Mth.clamp(desiredRate - cruiseClimbRate, -maxAccel, maxAccel);
        smoothedCruiseY += cruiseClimbRate;
        // Терминальный заход — плавный перелом курса вниз (dive², без прыжка вверх).
        double dive = Mth.clamp(1.0 - horizontalRemaining / Math.max(1.0, terminalDiveDistance), 0.0, 1.0);
        double y = Mth.lerp(dive * dive, smoothedCruiseY, targetY);
        return new Vec3(x, y, z);
    }

    /**
     * Максимальный рельеф в текущей точке и с упреждением по курсу (10 и 20 тиков пути):
     * ракета начинает набор высоты до склона, а не утыкается в него.
     */
    private int lookAheadTerrain(ServerLevel level, double x, double z, double routeLength) {
        int terrain = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        double dx = targetX - x;
        double dz = targetZ - z;
        double remaining = Math.hypot(dx, dz);
        if (remaining < 1.0E-3) return terrain;
        double speedPerTick = routeLength / Math.max(1, flightTicks);
        for (int ticksAhead : LOOKAHEAD_TICKS) {
            double distance = Math.min(remaining, speedPerTick * ticksAhead);
            int ahead = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(x + dx / remaining * distance), Mth.floor(z + dz / remaining * distance));
            terrain = Math.max(terrain, ahead);
        }
        return terrain;
    }

    private void updateRotation(Vec3 movement) {
        if (movement.lengthSqr() < 1.0E-6) return;
        double horizontal = Math.hypot(movement.x, movement.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-movement.x, movement.z));
        float pitch = (float) Math.toDegrees(Math.atan2(-movement.y, horizontal));
        setYRot(yaw);
        setXRot(pitch);
        yRotO = yaw;
        xRotO = pitch;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (isInvulnerableTo(source) || level().isClientSide() || exploding || amount <= 0) return false;
        health -= amount;
        if (health <= 0) detonate(true, position());
        return true;
    }

    private void detonate(boolean shotDown, Vec3 impact) {
        if (level().isClientSide() || exploding || isRemoved()) return;
        exploding = true;
        float power = shotDown ? shotDownPower : 1.0f;
        if (power > 0.0f) {
            Entity attacker = null;
            if (commanderId != null && level() instanceof ServerLevel serverLevel) {
                ServerPlayer commander = serverLevel.getServer().getPlayerList().getPlayer(commanderId);
                attacker = commander;
            }
            float radius = explosionRadius * (float) Math.sqrt(power);
            SbwMissileCompat.detonate(this, attacker, impact,
                    explosionDamage * power, radius,
                    destroyBlocks && !shotDown);
            if (level() instanceof ServerLevel serverLevel) {
                // Отметка поражения на карте — всем игрокам измерения.
                MissileImpactPacket packet = new MissileImpactPacket(
                        serverLevel.dimension().location().toString(),
                        impact.x, impact.z, radius, shotDown);
                for (ServerPlayer viewer : serverLevel.players()) {
                    PjmNetworking.sendToPlayer(viewer, packet);
                }
            }
        }
        discard();
    }

    /** Задержка алерта чужим командам после пуска (~8.5 с). */
    private static final int ENEMY_ALERT_DELAY_TICKS = 170;
    private static final double ENEMY_WARNING_DISTANCE_SQ = 256.0 * 256.0;

    /**
     * Отложенное предупреждение всем НЕ-сокомандникам: зона поражения на карте (без названия
     * ракеты) + эвакуационное уведомление тем, кто рядом с целью. Своя команда получила
     * алерт мгновенно при пуске из MissileStrikeManager.
     */
    private void sendEnemyAlert(ServerLevel level) {
        MissileAlertPacket alert = new MissileAlertPacket(
                level.dimension().location().toString(), targetX, targetZ,
                explosionRadius, "", false);
        for (ServerPlayer viewer : level.getServer().getPlayerList().getPlayers()) {
            if (teamId.equals(Teams.resolvePlayerTeamId(viewer))) continue;
            PjmNetworking.sendToPlayer(viewer, alert);
            if (viewer.serverLevel() == level
                    && viewer.distanceToSqr(targetX, targetY, targetZ) <= ENEMY_WARNING_DISTANCE_SQ) {
                PjmNetworking.sendToPlayer(viewer, new NotificationPacket(
                        Component.translatable("gui.pjmbasemod.missile.warning.title"),
                        Component.translatable("gui.pjmbasemod.missile.warning.subtitle"),
                        0xD6453D, 6000L));
            }
        }
    }

    private static final double AUDIO_RANGE_SQ = 750.0 * 750.0;

    /** Звуковой синк идёт мимо entity-трекинга: ракету слышно и там, где сущность не прогружена. */
    private void sendAudioSync(ServerLevel level, boolean active) {
        MissileAudioSyncPacket packet = new MissileAudioSyncPacket(
                getUUID(), isBallistic(), active, getX(), getY(), getZ());
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(getX(), getY(), getZ()) <= AUDIO_RANGE_SQ) {
                PjmNetworking.sendToPlayer(viewer, packet);
            }
        }
    }

    private void refreshChunkTicket(ServerLevel level) {
        ChunkPos current = chunkPosition();
        if (ticketCenter != null && ticketCenter.equals(current) && tickCount % 20 != 0) return;
        level.getChunkSource().addRegionTicket(FLIGHT_TICKET, current, TICKET_RADIUS, getUUID());
        // Упреждающий ticket по курсу (~15 тиков пути): на высокой скорости чанки и heightmap
        // для террейн-фоллоу успевают грузиться асинхронно, без sync-load лагов.
        Vec3 velocity = getDeltaMovement();
        if (velocity.horizontalDistanceSqr() > 1.0) {
            ChunkPos ahead = new ChunkPos(BlockPos.containing(position().add(velocity.scale(15.0))));
            if (!ahead.equals(current)) {
                level.getChunkSource().addRegionTicket(FLIGHT_TICKET, ahead, TICKET_RADIUS, getUUID());
            }
        }
        ticketCenter = current;
    }

    private void removeChunkTicket() {
        if (ticketCenter == null || !(level() instanceof ServerLevel serverLevel)) return;
        serverLevel.getChunkSource().removeRegionTicket(
                FLIGHT_TICKET, ticketCenter, TICKET_RADIUS, getUUID());
        ticketCenter = null;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && level() instanceof ServerLevel serverLevel) {
            sendAudioSync(serverLevel, false);
        }
        removeChunkTicket();
        super.remove(reason);
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(MISSILE_ID, tag.getString("MissileId"));
        entityData.set(BALLISTIC, tag.getBoolean("Ballistic"));
        configured = tag.getBoolean("Configured");
        teamId = tag.getString("TeamId");
        commanderId = tag.hasUUID("Commander") ? tag.getUUID("Commander") : null;
        startX = tag.getDouble("StartX");
        startY = tag.getDouble("StartY");
        startZ = tag.getDouble("StartZ");
        targetX = tag.getDouble("TargetX");
        targetY = tag.getDouble("TargetY");
        targetZ = tag.getDouble("TargetZ");
        flightTicks = Math.max(40, tag.getInt("FlightTicks"));
        elapsedTicks = Math.max(0, tag.getInt("ElapsedTicks"));
        cruiseHeight = tag.getInt("CruiseHeight");
        terminalDiveDistance = tag.getInt("TerminalDiveDistance");
        ballisticApex = tag.getInt("BallisticApex");
        weaveAmplitude = tag.getFloat("WeaveAmplitude");
        weaveCycles = tag.contains("WeaveCycles") ? tag.getFloat("WeaveCycles") : 1.0f;
        explosionDamage = tag.getFloat("ExplosionDamage");
        explosionRadius = tag.getFloat("ExplosionRadius");
        health = tag.getFloat("Health");
        shotDownPower = tag.getFloat("ShotDownPower");
        destroyBlocks = tag.getBoolean("DestroyBlocks");
        enemyAlertSent = tag.getBoolean("EnemyAlertSent");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("MissileId", getMissileId());
        tag.putBoolean("Ballistic", isBallistic());
        tag.putBoolean("Configured", configured);
        tag.putString("TeamId", teamId);
        if (commanderId != null) tag.putUUID("Commander", commanderId);
        tag.putDouble("StartX", startX);
        tag.putDouble("StartY", startY);
        tag.putDouble("StartZ", startZ);
        tag.putDouble("TargetX", targetX);
        tag.putDouble("TargetY", targetY);
        tag.putDouble("TargetZ", targetZ);
        tag.putInt("FlightTicks", flightTicks);
        tag.putInt("ElapsedTicks", elapsedTicks);
        tag.putInt("CruiseHeight", cruiseHeight);
        tag.putInt("TerminalDiveDistance", terminalDiveDistance);
        tag.putInt("BallisticApex", ballisticApex);
        tag.putFloat("WeaveAmplitude", weaveAmplitude);
        tag.putFloat("WeaveCycles", weaveCycles);
        tag.putFloat("ExplosionDamage", explosionDamage);
        tag.putFloat("ExplosionRadius", explosionRadius);
        tag.putFloat("Health", health);
        tag.putFloat("ShotDownPower", shotDownPower);
        tag.putBoolean("DestroyBlocks", destroyBlocks);
        tag.putBoolean("EnemyAlertSent", enemyAlertSent);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Полёт задаётся трансформацией сущности; обязательной анимации у модели нет.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
