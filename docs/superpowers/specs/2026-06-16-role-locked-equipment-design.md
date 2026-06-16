# Дизайн: Роль-локированное снаряжение (Фича B)

Дата: 2026-06-16
Статус: на ревью
Зависит от: `2026-06-16-donation-roles-design.md` (Фича A — активная роль игрока)

## Проблема

Снаряжение из «кита» роли должно работать только пока игрок в этой роли. Сменил роль — предметы старого кита использовать нельзя. Принадлежность предмета роли определяется через каталог склада (`WarehouseItemDefinition`). Для TACZ-оружия перехватить сам выстрел нельзя (нет ванильного события), поэтому такие предметы **запрещаем брать в руку**.

## Ключевые решения

1. **Детект принадлежности — без новых полей привязки.** У `WarehouseItemDefinition` уже есть `allowedRoles` (`List<String>`). Предмет считается «китовым», если он помечен `roleLocked: true` (см. ниже) и имеет непустой `allowedRoles`. Предмет «чужой», если активная роль игрока не входит в его `allowedRoles`.

2. **Включение — явным флагом** (консервативно, чтобы не сломать существующие конфиги склада, где `allowedRoles` уже используется для ограничения *выдачи*). Новое поле `roleLocked` (boolean, по умолчанию `false`) в `WarehouseItemDefinition`. Опционально `lockMode: "auto" | "use" | "hold"` (по умолчанию `auto`).

3. **Двухуровневая блокировка**, режим выбирается авто:
   - **Запрет применения** (`use`) — для предметов с ванильными событиями. Предмет можно носить, но не применить.
   - **Запрет удержания в руке** (`hold`) — для TACZ-стволов (`stack.getItem() instanceof IGun`) и всего, где `lockMode=hold`. Нельзя держать в выбранном слоте → выстрел невозможен по построению.
   - `auto`: TACZ/`IGun` → `hold`, иначе → `use`.

## Компоненты

### 1. Расширение `WarehouseItemDefinition`

Добавить поля (Gson):
- `boolean roleLocked` (default `false`)
- `String lockMode` (default `"auto"`, значения `auto`/`use`/`hold`)

Геттеры + учёт в `normalize()`. Существующие конфиги без этих полей продолжают работать (роль-лок выключен).

### 2. Опознание стека и его ролей — `EquipmentRoleIndex`

Перебор всего каталога на каждом тике недопустим. Новый кеш `common/inventory/EquipmentRoleIndex`:

- Строится из `WarehouseItemRegistry` при `reload()`.
- Индексирует только определения с `roleLocked = true`.
- Ключи поиска: ванильный `itemId` и TACZ `gunId` (через `IGun.getGunId(stack)` из `TaczWarehouseIntegration`).
- Метод `LockInfo lookup(ItemStack stack)` → `{ allowedRoles, lockMode }` или `null`, если предмет не роль-локирован. При неоднозначности (несколько определений на один itemId) — fallback на `def.matchesStack(stack)`.

### 3. Сервис блокировки — `EquipmentLockService`

Новый `common/inventory/EquipmentLockService` рядом с `InventoryLimitService`. Использует `RoleService.currentRole(player)` + `EquipmentRoleIndex`.

Вспомогательное:
```java
// предмет роль-локирован и активная роль игрока НЕ в allowedRoles
static boolean isForbidden(ServerPlayer player, ItemStack stack)
```

**Уровень «hold» (тик):** в `PjmServerEvents.onPlayerTick` (или собственный `PlayerTickEvent.Post`):
- Если в выбранном слоте hotbar (`player.getInventory().selected`) лежит запрещённый предмет с режимом `hold` — вытолкнуть его в свободный незаблокированный слот (переиспользовать логику перемещения из `InventoryLimitService.enforce`) либо переключить выбранный слот на пустой. Сопроводить `displayClientMessage` («снаряжение чужой роли»), с троттлингом, чтобы не спамить.

**Уровень «use» (события):** новые `@SubscribeEvent` в `PjmServerEvents` (server-side):
- `PlayerInteractEvent.RightClickItem`, `PlayerInteractEvent.RightClickBlock`, `PlayerInteractEvent.EntityInteract`, `AttackEntityEvent`.
- Если `isForbidden(player, event.getItemStack())` и режим `use` → `event.setCanceled(true)` + троттленное сообщение.

### 4. Клиентское зеркало (отзывчивость)

Чтобы запрет ощущался мгновенно (особенно `hold`):
- Новый пакет `EquipmentLockPacket` (S→C): набор «запрещённых сейчас» сигнатур предметов либо проще — клиент сам вычисляет на основе активной роли + синхронизированного индекса роль-локов.
- MVP-вариант: сервер авторитетен (тик выталкивает из руки), клиент опционально подсвечивает. Полное клиентское зеркало индекса — отдельная итерация, если серверного хватит визуально.
- Решение по объёму клиентской части принять при реализации; начать с серверной авторитетности.

### 5. Сетевой слой и прочее

- При добавлении пакета — регистрация в `PjmNetworking`, бамп `VERSION`, метод в `ClientPacketProxy` + реализация.
- `EquipmentRoleIndex.rebuild()` вызывать после `WarehouseItemRegistry.reload()` (на `ServerStartedEvent` и в `/pjm ... reload`).
- Локализация сообщений во все 5 языков.

## Затронутые/новые файлы

| Файл | Изменение |
|---|---|
| `common/warehouse/WarehouseItemDefinition.java` | +`roleLocked`, +`lockMode`, геттеры, `normalize()` |
| `common/inventory/EquipmentRoleIndex.java` | **новый** — кеш itemId/gunId → {roles, lockMode} |
| `common/inventory/EquipmentLockService.java` | **новый** — `isForbidden`, enforce hold, отмена use |
| `common/event/PjmServerEvents.java` | тик-enforce + обработчики use/attack |
| `common/compat/TaczWarehouseIntegration.java` | (при необходимости) хелпер «получить gunId из стека» |
| `common/network/packet/EquipmentLockPacket.java` | **новый** (если нужно клиентское зеркало) |
| `common/network/PjmNetworking.java` | регистрация пакета, бамп `VERSION` |
| `common/command/PjmCommands.java` | `reload` пересобирает `EquipmentRoleIndex` |
| `lang/*.json` (×5) | сообщения о запрете |

## Verification

- `./gradlew compileJava` + `./gradlew compileClientJava`.
- Валидация `items.json` с `roleLocked`/`lockMode`.
- Внутриигровая проверка пользователем: сменить роль, убедиться что (а) TACZ-ствол чужого кита выталкивается из руки; (б) право-клик предмет чужого кита не применяется; (в) предметы текущей роли работают штатно.

## Вне рамок (YAGNI)

- Автоматический роль-лок для всех предметов с `allowedRoles` (решено: только явный флаг).
- Полноценное клиентское предсказание блокировки до серверного подтверждения (начинаем с серверной авторитетности).
- Изъятие/возврат предметов на склад при смене роли (решено: блокируем, не изымаем).
