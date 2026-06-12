package ru.liko.pjmbasemod.common.garage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.liko.pjmbasemod.common.role.CombatRole;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Описание одного типа техники из каталога (config/pjmbasemod/vehicles/&lt;id&gt;.json).
 *
 * <p>Поля сериализуются Gson напрямую (имена совпадают с ключами JSON).</p>
 */
public final class VehicleDefinition {

    /** Уникальный id определения внутри мода (имя файла без .json). */
    private String id;
    /** Отображаемое имя в GUI. */
    private String displayName;
    /** Тип сущности техники в реестре, напр. "superbwarfare:m_1a_2". */
    private String entityType;
    /** Предмет-иконка для GUI, напр. "minecraft:iron_block". */
    private String icon;
    /** Произвольная категория для группировки (tank, heli, apc, ...). */
    private String category;
    /** Время сборки в секундах. */
    private int assemblyTime;
    /** Список ресурсов для сборки. */
    private List<CostEntry> cost;
    /** Список ролей, которым доступна техника; пусто или null — доступно всем. */
    private List<String> allowedRoles;
    private transient boolean invalidAllowedRoles;

    public VehicleDefinition() {
        // для Gson
    }

    public VehicleDefinition(String id, String displayName, String entityType,
                             String icon, String category, int assemblyTime, List<CostEntry> cost) {
        this.id = id;
        this.displayName = displayName;
        this.entityType = entityType;
        this.icon = icon;
        this.category = category;
        this.assemblyTime = assemblyTime;
        this.cost = cost;
    }

    public VehicleDefinition(String id, String displayName, String entityType,
                             String icon, String category, int assemblyTime, List<CostEntry> cost,
                             List<String> allowedRoles) {
        this(id, displayName, entityType, icon, category, assemblyTime, cost);
        this.allowedRoles = allowedRoles;
    }

    public String id() { return id == null ? "" : id; }
    public void setId(String id) { this.id = id; }

    public String displayName() {
        return displayName == null || displayName.isBlank() ? id() : displayName;
    }

    public String entityTypeString() { return entityType == null ? "" : entityType; }

    public String category() { return category == null ? "" : category; }
    
    public int assemblyTime() { return assemblyTime; }

    public List<CostEntry> cost() {
        return cost == null ? List.of() : cost;
    }

    public List<String> allowedRoles() {
        return allowedRoles == null ? List.of() : List.copyOf(allowedRoles);
    }

    public boolean roleRestricted() {
        return allowedRoles != null && !allowedRoles.isEmpty();
    }

    public boolean hasInvalidAllowedRoles() {
        return invalidAllowedRoles;
    }

    @Nullable
    public ResourceLocation entityTypeId() {
        return ResourceLocation.tryParse(entityTypeString());
    }

    public ItemStack iconStack() {
        ResourceLocation iconId = icon == null ? null : ResourceLocation.tryParse(icon);
        Item item = iconId == null ? null : BuiltInRegistries.ITEM.get(iconId);
        if (item == null || item == Items.AIR) return new ItemStack(Items.MINECART);
        return new ItemStack(item);
    }

    /** Проверка минимальной валидности определения. */
    public boolean isValid() {
        return !id().isBlank() && entityTypeId() != null && !invalidAllowedRoles;
    }

    /** Нормализует пустые коллекции после загрузки из JSON. */
    public void normalize() {
        if (cost == null) cost = new ArrayList<>();
        if (allowedRoles != null && !allowedRoles.isEmpty()) {
            List<String> normalized = CombatRole.normalizeList(allowedRoles);
            invalidAllowedRoles = normalized.isEmpty();
            allowedRoles = normalized;
        } else {
            allowedRoles = List.of();
            invalidAllowedRoles = false;
        }
    }
}
