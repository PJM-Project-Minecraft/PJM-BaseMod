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
import ru.liko.pjmbasemod.common.garage.GarageManager;
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

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // нет синхронизируемых полей
    }

    @Override
    public void tick() {
        // Замораживаем сущность: не даем физике/гравитации двигать или вращать ноутбук
        this.setDeltaMovement(0, 0, 0);
        // Фиксируем позицию (пересчитываем boundingBox на случай если что-то сдвинуло)
        this.setPos(this.getX(), this.getY(), this.getZ());
        super.tick();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        if (tag.contains("FacingYaw")) {
            float yaw = tag.getFloat("FacingYaw");
            this.setYRot(yaw);
            this.yRotO = yaw;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putFloat("FacingYaw", this.getYRot());
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
