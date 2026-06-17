# Роль-локированное снаряжение (Фича B) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Снаряжение из «кита» роли работает только пока игрок в этой роли: сменил роль — предметы старого кита нельзя применить (для большинства предметов) или даже взять в руку (для TACZ-оружия, чей выстрел не перехватывается событиями).

**Architecture:** Принадлежность предмета роли определяется существующим `WarehouseItemDefinition.allowedRoles` + новым явным флагом `roleLocked`. Серверный `EquipmentRoleIndex` хранит подмножество roleLocked-определений с предвычисленным режимом блокировки. `EquipmentLockService` решает, запрещён ли предмет для текущей активной роли игрока (`RoleService.currentRole`), и применяет блокировку двумя путями: тик выталкивает запрещённое HOLD-оружие из руки, обработчики событий отменяют применение USE-предметов. Сервер авторитетен; клиентских изменений и новых пакетов нет.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.172, Gson, NeoForge events (PlayerTickEvent, PlayerInteractEvent, AttackEntityEvent), TACZ (compileOnly, доступ только через `TaczWarehouseCompat`).

## Global Constraints

- **Верификация — компиляцией, не тестами.** Нет тестового харнесса; `runClient`/`runServer` не работают из этого пути (`!`). Каждая задача проверяется `./gradlew compileJava` (этой фиче клиентская компиляция не требуется — изменений в `src/client` нет, но финальная задача всё равно прогоняет и `compileClientJava` для гарантии). Внутриигровую проверку делает пользователь.
- **Source set isolation:** всё в `src/main` (common). `main` НИКОГДА не импортирует `client`. Фича B не трогает `src/client` и не добавляет сетевых пакетов.
- **TACZ — опциональная зависимость (compileOnly).** Common-код НИКОГДА не обращается к классам `com.tacz.*` напрямую — только через `TaczWarehouseCompat` (внутри которого стоит `isLoaded()` guard и который делегирует в package-private `TaczWarehouseIntegration`). Любой прямой импорт `com.tacz.*` вне `TaczWarehouseIntegration` ломает dedicated-server без TACZ.
- **Конфиги** мода читаются из `config/pjmbasemod/...` (FMLPaths.CONFIGDIR). Каталог предметов склада — `config/pjmbasemod/warehouse/items.json`.
- **Локализация — во все 5 языков:** `ru_ru`, `en_us`, `uk_ua`, `de_de`, `zh_cn` (в `src/client/resources/assets/pjmbasemod/lang/`).
- **Активная роль игрока:** `RoleService.currentRole(ServerPlayer)` → `@Nullable CombatRole` (учитывает команду; null если роли нет). `role.id()` → строковый id.
- **`WarehouseItemDefinition.allowedRoles()`** → `List<String>` (нормализованные id ролей; пусто = нет ограничения). `matchesStack(ItemStack)` → `boolean` (совпадает ли стек с определением, включая TACZ). `iconStack()` → `ItemStack`. `hasTaczGun()` → `boolean`. `normalize()` вызывается при загрузке JSON.
- **`WarehouseItemRegistry.get().all()`** → `Collection<WarehouseItemDefinition>`. `reload()` → `int`.
- **Существующая блокировка инвентаря** (`InventoryLimitService`/`SlotMixin`) — это блокировка *слотов*, НЕ применения; Фича B её не меняет, а добавляет параллельный механизм.

---

### Task 1: Поля `roleLocked` и `lockMode` в `WarehouseItemDefinition`

**Files:**
- Modify: `src/main/java/ru/liko/pjmbasemod/common/warehouse/WarehouseItemDefinition.java`

**Interfaces:**
- Produces:
  - `WarehouseItemDefinition.roleLocked()` → `boolean` (по умолчанию false)
  - `WarehouseItemDefinition.lockMode()` → `String` (нормализованное: `"auto"` | `"use"` | `"hold"`, по умолчанию `"auto"`)

- [ ] **Step 1: Добавить поля**

