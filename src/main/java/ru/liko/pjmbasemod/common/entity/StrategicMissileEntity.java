package ru.liko.pjmbasemod.common.entity;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import ru.liko.pjmbasemod.common.compat.SbwMissileCompat;
import ru.liko.pjmbasemod.common.missile.MissileDefinition;
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
    private float terminalPopUp;
    private float explosionDamage = 120.0f;
    private float explosionRadius = 8.0f;
    private float health = 35.0f;
    private float shotDownPower = 0.35f;
    private boolean destroyBlocks;
    private boolean exploding;
    @Nullable private ChunkPos ticketCenter;

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
        this.terminalPopUp = definition.terminalPopUp();
        this.explosionDamage = definition.damage();
        this.explosionRadius = definition.radius();
        this.health = definition.hitPoints();
        this.shotDownPower = definition.shotDownPower();
        this.destroyBlocks = definition.destroyBlocks();
        this.configured = true;
        setPos(start);
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
    public void tick() {
        super.tick();
        if (level().isClientSide() || !configured || exploding) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;

        refreshChunkTicket(serverLevel);
        if (++elapsedTicks > flightTicks + 40) {
            detonate(false, new Vec3(targetX, targetY, targetZ));
            return;
        }

        double t = Mth.clamp(elapsedTicks / (double) flightTicks, 0.0, 1.0);
        Vec3 next = isBallistic() ? ballisticPosition(t) : cruisePosition(serverLevel, t);
        Vec3 previous = position();
        BlockHitResult hit = serverLevel.clip(new ClipContext(
                previous, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            setPos(hit.getLocation());
            detonate(false, hit.getLocation());
            return;
        }

        Vec3 movement = next.subtract(previous);
        setDeltaMovement(movement);
        updateRotation(movement);
        setPos(next);
        if (t >= 1.0 || next.distanceToSqr(targetX, targetY, targetZ) <= 1.0) {
            detonate(false, new Vec3(targetX, targetY, targetZ));
        }
    }

    private Vec3 ballisticPosition(double t) {
        double x = Mth.lerp(t, startX, targetX);
        double z = Mth.lerp(t, startZ, targetZ);
        double baseline = Mth.lerp(t, startY, targetY);
        double y = baseline + 4.0 * ballisticApex * t * (1.0 - t);
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
        int terrain = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(x), Mth.floor(z));
        double cruiseY = Math.min(level.getMaxBuildHeight() - 8.0, terrain + cruiseHeight);
        double dive = Mth.clamp(1.0 - horizontalRemaining / Math.max(1.0, terminalDiveDistance), 0.0, 1.0);
        double y = Mth.lerp(dive * dive, cruiseY, targetY)
                + terminalPopUp * Math.sin(Math.PI * dive);
        return new Vec3(x, y, z);
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
            SbwMissileCompat.detonate(this, attacker, impact,
                    explosionDamage * power,
                    explosionRadius * (float) Math.sqrt(power),
                    destroyBlocks && !shotDown);
        }
        discard();
    }

    private void refreshChunkTicket(ServerLevel level) {
        ChunkPos current = chunkPosition();
        if (ticketCenter != null && ticketCenter.equals(current) && tickCount % 40 != 0) return;
        level.getChunkSource().addRegionTicket(FLIGHT_TICKET, current, TICKET_RADIUS, getUUID());
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
        terminalPopUp = tag.getFloat("TerminalPopUp");
        explosionDamage = tag.getFloat("ExplosionDamage");
        explosionRadius = tag.getFloat("ExplosionRadius");
        health = tag.getFloat("Health");
        shotDownPower = tag.getFloat("ShotDownPower");
        destroyBlocks = tag.getBoolean("DestroyBlocks");
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
        tag.putFloat("TerminalPopUp", terminalPopUp);
        tag.putFloat("ExplosionDamage", explosionDamage);
        tag.putFloat("ExplosionRadius", explosionRadius);
        tag.putFloat("Health", health);
        tag.putFloat("ShotDownPower", shotDownPower);
        tag.putBoolean("DestroyBlocks", destroyBlocks);
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
