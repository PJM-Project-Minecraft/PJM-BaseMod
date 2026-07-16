package ru.liko.pjmbasemod.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ru.liko.pjmbasemod.common.garage.GarageManager;
import ru.liko.pjmbasemod.common.garage.GarageType;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Размещаемый терминал-«ноутбук». ПКМ открывает GUI гаража интерактирующего игрока.
 * Сущность неподвижна, рендерится через GeckoLib.
 */
public class NotebookEntity extends Entity implements GeoEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.notebook.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private UUID ownerId;

    /** Якорная позиция: куда сущность возвращается, если её сдвинули (взрыв, wind charge, крючок). */
    @Nullable
    private Vec3 anchor;

    /** Тип гаража, который открывает этот терминал (наземка/авиация). */
    private GarageType garageType = GarageType.GROUND;

    public NotebookEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        this.blocksBuilding = true;
    }

    public void setOwner(@Nullable UUID ownerId) {
        this.ownerId = ownerId;
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    public void setGarageType(GarageType garageType) {
        this.garageType = garageType == null ? GarageType.GROUND : garageType;
    }

    public GarageType getGarageType() {
        return garageType == null ? GarageType.GROUND : garageType;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // нет синхронизируемых полей
    }

    @Override
    public void tick() {
        // Первый тик фиксирует якорь (позиция размещения/загрузки). Далее любой сдвиг
        // (взрыв, wind charge, крючок, поршень) откатывается — иначе "заморозка" на месте
        // просто закрепляла бы сущность там, куда её толкнули.
        if (anchor == null) anchor = position();
        this.setDeltaMovement(0, 0, 0);
        if (getX() != anchor.x || getY() != anchor.y || getZ() != anchor.z) {
            this.setPos(anchor.x, anchor.y, anchor.z);
        }
        super.tick();
    }

    @Override
    public void remove(RemovalReason reason) {
        // Терминал нельзя снести ванильным /kill (он вызывает kill() → remove(KILLED)).
        // Убрать его можно только командой «/pjm entity remove», которая идёт через
        // discard() → RemovalReason.DISCARDED.
        if (reason == RemovalReason.KILLED) {
            return;
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        if (tag.contains("FacingYaw")) {
            float yaw = tag.getFloat("FacingYaw");
            this.setYRot(yaw);
            this.yRotO = yaw;
        }
        this.garageType = GarageType.fromString(tag.getString("GarageType"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putFloat("FacingYaw", this.getYRot());
        tag.putString("GarageType", getGarageType().id());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            GarageManager.openGarage(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