После поля `private TaczGunConfig tacz;` (около строки 83) добавь:

```java
    /** Запрещать ли применение/удержание этого предмета игроку не той роли (allowedRoles). По умолчанию false. */
    private boolean roleLocked;
    /** Режим блокировки: "auto" (TACZ-оружие → hold, иначе use), "use", "hold". По умолчанию "auto". */
    private String lockMode;
```

- [ ] **Step 2: Добавить геттеры**

Рядом с `public boolean roleRestricted() { ... }` (около строки 191) добавь:

```java
    /** Включён ли роль-лок применения/удержания для этого предмета. */
    public boolean roleLocked() { return roleLocked; }

    /** Режим блокировки: "auto" | "use" | "hold". */
    public String lockMode() {
        return lockMode == null || lockMode.isBlank() ? "auto" : lockMode;
    }
```

- [ ] **Step 3: Нормализовать `lockMode` в `normalize()`**

В методе `normalize()`, перед строкой `if (displayCategory == null || displayCategory.isBlank()) ...` (около строки 379) добавь:

```java
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
```

- [ ] **Step 4: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/warehouse/WarehouseItemDefinition.java
git commit -m "feat(equipment): поля roleLocked и lockMode в WarehouseItemDefinition"
```

---

### Task 2: TACZ-хелпер `isGun(ItemStack)`

**Files:**
- Modify: `src/main/java/ru/liko/pjmbasemod/common/compat/TaczWarehouseCompat.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/compat/TaczWarehouseIntegration.java`

**Interfaces:**
- Produces: `TaczWarehouseCompat.isGun(ItemStack stack)` → `boolean` (true, если TACZ загружен и предмет — TACZ-ствол `IGun`; false иначе, в т.ч. без TACZ)

- [ ] **Step 1: Метод в `TaczWarehouseIntegration` (package-private, прямые TACZ-вызовы здесь разрешены)**

В `TaczWarehouseIntegration` после метода `matches(...)` (около строки 135) добавь:

```java
    static boolean isGun(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IGun;
    }
```

(`IGun` уже импортирован в этом файле — `import com.tacz.guns.api.item.IGun;`.)

- [ ] **Step 2: Безопасная обёртка в `TaczWarehouseCompat`**

В `TaczWarehouseCompat` после метода `matches(...)` (около строки 29) добавь:

```java
    /** true, если TACZ загружен и предмет — TACZ-ствол (IGun). */
    public static boolean isGun(ItemStack stack) {
        return isLoaded() && TaczWarehouseIntegration.isGun(stack);
    }
```

(`ItemStack` уже импортирован в `TaczWarehouseCompat`.)

- [ ] **Step 3: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/compat/TaczWarehouseCompat.java src/main/java/ru/liko/pjmbasemod/common/compat/TaczWarehouseIntegration.java
git commit -m "feat(equipment): TaczWarehouseCompat.isGun для определения TACZ-ствола"
```

---

### Task 3: `EquipmentRoleIndex` — индекс роль-локированных предметов

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/inventory/EquipmentRoleIndex.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/event/PjmServerEvents.java` (метод `onServerStarted`)

**Interfaces:**
- Consumes: `WarehouseItemRegistry.get().all()`, `WarehouseItemDefinition.roleLocked()/allowedRoles()/lockMode()/hasTaczGun()/iconStack()/matchesStack()` (Task 1), `TaczWarehouseCompat.isGun(...)` (Task 2)
- Produces:
  - `EquipmentRoleIndex.get()` → singleton
  - `EquipmentRoleIndex.get().rebuild()` → `int` (число роль-локированных определений)
  - `EquipmentRoleIndex.get().lookup(ItemStack)` → `@Nullable LockInfo`
  - `EquipmentRoleIndex.LockMode` — enum `{ USE, HOLD }`
  - `EquipmentRoleIndex.LockInfo` — record `(List<String> allowedRoles, LockMode mode)`

- [ ] **Step 1: Создать `EquipmentRoleIndex`**

Файл `src/main/java/ru/liko/pjmbasemod/common/inventory/EquipmentRoleIndex.java`:

```java
package ru.liko.pjmbasemod.common.inventory;

