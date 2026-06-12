package ru.liko.pjmbasemod.common.warehouse;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.liko.pjmbasemod.common.compat.TaczWarehouseCompat;
import ru.liko.pjmbasemod.common.role.CombatRole;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * Описание одного выдаваемого предмета склада
 * (config/pjmbasemod/warehouse/items.json или legacy-файл items/&lt;id&gt;.json).
 *
 * <p>Поля сериализуются Gson напрямую (имена совпадают с ключами JSON).</p>
 */
public final class WarehouseItemDefinition {

    /** Уникальный id определения. */
    private String id;
    /** Отображаемое имя в GUI. */
    private String displayName;
    /** Registry id предмета или TACZ gunpack id, напр. "superbwarfare:ak_47" или "tacz:ak47". */
    private String itemId;
    /** Из какого пула очков списывается стоимость (weapon/supply/equipment/raw/special). */
    private String pool;
    /** Категория для группировки во вкладках GUI (weapon, ammo, food, medicine, equipment, raw, vehicle, special). */
    private String displayCategory;
    /** Стоимость одной выдачи в очках пула. */
    private int pointCost;
    /** Максимум за одну выдачу (0 — без ограничения уровня предмета). */
    private int maxPerWithdraw;
    /**
     * Сколько очков возвращается при сдаче одной штуки на склад.
     * {@code null} (поле отсутствует в JSON) — равно стоимости выдачи; {@code 0} — предмет не принимается.
     * Для предметов с прочностью фактическое начисление масштабируется по остатку прочности.
     */
    private Integer refundValue;
    /** Список ролей, которым доступна выдача; пусто или null — доступно всем. */
    private List<String> allowedRoles;
    private transient boolean invalidAllowedRoles;
    /** Минимальный ранг (id), начиная с которого предмет доступен; пусто/null — без ограничения по рангу. */
    private String minRank;

    public WarehouseItemDefinition() {
        // для Gson
    }

    public WarehouseItemDefinition(String id, String displayName, String itemId, WarehousePoolCategory pool,
                                   String displayCategory, int pointCost, int maxPerWithdraw) {
        this.id = id;
        this.displayName = displayName;
        this.itemId = itemId;
        this.pool = pool.id();
        this.displayCategory = displayCategory;
        this.pointCost = pointCost;
        this.maxPerWithdraw = maxPerWithdraw;
    }

    public WarehouseItemDefinition(String id, String displayName, String itemId, WarehousePoolCategory pool,
                                   String displayCategory, int pointCost, int maxPerWithdraw,
                                   List<String> allowedRoles) {
        this(id, displayName, itemId, pool, displayCategory, pointCost, maxPerWithdraw);
        this.allowedRoles = allowedRoles;
    }

    public String id() { return id == null ? "" : id; }
    public void setId(String id) { this.id = id; }

    public String displayName() {
        return displayName == null || displayName.isBlank() ? id() : displayName;
    }

    public String itemIdString() { return itemId == null ? "" : itemId; }

    public WarehousePoolCategory pool() {
        return WarehousePoolCategory.byIdOrDefault(pool, WarehousePoolCategory.SPECIAL);
    }

    public String displayCategory() {
        return displayCategory == null || displayCategory.isBlank()
                ? pool().id() : displayCategory.trim().toLowerCase(Locale.ROOT);
    }

    public int pointCost() { return Math.max(1, pointCost); }

    public int maxPerWithdraw() { return Math.max(1, maxPerWithdraw); }

    /** Базовый возврат очков за сдачу одной штуки; {@code null} → равен стоимости выдачи. */
    public int refundValue() {
        return refundValue == null ? pointCost() : Math.max(0, refundValue);
    }

    /** Принимает ли склад этот предмет в обмен на очки. */
    public boolean depositable() { return refundValue() > 0; }

    public void setRefundValue(Integer refundValue) { this.refundValue = refundValue; }

    public List<String> allowedRoles() {
        return allowedRoles == null ? List.of() : List.copyOf(allowedRoles);
    }

    public boolean roleRestricted() {
        return allowedRoles != null && !allowedRoles.isEmpty();
    }

    public boolean hasInvalidAllowedRoles() {
        return invalidAllowedRoles;
    }

    /** Минимальный требуемый ранг (id) или "" если ограничения нет. */
    public String minRank() {
        return minRank == null ? "" : minRank;
    }

    public boolean rankRestricted() {
        return minRank != null && !minRank.isBlank();
    }

    public void setMinRank(String minRank) { this.minRank = minRank; }

    @Nullable
    public ResourceLocation itemLocation() {
        return ResourceLocation.tryParse(itemIdString());
    }

    @Nullable
    public Item resolveItem() {
        ResourceLocation loc = itemLocation();
        if (loc == null) return null;
        Item item = BuiltInRegistries.ITEM.get(loc);
        return item == Items.AIR ? null : item;
    }

    public ItemStack iconStack() {
        ItemStack stack = createStack(1);
        return stack.isEmpty() ? new ItemStack(Items.BARRIER) : stack;
    }

    /** Создаёт реальный ItemStack для выдачи игроку, включая виртуальные TACZ id. */
    public ItemStack createStack(int count) {
        ResourceLocation loc = itemLocation();
        if (loc == null) return ItemStack.EMPTY;

        Item item = resolveItem();
        if (item != null) {
            return new ItemStack(item, Math.max(1, count));
        }

        return TaczWarehouseCompat.createStack(loc, count);
    }

    /** Проверяет, что стек соответствует этой записи склада. */
    public boolean matchesStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation loc = itemLocation();
        if (loc == null) return false;

        Item item = resolveItem();
        if (item != null) {
            return stack.getItem() == item;
        }

        return TaczWarehouseCompat.matches(stack, loc);
    }

    /** Проверка минимальной валидности определения. */
    public boolean isValid() {
        ResourceLocation loc = itemLocation();
        return !id().isBlank() && loc != null
                && !invalidAllowedRoles
                && (resolveItem() != null || TaczWarehouseCompat.canResolve(loc));
    }

    /** Нормализация после загрузки из JSON. */
    public void normalize() {
        if (pointCost <= 0) pointCost = 1;
        if (maxPerWithdraw <= 0) maxPerWithdraw = 16;
        if (displayCategory == null || displayCategory.isBlank()) displayCategory = pool().id();
        if (allowedRoles != null && !allowedRoles.isEmpty()) {
            List<String> normalized = CombatRole.normalizeList(allowedRoles);
            invalidAllowedRoles = normalized.isEmpty();
            allowedRoles = normalized;
        } else {
            allowedRoles = List.of();
            invalidAllowedRoles = false;
        }
        if (minRank != null && !minRank.isBlank()) {
            String id = minRank.trim().toLowerCase(Locale.ROOT);
            if (ru.liko.pjmbasemod.common.rank.RankRegistry.get().byId(id) == null) {
                ru.liko.pjmbasemod.Pjmbasemod.LOGGER.warn(
                        "Warehouse: предмет '{}' ссылается на неизвестный minRank '{}', ограничение по рангу снято.",
                        id(), minRank);
                minRank = null;
            } else {
                minRank = id;
            }
        } else {
            minRank = null;
        }
    }
}
