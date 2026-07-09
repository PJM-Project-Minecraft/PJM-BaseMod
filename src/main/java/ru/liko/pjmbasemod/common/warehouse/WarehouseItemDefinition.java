package ru.liko.pjmbasemod.common.warehouse;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
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
import java.util.Map;

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
     * Сколько очков возвращается при сдаче одной выдачи — пачки из {@link #quantity} штук
     * (симметрично {@link #pointCost}). Сдача принимается только целыми пачнами, иначе возможен дюп очков.
     * {@code null} (поле отсутствует в JSON) — равно стоимости выдачи; {@code 0} — предмет не принимается.
     * Для предметов с прочностью фактическое начисление масштабируется по остатку прочности.
     */
    private Integer refundValue;
    /** Список ролей, которым доступна выдача; пусто или null — доступно всем. */
    private List<String> allowedRoles;
    private transient boolean invalidAllowedRoles;
    /** Список команд (id), которым доступна выдача; пусто или null — доступно всем командам. */
    private List<String> allowedTeams;
    private transient boolean invalidAllowedTeams;
    /** Минимальный ранг (id), начиная с которого предмет доступен; пусто/null — без ограничения по рангу. */
    private String minRank;
    /**
     * Ключ донат-пермишена. Пусто/null — предмет доступен всем. Иначе выдача требует permission-ноду
     * {@code pjmbasemod.warehouse.perm.<permission>} (выдаётся донат-плагином/LuckPerms). Один ключ
     * у нескольких предметов = общая нода («пакет доната»). См. {@link WarehouseDonorPermissions}.
     */
    private String permission;
    /**
     * Донат-«Доступ» ({@link ru.liko.pjmbasemod.common.access.AccessType}). Пусто/null — без ограничения.
     * Иначе выдача требует permission-ноду {@code pjmbasemod.access.<access>} (напр. {@code uav} — доступ
     * к БПЛА). Отдельно от {@link #permission}: {@code permission} — генерик-«пакет доната», {@code access} —
     * именованный Доступ. См. {@link ru.liko.pjmbasemod.common.access.AccessPermissions}.
     */
    private String access;
    /** Сколько штук предмета выдаётся за одну покупку (за {@link #pointCost} очков). По умолчанию 1. */
    private int quantity = 1;
    /**
     * Компоненты данных (NBT) предмета в синтаксисе команды {@code /give}, например
     * {@code "[minecraft:custom_name='АК-74М',minecraft:max_stack_size=1]"}.
     * Применяются поверх созданного стека, включая предметы TACZ/SuperBWarfare.
     * Пусто/null — стек выдаётся без изменений.
     */
    private String components;
    /** Кеш разобранного патча компонентов (ленивая инициализация на сервере, есть доступ к реестрам). */
    private transient DataComponentPatch parsedComponents;
    private transient boolean componentsParsed;
    /**
     * Полный SNBT предмета в форме {@code {components:{...},count:1,id:"namespace:item"}}
     * (как при копировании предмета в игре). Если задан — предмет выдаётся точно по этому NBT,
     * {@link #components} игнорируется, {@link #itemId} используется лишь для иконки/совпадения.
     * Заполняется командой {@code /pjm warehouse additem}.
     */
    private String itemNbt;
    private transient ItemStack templateStack;
    private transient boolean templateParsed;
    /**
     * Декларативное описание ствола TACZ: id ствола, патроны, режим огня и обвесы по слотам.
     * Предпочтительный способ задавать TACZ-оружие — читаемо и не требует ручного NBT.
     */
    private TaczGunConfig tacz;
    /**
     * Реальный TACZ-id «простого» предмета — патрона или обвеса. У таких предметов один базовый Item
     * ({@code tacz:ammo} / {@code tacz:attachment}) на все варианты, а конкретика хранится в NBT
     * (как GunId у стволов). Без этого id GUI показывал бы общий ключ {@code item.tacz.ammo}/{@code .attachment}
     * и missing-иконку. JSON-ключ: {@code "taczId"}.
     */
    private String taczId;
    /** Запрещать ли применение/удержание этого предмета игроку не той роли (allowedRoles). По умолчанию false. */
    private boolean roleLocked;
    /** Режим блокировки: "auto" (TACZ-оружие → hold, иначе use), "use", "hold". По умолчанию "auto". */
    private String lockMode;

    /** Конфиг TACZ-ствола из JSON-секции {@code "tacz"}. Поля парсятся Gson по именам. */
    public static final class TaczGunConfig {
        /** Id ствола TACZ/ганпака, напр. "mk16:ak74m" или "tacz:ak47". */
        private String gunId;
        /** Патронов в магазине. */
        private int ammo;
        /** Режим огня: AUTO / SEMI / BURST (без учёта регистра). Пусто — по умолчанию ствола. */
        private String fireMode;
        /** Дослан ли патрон в ствол; null — определяется автоматически (ammo > 0). */
        private Boolean ammoInBarrel;
        /** Обвесы по слотам: ключ — scope/muzzle/stock/grip/laser/extended_mag, значение — id обвеса. */
        private Map<String, String> attachments;

        public String gunId() { return gunId == null ? "" : gunId; }
        public int ammo() { return Math.max(0, ammo); }
        public String fireMode() { return fireMode == null ? "" : fireMode; }
        public Boolean ammoInBarrel() { return ammoInBarrel; }
        public Map<String, String> attachments() { return attachments == null ? Map.of() : attachments; }
        public boolean isPresent() { return gunId != null && !gunId.isBlank(); }
    }

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

    /** Имя как задано автором ("" если не задано) — клиент сам подтянет локализованное имя предмета. */
    public String rawDisplayName() {
        return displayName == null ? "" : displayName;
    }

    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String itemIdString() { return itemId == null ? "" : itemId; }

    /**
     * Эффективный id для иконки и имени в GUI. Для декларативного TACZ-ствола возвращает его {@code gunId}
     * (напр. "tacz:ak47"), чтобы клиент собрал ствол с выставленным GunId — тогда отрисуется правильная
     * модель и подтянется имя ствола. Для прочих предметов — обычный {@link #itemIdString()}.
     */
    public String iconId() {
        if (hasTaczGun()) return tacz.gunId();
        if (hasTaczSimpleItem()) return taczId;
        return itemIdString();
    }

    public WarehousePoolCategory pool() {
        return WarehousePoolCategory.byIdOrDefault(pool, WarehousePoolCategory.SPECIAL);
    }

    public String displayCategory() {
        return displayCategory == null || displayCategory.isBlank()
                ? pool().id() : displayCategory.trim().toLowerCase(Locale.ROOT);
    }

    public int pointCost() { return Math.max(1, pointCost); }

    public int maxPerWithdraw() { return Math.max(1, maxPerWithdraw); }

    public void setMaxPerWithdraw(int maxPerWithdraw) { this.maxPerWithdraw = maxPerWithdraw; }

    /** Сколько штук выдаётся за одну покупку (за {@link #pointCost()} очков). Минимум 1. */
    public int quantity() { return Math.max(1, quantity); }

    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String componentsString() { return components == null ? "" : components; }

    public boolean hasComponents() { return components != null && !components.isBlank(); }

    public void setComponents(String components) { this.components = components; }

    public String itemNbtString() { return itemNbt == null ? "" : itemNbt; }

    public boolean hasItemNbt() { return itemNbt != null && !itemNbt.isBlank(); }

    public void setItemNbt(String itemNbt) { this.itemNbt = itemNbt; }

    public boolean hasTaczGun() { return tacz != null && tacz.isPresent(); }

    @Nullable
    public TaczGunConfig taczGun() { return tacz; }

    /** Есть ли декларативный id «простого» TACZ-предмета (патрон/обвес). */
    public boolean hasTaczSimpleItem() { return taczId != null && !taczId.isBlank(); }

    public String taczSimpleId() { return taczId == null ? "" : taczId; }

    /** Заполняет декларативный {@code taczId} (для команды additem): патрон/обвес по реальному id. */
    public void setTaczSimpleId(String taczId) { this.taczId = taczId; }

    /**
     * Заполняет декларативный блок {@code tacz} считанными из стека данными ствола
     * (используется командой {@code additem} для надёжного захвата GunId без сырого NBT).
     */
    public void setTaczGun(String gunId, int ammo, @Nullable String fireMode,
                           @Nullable Boolean ammoInBarrel, @Nullable Map<String, String> attachments) {
        TaczGunConfig cfg = new TaczGunConfig();
        cfg.gunId = gunId;
        cfg.ammo = Math.max(0, ammo);
        cfg.fireMode = (fireMode == null || fireMode.isBlank()) ? null : fireMode;
        cfg.ammoInBarrel = ammoInBarrel;
        cfg.attachments = (attachments == null || attachments.isEmpty()) ? null : new java.util.LinkedHashMap<>(attachments);
        this.tacz = cfg;
    }

    /** Базовый возврат очков за сдачу одной пачки ({@link #quantity} штук); {@code null} → равен стоимости выдачи. */
    public int refundValue() {
        return refundValue == null ? pointCost() : Math.max(0, refundValue);
    }

    /** Принимает ли склад этот предмет в обмен на очки. */
    public boolean depositable() { return refundValue() > 0; }

    public void setRefundValue(Integer refundValue) { this.refundValue = refundValue; }

    public List<String> allowedRoles() {
        return allowedRoles == null ? List.of() : List.copyOf(allowedRoles);
    }

    public void setAllowedRoles(List<String> allowedRoles) { this.allowedRoles = allowedRoles; }

    public boolean roleRestricted() {
        return allowedRoles != null && !allowedRoles.isEmpty();
    }

    /** Включён ли роль-лок применения/удержания для этого предмета. */
    public boolean roleLocked() { return roleLocked; }

    public void setRoleLocked(boolean roleLocked) { this.roleLocked = roleLocked; }

    /** Режим блокировки: "auto" | "use" | "hold". */
    public String lockMode() {
        return lockMode == null || lockMode.isBlank() ? "auto" : lockMode;
    }

    public void setLockMode(String lockMode) { this.lockMode = lockMode; }

    public boolean hasInvalidAllowedRoles() {
        return invalidAllowedRoles;
    }

    public List<String> allowedTeams() {
        return allowedTeams == null ? List.of() : List.copyOf(allowedTeams);
    }

    public boolean teamRestricted() {
        return allowedTeams != null && !allowedTeams.isEmpty();
    }

    public boolean hasInvalidAllowedTeams() {
        return invalidAllowedTeams;
    }

    public void setAllowedTeams(List<String> allowedTeams) {
        this.allowedTeams = allowedTeams;
    }

    /** Минимальный требуемый ранг (id) или "" если ограничения нет. */
    public String minRank() {
        return minRank == null ? "" : minRank;
    }

    public boolean rankRestricted() {
        return minRank != null && !minRank.isBlank();
    }

    public void setMinRank(String minRank) { this.minRank = minRank; }

    /** Ключ донат-пермишена или "" если ограничения нет. */
    public String permission() {
        return permission == null ? "" : permission;
    }

    /** Требует ли предмет донат-пермишен для выдачи. */
    public boolean donateRestricted() {
        return permission != null && !permission.isBlank();
    }

    /** id донат-«Доступа» или "" если ограничения нет. */
    public String access() {
        return access == null ? "" : access;
    }

    public void setAccess(String access) { this.access = access; }

    /** Требует ли предмет донат-«Доступ» для выдачи. */
    public boolean accessRestricted() {
        return access != null && !access.isBlank();
    }

    public void setPermission(String permission) { this.permission = permission; }

    @Nullable
    public ResourceLocation itemLocation() {
        // itemId необязателен, если задан itemNbt — id берётся из самого NBT.
        if ((itemId == null || itemId.isBlank()) && hasItemNbt()) {
            try {
                String id = TagParser.parseTag(itemNbt.trim()).getString("id");
                if (!id.isBlank()) return ResourceLocation.tryParse(id);
            } catch (Exception ignored) {
                // ниже вернётся попытка по itemIdString()
            }
        }
        // itemId необязателен и для декларативного патрона/обвеса — id берётся из самого taczId.
        if ((itemId == null || itemId.isBlank()) && hasTaczSimpleItem()) {
            return ResourceLocation.tryParse(taczId);
        }
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

    /** Создаёт реальный ItemStack для выдачи игроку без применения компонентов (для иконок/проверок). */
    public ItemStack createStack(int count) {
        return createStack(count, null);
    }

    /**
     * Создаёт реальный ItemStack для выдачи игроку, включая виртуальные TACZ id.
     * Если задан {@link #components} и передан {@code lookup} (реестры), применяет компоненты (NBT) поверх стека.
     */
    public ItemStack createStack(int count, @Nullable HolderLookup.Provider lookup) {
        // Декларативный TACZ-ствол: собираем через GunItemBuilder (обвесы, патроны, режим огня).
        if (hasTaczGun() && lookup != null) {
            ItemStack gun = TaczWarehouseCompat.createGun(lookup, tacz.gunId(), tacz.ammo(),
                    tacz.fireMode(), tacz.ammoInBarrel(), tacz.attachments(), Math.max(1, count));
            if (!gun.isEmpty()) {
                applyComponents(gun, lookup); // дополнительные ванильные компоненты поверх, если заданы
                return gun;
            }
        }

        // Декларативный «простой» TACZ-предмет (патрон/обвес): собираем по реальному id. lookup не требуется,
        // createStack сам определяет тип (ammo/attachment) по индексам TACZ и выставляет размер стака патронов.
        if (hasTaczSimpleItem()) {
            ResourceLocation simpleLoc = ResourceLocation.tryParse(taczId);
            if (simpleLoc != null) {
                ItemStack simple = TaczWarehouseCompat.createStack(simpleLoc, Math.max(1, count));
                if (!simple.isEmpty()) {
                    applyComponents(simple, lookup);
                    return simple;
                }
            }
        }

        // Полный SNBT (захват командой) воспроизводит предмет точь-в-точь, включая данные TACZ.
        if (hasItemNbt() && lookup != null) {
            ItemStack template = templateStack(lookup);
            if (template != null && !template.isEmpty()) {
                ItemStack copy = template.copy();
                copy.setCount(Math.max(1, count));
                return copy;
            }
        }

        ResourceLocation loc = itemLocation();
        if (loc == null) return ItemStack.EMPTY;

        Item item = resolveItem();
        ItemStack stack = item != null
                ? new ItemStack(item, Math.max(1, count))
                : TaczWarehouseCompat.createStack(loc, count);

        applyComponents(stack, lookup);
        return stack;
    }

    /** Лениво разбирает {@link #itemNbt} в шаблонный стек; кеширует результат (в т.ч. неудачу как null). */
    @Nullable
    private ItemStack templateStack(HolderLookup.Provider lookup) {
        if (templateParsed) return templateStack;
        templateParsed = true;
        templateStack = null;
        try {
            CompoundTag tag = TagParser.parseTag(itemNbt.trim());
            templateStack = ItemStack.parse(lookup, tag).orElse(null);
        } catch (Exception e) {
            ru.liko.pjmbasemod.Pjmbasemod.LOGGER.warn(
                    "Warehouse: предмет '{}' — не удалось разобрать itemNbt: {}", id(), e.getMessage());
        }
        return templateStack;
    }

    /** Применяет настроенные компоненты (NBT) к стеку; ничего не делает без lookup или без components. */
    private void applyComponents(ItemStack stack, @Nullable HolderLookup.Provider lookup) {
        if (stack.isEmpty() || lookup == null || !hasComponents()) return;
        DataComponentPatch patch = parsedComponents(lookup);
        if (patch != null && patch != DataComponentPatch.EMPTY) {
            stack.applyComponents(patch);
        }
    }

    /** Лениво разбирает {@link #components} в патч компонентов; кеширует результат (в т.ч. неудачу как null). */
    @Nullable
    private DataComponentPatch parsedComponents(HolderLookup.Provider lookup) {
        if (componentsParsed) return parsedComponents;
        componentsParsed = true;
        String raw = components.trim();
        if (!raw.startsWith("[")) raw = "[" + raw + "]";
        try {
            ItemParser parser = new ItemParser(lookup);
            // Парсим компоненты на «фиктивном» предмете — нужен лишь патч, применяемый к реальному стеку.
            ItemParser.ItemResult result = parser.parse(new StringReader("minecraft:stone" + raw));
            parsedComponents = result.components();
        } catch (Exception e) {
            parsedComponents = null;
            ru.liko.pjmbasemod.Pjmbasemod.LOGGER.warn(
                    "Warehouse: предмет '{}' — не удалось разобрать components '{}': {}",
                    id(), components, e.getMessage());
        }
        return parsedComponents;
    }

    /** Проверка соответствия по типу/gunId (без сверки полного NBT — для роль-лока инвентаря). */
    public boolean matchesStack(ItemStack stack) {
        return matchesStack(stack, null);
    }

    /**
     * Соответствует ли стек этой записи склада. Для TACZ-ствола сверяется реальный {@code gunId}
     * (иначе можно сдать любой ствол того же базового предмета). Для предметов, захваченных через
     * {@code additem} (поле {@link #itemNbt}), при наличии {@code lookup} дополнительно сверяются
     * компоненты (NBT) с шаблоном — чтобы нельзя было сдать «пустой» предмет того же типа.
     */
    public boolean matchesStack(ItemStack stack, @Nullable net.minecraft.core.HolderLookup.Provider lookup) {
        if (stack.isEmpty()) return false;

        // TACZ-ствол: строго по gunId.
        if (hasTaczGun()) {
            ResourceLocation gunId = ResourceLocation.tryParse(tacz.gunId());
            return gunId != null && TaczWarehouseCompat.matches(stack, gunId);
        }

        // TACZ-патрон/обвес: строго по реальному id (иначе можно сдать любой вариант базового предмета).
        if (hasTaczSimpleItem()) {
            ResourceLocation simpleId = ResourceLocation.tryParse(taczId);
            return simpleId != null && TaczWarehouseCompat.matches(stack, simpleId);
        }

        ResourceLocation loc = itemLocation();
        if (loc == null) return false;

        Item item = resolveItem();
        if (item != null) {
            if (stack.getItem() != item) return false;
        } else if (!TaczWarehouseCompat.matches(stack, loc)) {
            return false;
        }

        // Сверка компонентов с захваченным шаблоном. Повреждаемые пропускаем: их ценность уже
        // масштабируется по прочности при сдаче, а компонент durability у изношенного отличается.
        if (hasItemNbt() && lookup != null && !stack.isDamageableItem()) {
            ItemStack template = templateStack(lookup);
            if (template != null && !template.isEmpty()) {
                return ItemStack.isSameItemSameComponents(stack, template);
            }
        }
        return true;
    }

    /** Проверка минимальной валидности определения. */
    public boolean isValid() {
        ResourceLocation loc = itemLocation();
        return !id().isBlank() && loc != null
                && !invalidAllowedRoles
                && !invalidAllowedTeams
                && (resolveItem() != null || TaczWarehouseCompat.canResolve(loc));
    }

    /** Нормализация после загрузки из JSON. */
    public void normalize() {
        if (pointCost <= 0) pointCost = 1;
        if (maxPerWithdraw <= 0) maxPerWithdraw = 16;
        if (quantity <= 0) quantity = 1;
        // Для декларативного TACZ-ствола itemId не обязателен — подставляем базовый предмет
        // (нужен для иконки в каталоге, совпадения при сдаче и валидации).
        if (hasTaczGun() && (itemId == null || itemId.isBlank())) {
            itemId = "tacz:modern_kinetic_gun";
        }
        // Для декларативного патрона/обвеса базовый itemId не обязателен — id берётся из самого taczId
        // (см. itemLocation()); базовый предмет (tacz:ammo/tacz:attachment) различается, потому не хардкодим.
        // Сброс кешей: при перезагрузке конфига строки могли измениться.
        componentsParsed = false;
        parsedComponents = null;
        templateParsed = false;
        templateStack = null;
        if (lockMode != null) {
            String lm = lockMode.trim().toLowerCase(java.util.Locale.ROOT);
            if (lm.equals("auto") || lm.equals("use") || lm.equals("hold")) {
                lockMode = lm;
            } else {
                ru.liko.pjmbasemod.Pjmbasemod.LOGGER.warn(
                        "Warehouse: предмет '{}' имеет неизвестный lockMode '{}', использую auto.", id(), lockMode);
                lockMode = "auto";
            }
        } else {
            lockMode = "auto";
        }
        if (displayCategory == null || displayCategory.isBlank()) displayCategory = pool().id();
        if (allowedRoles != null && !allowedRoles.isEmpty()) {
            List<String> normalized = CombatRole.normalizeList(allowedRoles);
            invalidAllowedRoles = normalized.isEmpty();
            allowedRoles = normalized;
        } else {
            allowedRoles = List.of();
            invalidAllowedRoles = false;
        }
        if (allowedTeams != null && !allowedTeams.isEmpty()) {
            List<String> normalized = new java.util.ArrayList<>();
            for (String raw : allowedTeams) {
                String team = ru.liko.pjmbasemod.common.teams.Teams.normalize(raw);
                if (team.isBlank() || normalized.contains(team)) continue;
                if (ru.liko.pjmbasemod.common.teams.Teams.exists(team)) {
                    normalized.add(team);
                } else {
                    ru.liko.pjmbasemod.Pjmbasemod.LOGGER.warn(
                            "Warehouse: предмет '{}' ссылается на неизвестную команду '{}', она пропущена.",
                            id(), raw);
                }
            }
            invalidAllowedTeams = normalized.isEmpty();
            allowedTeams = normalized;
        } else {
            allowedTeams = List.of();
            invalidAllowedTeams = false;
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
        if (permission != null && !permission.isBlank()) {
            // Санитайз ключа: латиница/цифры/_/-, остальное → '_'. Ключ входит в имя permission-ноды.
            String key = permission.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
            permission = key.isBlank() ? null : key;
        } else {
            permission = null;
        }
    }
}