import net.minecraft.world.item.ItemStack;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.compat.TaczWarehouseCompat;
import ru.liko.pjmbasemod.common.warehouse.WarehouseItemDefinition;
import ru.liko.pjmbasemod.common.warehouse.WarehouseItemRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Индекс роль-локированного снаряжения: подмножество определений склада с roleLocked=true,
 * с предвычисленным режимом блокировки. Режим вычисляется один раз при rebuild
 * (TACZ дёргается только здесь, не на каждом тике). Опознание стека — через
 * существующий {@link WarehouseItemDefinition#matchesStack(ItemStack)}.
 */
public final class EquipmentRoleIndex {

    public enum LockMode { USE, HOLD }

    /** Какие роли допускают предмет и каким способом он блокируется для остальных. */
    public record LockInfo(List<String> allowedRoles, LockMode mode) {}

    private static final EquipmentRoleIndex INSTANCE = new EquipmentRoleIndex();

    /** Пара: определение + предвычисленный режим. */
    private record Entry(WarehouseItemDefinition def, LockMode mode) {}

    private volatile List<Entry> entries = List.of();

    private EquipmentRoleIndex() {}

    public static EquipmentRoleIndex get() { return INSTANCE; }

    /** Пересобирает индекс из текущего каталога склада. Возвращает число роль-локированных предметов. */
    public synchronized int rebuild() {
        List<Entry> built = new ArrayList<>();
        for (WarehouseItemDefinition def : WarehouseItemRegistry.get().all()) {
            if (!def.roleLocked()) continue;
            if (def.allowedRoles().isEmpty()) {
                Pjmbasemod.LOGGER.warn(
                        "Equipment: предмет '{}' помечен roleLocked, но не имеет allowedRoles — роль-лок не действует.",
                        def.id());
                continue;
            }
            built.add(new Entry(def, resolveMode(def)));
        }
        entries = List.copyOf(built);
        Pjmbasemod.LOGGER.info("Equipment: индекс роль-локированных предметов: {}.", entries.size());
        return entries.size();
    }

    /** Определение режима блокировки для предмета: явный lockMode или auto (TACZ-ствол → HOLD, иначе USE). */
    private LockMode resolveMode(WarehouseItemDefinition def) {
        switch (def.lockMode()) {
            case "use": return LockMode.USE;
            case "hold": return LockMode.HOLD;
            default:
                boolean gun = def.hasTaczGun() || TaczWarehouseCompat.isGun(def.iconStack());
                return gun ? LockMode.HOLD : LockMode.USE;
        }
    }

    /** Находит роль-лок для стека или null, если предмет не роль-локирован. */
    @Nullable
    public LockInfo lookup(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Entry entry : entries) {
            if (entry.def().matchesStack(stack)) {
                return new LockInfo(entry.def().allowedRoles(), entry.mode());
            }
        }
        return null;
    }
}
```

- [ ] **Step 2: Пересобирать индекс на старте сервера**

В `PjmServerEvents.onServerStarted` найди строку `WarehouseItemRegistry.get().reload();` (около строки 174) и добавь сразу после неё:

```java
        ru.liko.pjmbasemod.common.inventory.EquipmentRoleIndex.get().rebuild();
```

(Индекс зависит от каталога склада — пересобираем после его перезагрузки.)

- [ ] **Step 3: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/inventory/EquipmentRoleIndex.java src/main/java/ru/liko/pjmbasemod/common/event/PjmServerEvents.java
git commit -m "feat(equipment): индекс роль-локированных предметов EquipmentRoleIndex"
```

---

