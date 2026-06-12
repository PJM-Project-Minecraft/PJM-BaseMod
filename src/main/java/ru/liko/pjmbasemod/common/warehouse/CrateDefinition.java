package ru.liko.pjmbasemod.common.warehouse;

/**
 * Конфиг одного типа ящика поставки (config/pjmbasemod/warehouse/crates/&lt;id&gt;.json).
 * Связывает зарегистрированный предмет-ящик с пулом очков и количеством начисляемых очков.
 *
 * <p>Поля сериализуются Gson напрямую.</p>
 */
public final class CrateDefinition {

    /** id ящика = path зарегистрированного предмета (weapon_crate, supply_crate, equipment_crate, raw_crate, special_crate). */
    private String id;
    /** Пул очков, который пополняет ящик. */
    private String pool;
    /** Сколько очков даёт один ящик. */
    private int points;

    public CrateDefinition() {
        // для Gson
    }

    public CrateDefinition(String id, WarehousePoolCategory pool, int points) {
        this.id = id;
        this.pool = pool.id();
        this.points = points;
    }

    public String id() { return id == null ? "" : id; }
    public void setId(String id) { this.id = id; }

    public WarehousePoolCategory pool() {
        return WarehousePoolCategory.byIdOrDefault(pool, WarehousePoolCategory.SPECIAL);
    }

    public int points() { return Math.max(1, points); }

    public boolean isValid() {
        return !id().isBlank();
    }

    public void normalize() {
        if (points <= 0) points = 1;
    }
}
