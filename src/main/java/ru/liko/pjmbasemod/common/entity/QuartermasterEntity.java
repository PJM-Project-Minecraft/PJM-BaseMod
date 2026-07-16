package ru.liko.pjmbasemod.common.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import ru.liko.pjmbasemod.common.capturepoint.CapturePointSavedData;
import ru.liko.pjmbasemod.common.item.SupplyCrateItem;
import ru.liko.pjmbasemod.common.teams.Teams;
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

    // --- Режим раздатчика ящиков на точке захвата (пусто = обычный кладовщик) ---
    /** id ящика для выдачи (weapon_crate/supply_crate/...); пусто — NPC не раздатчик. */
    private String dispenserCrateId = "";
    /** Максимальный запас ящиков у точки. */
    private int stockMax = 0;
    /** Текущий запас. */
    private int stockCurrent = 0;
    /** Секунд на восполнение +1 ящика (0 — без регена). */
    private int regenSeconds = 0;
    /** Игровое время, от которого отсчитывается реген (обновляется лениво). */
    private long lastRegenTime = 0L;

    /** Якорная позиция: NPC возвращается сюда, если его сдвинули (нокбэк, крючок, поршень). */
    @Nullable
    private Vec3 anchor;

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

    public boolean isDispenser() {
        return !dispenserCrateId.isBlank();
    }

    /** Настроить NPC как раздатчик ящиков (crateId пусто/none — снять режим). */
    public void setDispenser(String crateId, int stockMax, int regenSeconds) {
        this.dispenserCrateId = crateId == null || crateId.equalsIgnoreCase("none") ? "" : crateId.trim().toLowerCase(Locale.ROOT);
        this.stockMax = Math.max(0, stockMax);
        this.stockCurrent = this.stockMax;
        this.regenSeconds = Math.max(0, regenSeconds);
        this.lastRegenTime = level().getGameTime();
    }

    // ---------------------------------------------------------------- поведение

    @Override
    public void tick() {
        // Первый тик фиксирует якорь. Любой сдвиг (нокбэк не из hurt, крючок, поршень,
        // wind charge) откатывается: push() заглушён, но прочие векторы идут мимо него.
        if (anchor == null) anchor = position();
        super.tick();
        this.setDeltaMovement(0, 0, 0);
        if (getX() != anchor.x || getY() != anchor.y || getZ() != anchor.z) {
            this.setPos(anchor.x, anchor.y, anchor.z);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (isDispenser()) {
                dispense(serverPlayer);
                return InteractionResult.CONSUME;
            }
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

    /**
     * Выдача ящика на точке захвата: только команде-владельцу точки, из ограниченного
     * запаса. Реген запаса ленивый — считается по игровому времени в момент обращения,
     * без серверного тика.
     */
    private void dispense(ServerPlayer player) {
        regenStock();
        String dim = level().dimension().location().toString();
        String owner = CapturePointSavedData.get(player.server)
                .ownerTeamAt(dim, blockPosition().getX(), blockPosition().getZ());
        if (owner.isBlank()) {
            player.displayClientMessage(Component.literal("Точка ничейная — выдача недоступна."), true);
            return;
        }
        String team = Teams.resolvePlayerTeamId(player);
        if (team == null || !owner.equals(team)) {
            player.displayClientMessage(Component.literal("Ящики выдаёт только команда-владелец точки."), true);
            return;
        }
        if (stockCurrent <= 0) {
            player.displayClientMessage(Component.literal("Запас пуст — ждите пополнения."), true);
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("pjmbasemod", dispenserCrateId));
        if (!(item instanceof SupplyCrateItem)) {
            player.displayClientMessage(Component.literal("Ошибка: ящик '" + dispenserCrateId + "' не найден."), true);
            return;
        }
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        stockCurrent--;
        player.displayClientMessage(Component.literal("Выдан ящик · осталось " + stockCurrent + "/" + stockMax), true);
    }

    private void regenStock() {
        if (stockCurrent >= stockMax || regenSeconds <= 0) {
            lastRegenTime = level().getGameTime(); // держим часы актуальными, пока запас полон/реген выключен
            return;
        }
        long now = level().getGameTime();
        long per = (long) regenSeconds * 20L;
        long elapsed = now - lastRegenTime;
        if (elapsed >= per) {
            int gained = (int) (elapsed / per);
            stockCurrent = Math.min(stockMax, stockCurrent + gained);
            lastRegenTime = stockCurrent >= stockMax ? now : lastRegenTime + (long) gained * per;
        }
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
    public void push(double x, double y, double z) {
        // Полностью игнорируем внешние импульсы (толчок игроком при коллизии, толпа,
        // поток жидкости, взрыв): через push() все внешние силы добавляют скорость.
        // isPushable()==false не перехватывает толчок игрока в этой версии, поэтому глушим здесь.
        // Гравитация идёт мимо push() (через deltaMovement в travel()), так что NPC стоит на земле.
    }

    @Override
    public boolean isPushedByFluid() {
        // Кладовщик не сносится течением воды/лавы.
        return false;
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
        this.dispenserCrateId = tag.getString("DispenserCrate");
        this.stockMax = tag.getInt("StockMax");
        this.stockCurrent = tag.getInt("StockCurrent");
        this.regenSeconds = tag.getInt("RegenSeconds");
        this.lastRegenTime = tag.getLong("LastRegen");
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
        tag.putString("DispenserCrate", dispenserCrateId);
        tag.putInt("StockMax", stockMax);
        tag.putInt("StockCurrent", stockCurrent);
        tag.putInt("RegenSeconds", regenSeconds);
        tag.putLong("LastRegen", lastRegenTime);
        tag.putString("AllowedCategories", String.join(",", allowedCategories));
    }

    private static String sanitizeSkin(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_SKIN;
        // Символы вне набора валидного пути ResourceLocation ('+', пробелы и т.п.) ЗАМЕНЯЕМ на '_',
        // а не оставляем: '+' в пути textures/skins/<id>.png роняет клиент (ResourceLocationException)
        // при рендере NPC. Легаси-имена вида "skin_mc+jacket" так превращаются в существующую
        // "skin_mc_jacket". Набор символов согласован со SkinRegistry.sanitize.
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.\\-]", "_");
        return cleaned.isBlank() ? DEFAULT_SKIN : cleaned;
    }
}