### Task 4: `EquipmentLockService` — логика запрета

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/inventory/EquipmentLockService.java`

**Interfaces:**
- Consumes: `EquipmentRoleIndex.get().lookup(...)`, `EquipmentRoleIndex.LockInfo`, `EquipmentRoleIndex.LockMode` (Task 3), `RoleService.currentRole(ServerPlayer)`
- Produces:
  - `EquipmentLockService.isForbidden(ServerPlayer, ItemStack)` → `boolean` (предмет роль-локирован и активная роль игрока не входит в его allowedRoles)
  - `EquipmentLockService.enforceHeld(ServerPlayer)` → `void` (выталкивает запрещённое HOLD-оружие из основной руки)
  - `EquipmentLockService.shouldCancelUse(ServerPlayer, ItemStack)` → `boolean` (для обработчиков событий: предмет запрещён → отменить применение; шлёт троттленное сообщение)

- [ ] **Step 1: Создать `EquipmentLockService`**

Файл `src/main/java/ru/liko/pjmbasemod/common/inventory/EquipmentLockService.java`:

```java
package ru.liko.pjmbasemod.common.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.liko.pjmbasemod.common.role.RoleService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Серверная блокировка снаряжения чужой роли. HOLD-предметы (TACZ-оружие) выталкиваются
 * из руки на тике; USE-предметы блокируются обработчиками событий применения.
 * Сервер авторитетен; клиентских зеркал нет.
 */
public final class EquipmentLockService {

    /** Последний последний слот основного инвентаря (хотбар + 3 ряда). */
    private static final int MAIN_INVENTORY_END = 35;
    /** Минимальный интервал между предупреждениями игроку, тики. */
    private static final long WARN_INTERVAL_TICKS = 40L;

    private static final Map<UUID, Long> lastWarnTick = new HashMap<>();

    private EquipmentLockService() {}

    /** Предмет роль-локирован И активная роль игрока не входит в его allowedRoles (или роли нет). */
    public static boolean isForbidden(ServerPlayer player, ItemStack stack) {
        if (player == null || stack.isEmpty()) return false;
        EquipmentRoleIndex.LockInfo info = EquipmentRoleIndex.get().lookup(stack);
        if (info == null) return false;
        var role = RoleService.currentRole(player);
        return role == null || !info.allowedRoles().contains(role.id());
    }

    /** Выталкивает из основной руки запрещённое HOLD-оружие (нельзя держать → нельзя выстрелить). */
    public static void enforceHeld(ServerPlayer player) {
        if (player == null || player.isCreative()) return;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;

        EquipmentRoleIndex.LockInfo info = EquipmentRoleIndex.get().lookup(held);
        if (info == null || info.mode() != EquipmentRoleIndex.LockMode.HOLD) return;
        if (!isForbidden(player, held)) return;

        Inventory inv = player.getInventory();
        int handSlot = inv.selected;
        if (handSlot < 0 || handSlot > 8) return; // активна не основная рука хотбара
        if (!moveToBackpack(inv, handSlot)) return; // некуда убрать — оставляем как есть

        player.inventoryMenu.broadcastChanges();
        warn(player);
    }

    /** Для обработчиков событий применения: запрещён ли предмет; при запрете шлёт троттленное сообщение. */
    public static boolean shouldCancelUse(ServerPlayer player, ItemStack stack) {
        if (!isForbidden(player, stack)) return false;
        warn(player);
        return true;
    }

    /**
     * Перекладывает предмет из слота {@code from} в свободный слот рюкзака (9..35).
     * Возвращает false, если свободных слотов нет.
     */
    private static boolean moveToBackpack(Inventory inv, int from) {
        ItemStack stack = inv.getItem(from);
        if (stack.isEmpty()) return false;
        for (int i = 9; i <= MAIN_INVENTORY_END; i++) {
            if (inv.getItem(i).isEmpty()) {
                inv.setItem(i, stack);
                inv.setItem(from, ItemStack.EMPTY);
                return true;
            }
        }
        return false;
    }

