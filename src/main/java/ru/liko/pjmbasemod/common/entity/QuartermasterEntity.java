package ru.liko.pjmbasemod.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ru.liko.pjmbasemod.common.item.SupplyCrateItem;
import ru.liko.pjmbasemod.common.warehouse.WarehouseManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * NPC-кладовщик. Гуманоид, рендерится ванильной player-моделью со скином из
 * {@code textures/skins/}. Хранит привязку к именованному складу и настройки доступа/выдачи.
 * ПКМ ящиком — сдача поставки, ПКМ без ящика — открытие GUI склада.
 *
 * <p>ИИ ограничен «живым» idle на месте: смотрит на ближайших игроков и оглядывается.
 * Скорость передвижения 0 и отсутствие целей на ходьбу гарантируют, что NPC не сходит с поста.
 */
public class QuartermasterEntity extends Mob {

    public static final String DEFAULT_SKIN = "skin_emr";

    private static final EntityDataAccessor<String> SKIN_ID =
            SynchedEntityData.defineId(QuartermasterEntity.class, EntityDataSerializers.STRING);

    private String warehouseId = "";
    /** id команды (scoreboard), которой доступен NPC; пусто — доступен всем. */
    private String teamRestriction = "";
    /** Разрешённые display-категории; пусто — все. */
    private final List<String> allowedCategories = new ArrayList<>();
    /** Лимит выдачи за раз (0 — использовать лимит из определения предмета). */
    private int withdrawLimit = 0;
    /** Задержка между выдачами в тиках. */
    private int cooldownTicks = 0;

    public QuartermasterEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setInvulnerable(true);
        this.setSilent(true);
    }

    @Override
    protected void registerGoals() {
        // Только «живой» idle на месте: целей на ходьбу нет, NPC не сходит с поста.
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_ID, DEFAULT_SKIN);
    }

    // ---------------------------------------------------------------- настройки

    public String getSkinId() {
        return this.entityData.get(SKIN_ID);
    }

    public void setSkinId(String skinId) {
        this.entityData.set(SKIN_ID, sanitizeSkin(skinId));
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId == null ? "" : warehouseId;
    }

    public String getTeamRestriction() {
        return teamRestriction;
    }

    public void setTeamRestriction(String teamRestriction) {
        this.teamRestriction = teamRestriction == null ? "" : teamRestriction.trim().toLowerCase(Locale.ROOT);
    }

    public List<String> getAllowedCategories() {
        return List.copyOf(allowedCategories);
    }

    public void setAllowedCategories(List<String> categories) {
        allowedCategories.clear();
        if (categories != null) {
            for (String c : categories) {
                if (c != null && !c.isBlank()) allowedCategories.add(c.trim().toLowerCase(Locale.ROOT));
            }
        }
    }

    /** true — display-категория разрешена этим NPC (пустой список = всё разрешено). */
    public boolean allowsCategory(String displayCategory) {
        return allowedCategories.isEmpty() || allowedCategories.contains(displayCategory.toLowerCase(Locale.ROOT));
    }

    public int getWithdrawLimit() {
        return withdrawLimit;
    }

    public void setWithdrawLimit(int withdrawLimit) {
        this.withdrawLimit = Math.max(0, withdrawLimit);
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public void setCooldownTicks(int cooldownTicks) {
        this.cooldownTicks = Math.max(0, cooldownTicks);
    }

    // ---------------------------------------------------------------- поведение

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ItemStack inHand = serverPlayer.getItemInHand(hand);
            if (inHand.getItem() instanceof SupplyCrateItem) {
                WarehouseManager.handleCrateInteract(serverPlayer, this, hand);
            } else {
                WarehouseManager.openWarehouse(serverPlayer, this);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Кладовщик полностью бессмертен — в том числе к /kill (он бьёт уроном с тегом
        // BYPASSES_INVULNERABILITY). Убрать NPC можно только командой «/pjm entity remove»,
        // которая идёт мимо hurt() через discard().
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        // Подстраховка: даже если урон-иммунитет обойдут, снос по /kill (RemovalReason.KILLED)
        // игнорируем. Командное удаление использует discard() → RemovalReason.DISCARDED.
        if (reason == RemovalReason.KILLED) {
            return;
        }
        super.remove(reason);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
        // кладовщик неподвижен и не толкается
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkinId(tag.contains("SkinId") ? tag.getString("SkinId") : DEFAULT_SKIN);
        this.warehouseId = tag.getString("WarehouseId");
        this.teamRestriction = tag.getString("TeamRestriction");
        this.withdrawLimit = tag.getInt("WithdrawLimit");
        this.cooldownTicks = tag.getInt("CooldownTicks");
        allowedCategories.clear();
        if (tag.contains("AllowedCategories")) {
            String joined = tag.getString("AllowedCategories");
            if (!joined.isBlank()) {
                allowedCategories.addAll(Arrays.asList(joined.split(",")));
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkinId", getSkinId());
        tag.putString("WarehouseId", warehouseId);
        tag.putString("TeamRestriction", teamRestriction);
        tag.putInt("WithdrawLimit", withdrawLimit);
        tag.putInt("CooldownTicks", cooldownTicks);
        tag.putString("AllowedCategories", String.join(",", allowedCategories));
    }

    private static String sanitizeSkin(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_SKIN;
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_+\\-]", "");
    }
}