    private static void warn(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = lastWarnTick.get(player.getUUID());
        if (last != null && now - last < WARN_INTERVAL_TICKS) return;
        lastWarnTick.put(player.getUUID(), now);
        player.displayClientMessage(
                Component.translatable("gui.pjmbasemod.equipment.wrong_role"), true);
    }
}
```

- [ ] **Step 2: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/inventory/EquipmentLockService.java
git commit -m "feat(equipment): EquipmentLockService — выталкивание из руки + отмена применения"
```

---

### Task 5: Подключение событий в `PjmServerEvents`

**Files:**
- Modify: `src/main/java/ru/liko/pjmbasemod/common/event/PjmServerEvents.java`

**Interfaces:**
- Consumes: `EquipmentLockService.enforceHeld(...)`, `EquipmentLockService.shouldCancelUse(...)` (Task 4)
- Produces: тик-enforce удержания + отмена применения USE-предметов через 4 события (RightClickItem, RightClickBlock, EntityInteract, AttackEntity).

- [ ] **Step 1: Импорты**

В блоке импортов `PjmServerEvents` добавь:

```java
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import ru.liko.pjmbasemod.common.inventory.EquipmentLockService;
```

(`PlayerInteractEvent` и `InteractionHand` уже импортированы; `ItemStack` нужно добавить, если его нет: `import net.minecraft.world.item.ItemStack;`.)

- [ ] **Step 2: Тик-enforce удержания**

В методе `onPlayerTick`, после строки `RoleService.onPlayerTick(player);` (около строки 115) добавь:

```java
        EquipmentLockService.enforceHeld(player);
```

- [ ] **Step 3: Расширить существующий `onEntityInteract`**

В методе `onEntityInteract` (около строки 127), сразу после блока проверок `if (!(event.getEntity() instanceof ServerPlayer player)) return;` (строка 130) и ПЕРЕД логикой ноутбука/гаража добавь:

```java
        if (EquipmentLockService.shouldCancelUse(player, player.getMainHandItem())) {
            event.setCanceled(true);
            return;
        }
```

- [ ] **Step 4: Добавить три новых обработчика**

После метода `onEntityInteract` (после строки 145) добавь:

```java
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (EquipmentLockService.shouldCancelUse(player, event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (EquipmentLockService.shouldCancelUse(player, event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;
        if (EquipmentLockService.shouldCancelUse(player, player.getMainHandItem())) {
            event.setCanceled(true);
        }
    }
```

- [ ] **Step 5: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/event/PjmServerEvents.java
git commit -m "feat(equipment): тик-enforce удержания + отмена применения чужого снаряжения"
```

---

### Task 6: Пересборка индекса в `/pjm reload` + локализация

**Files:**
- Modify: `src/main/java/ru/liko/pjmbasemod/common/command/PjmCommands.java`
- Modify: `src/client/resources/assets/pjmbasemod/lang/ru_ru.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/en_us.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/uk_ua.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/de_de.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/zh_cn.json`

**Interfaces:**
- Consumes: `EquipmentRoleIndex.get().rebuild()` (Task 3); ключ `gui.pjmbasemod.equipment.wrong_role` (используется в Task 4, без аргументов).

- [ ] **Step 1: Пересобирать индекс при reload секции `warehouse`**

Открой `PjmCommands.java`. Найди ветку обработки reload для секции `warehouse` (там вызывается `WarehouseItemRegistry.get().reload()`, по аналогии с веткой `roles` из Фичи A). Сразу после вызова `WarehouseItemRegistry.get().reload()` (и до отчётного сообщения) добавь:

```java
            ru.liko.pjmbasemod.common.inventory.EquipmentRoleIndex.get().rebuild();
```

Это гарантирует, что после правки `items.json` и `/pjm config reload warehouse` индекс роль-локов обновится без перезапуска сервера. Если в ветке `warehouse` несколько reload-вызовов (items/crates) — ставь rebuild после `WarehouseItemRegistry`. Не меняй другие секции.

- [ ] **Step 2: Добавить ключ локализации во все 5 файлов**

В каждом lang-файле найди группу `gui.pjmbasemod.equipment.*` (если её нет — добавь рядом с другими `gui.pjmbasemod.*` ключами) и добавь:

`ru_ru.json`:
```json
  "gui.pjmbasemod.equipment.wrong_role": "§cЭто снаряжение доступно только своей роли.",
```
`en_us.json`:
```json
  "gui.pjmbasemod.equipment.wrong_role": "§cThis equipment is restricted to its role.",
```
`uk_ua.json`:
```json
  "gui.pjmbasemod.equipment.wrong_role": "§cЦе спорядження доступне лише своїй ролі.",
```
`de_de.json`:
```json
  "gui.pjmbasemod.equipment.wrong_role": "§cDiese Ausrüstung ist nur für ihre Rolle verfügbar.",
```
`zh_cn.json`:
```json
  "gui.pjmbasemod.equipment.wrong_role": "§c该装备仅限对应职业使用。",
```

Следи за запятыми: не последний ключ в объекте → запятая в конце; перед закрывающей `}` запятой быть не должно.

- [ ] **Step 3: Валидация JSON**

Run: `for f in src/client/resources/assets/pjmbasemod/lang/*.json; do python3 -c "import json,sys; json.load(open('$f', encoding='utf-8'))" && echo "OK $f"; done`
Expected: `OK` для всех пяти файлов.

- [ ] **Step 4: Финальная компиляция (common + client)**

Run: `./gradlew compileJava` затем `./gradlew compileClientJava`
Expected: оба BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/command/PjmCommands.java src/client/resources/assets/pjmbasemod/lang/ru_ru.json src/client/resources/assets/pjmbasemod/lang/en_us.json src/client/resources/assets/pjmbasemod/lang/uk_ua.json src/client/resources/assets/pjmbasemod/lang/de_de.json src/client/resources/assets/pjmbasemod/lang/zh_cn.json
git commit -m "feat(equipment): rebuild индекса в /pjm reload + локализация (5 языков)"
```

---

## Итог и ручная проверка пользователем

После всех задач (из пути без `!`):
1. В `config/pjmbasemod/warehouse/items.json` пометить предмет кита роли: добавить `"roleLocked": true` (и убедиться, что у него непустой `"allowedRoles"`, например `["uav_operator"]`). Для TACZ-оружия режим определится как HOLD автоматически.
2. `/pjm config reload warehouse` — индекс пересоберётся.
3. Игроком с ролью UAV_OPERATOR: оружие кита держится и стреляет нормально.
4. Сменить роль (или снять): TACZ-оружие чужого кита **выталкивается из руки** при попытке держать; право-клик/атака обычным роль-локированным предметом чужого кита **не срабатывает**, появляется сообщение «снаряжение доступно только своей роли».
5. Предметы без `roleLocked` ведут себя как раньше.

## Self-Review (выполнено при написании плана)

- **Spec coverage:** детект через `allowedRoles` + явный `roleLocked` (Task 1); опознание через `matchesStack`, индекс подмножества (Task 3); двухуровневая блокировка — HOLD выталкивание (Task 4 enforceHeld) + USE отмена событий (Task 5); auto-режим TACZ→HOLD (Task 3 resolveMode + Task 2 isGun); rebuild на старте и в команде (Tasks 3, 6); локализация (Task 6); серверный авторитет без клиента/сети (вся фича в common). Все разделы спека покрыты.
- **Placeholder scan:** плейсхолдеров нет, код приведён полностью.
- **Type consistency:** `LockInfo(List<String> allowedRoles, LockMode mode)`, `LockMode{USE,HOLD}`, `lookup(ItemStack)→LockInfo`, `isForbidden(ServerPlayer,ItemStack)`, `enforceHeld(ServerPlayer)`, `shouldCancelUse(ServerPlayer,ItemStack)`, `roleLocked()`, `lockMode()`, `TaczWarehouseCompat.isGun(ItemStack)` — согласованы между задачами.
- **YAGNI:** клиентское зеркало и сетевые пакеты сознательно исключены (серверный авторитет достаточен для MVP, спек это допускал).
