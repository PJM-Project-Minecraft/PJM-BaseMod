# Дизайн: донат-пермишены на предметы склада

Дата: 2026-07-04
Статус: одобрено, в реализации
Подсистема: `common/warehouse` + `client/gui/screen/WarehouseScreen`

## Проблема

Часть предметов склада должна выдаваться только за донат. У предмета склада уже
есть три оси ограничений (`allowedRoles`, `allowedTeams`, `minRank`); нужна
четвёртая — **пермишен-ключ доната**, проверяемый через тот же permission-бэкенд
(NeoForge `PermissionAPI` → LuckPerms), что и остальные права мода.

## Ключевые решения (утверждены с пользователем)

| Вопрос | Решение |
|---|---|
| Формат | Произвольный **ключ** `permission: "vip_pack"` → нода `pjmbasemod.warehouse.perm.vip_pack`. Один ключ у нескольких предметов = общая нода («пакет доната»). |
| GUI | Недоступный донат-предмет **виден всем** с иконкой замка и подписью «Доступно за донат» (реклама), не скрывается. |
| Fallback без LuckPerms | **Только OP** (`hasPermissions(2)`) — безопасный дефолт, как у прочих Permissions мода. |

## Компоненты

### 1. Модель — `WarehouseItemDefinition`

Новое поле `private String permission;`
- `permission()` → `""` если не задан; `donateRestricted()` → `permission != null && !permission.isBlank()`.
- В `normalize()`: `trim().toLowerCase(ROOT)`, санитайз символов (латиница/цифры/`_`/`-`),
  пустой результат → поле сбрасывается в `null` (ограничение снято).

### 2. Проверка — новый `common/warehouse/WarehouseDonorPermissions`

Ноды **динамические** (ключи из JSON неизвестны на момент `PermissionGatherEvent.Nodes`,
который происходит до загрузки `items.json` в `ServerStartedEvent`), поэтому статически
не регистрируются — создаются и кешируются по ключу при первой проверке.

```java
public static boolean canAccess(ServerPlayer player, WarehouseItemDefinition def) {
    if (!def.donateRestricted()) return true;      // не донатный — всем
    if (player == null) return false;
    if (!PermissionReady.isReady(player)) return player.hasPermissions(2); // до логина
    PermissionNode<Boolean> node = nodeFor(def.permission()); // кеш ConcurrentHashMap
    return Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
}
```

- Нода: `new PermissionNode<>(MODID, "warehouse.perm." + key, BOOLEAN, resolver)`,
  где `resolver = (p, uuid, ctx) -> p != null && p.hasPermissions(2)`.
- Fallback «только OP» без LuckPerms получается автоматически: `DefaultPermissionHandler`
  вызывает resolver ноды (OP-check). Сетевой регистрации нод не требуется — LuckPerms
  backend мапит право по имени ноды.

### 3. Серверная логика — `WarehouseManager`

- `handleWithdraw`: после проверки `minRank` добавить
  `if (!WarehouseDonorPermissions.canAccess(player, def))` → сообщение
  `gui.pjmbasemod.warehouse.donate_restricted`, `return`.
- `buildSnapshot`: вычислить `boolean donateAllowed = WarehouseDonorPermissions.canAccess(player, def)`,
  передать в `ItemEntry`. В отличие от `teamAllows` (скрывает предмет через `continue`),
  донат **не скрывается** — только флаг.

### 4. Сеть — `WarehouseSnapshot.ItemEntry`

Одно новое поле `boolean donateAllowed`. Обновить ручные `write`/`read` в
`WarehouseSnapshot`. **Бамп `PjmNetworking.VERSION` "12" → "13"** (меняется состав снапшота).

### 5. GUI — `WarehouseScreen`

Третья причина блокировки после роли/ранга (тот же механизм `locked`):
- `donateLocked = item.roleAllowed() && item.rankAllowed() && !item.donateAllowed()`.
- `locked = roleLocked || rankLocked || donateLocked` → замок + серый текст.
- Строка причины (когда `donateLocked`): `gui.pjmbasemod.donate.required`.
- Кнопка «Получить» disabled при `!donateAllowed`; текст `gui.pjmbasemod.donate.locked_short`.
- Обновить гейты доступности: `canWithdrawItem` (~155), hover/purchase (~350), `withdrawEnabled` (~531).

### 6. Локализация ×5 (`ru_ru`, `en_us`, `uk_ua`, `de_de`, `zh_cn`)

- `gui.pjmbasemod.warehouse.donate_restricted` — отказ при выдаче.
- `gui.pjmbasemod.donate.required` — строка причины в списке.
- `gui.pjmbasemod.donate.locked_short` — на кнопке.

### 7. Доки

`docs/WAREHOUSE.md` — строка `permission` в таблице полей `items.json`.

## Затронутые/новые файлы

| Файл | Изменение |
|---|---|
| `common/warehouse/WarehouseDonorPermissions.java` | **новый** — динамическая проверка ноды |
| `common/warehouse/WarehouseItemDefinition.java` | поле `permission` + геттеры + `normalize()` |
| `common/warehouse/WarehouseManager.java` | проверка в `handleWithdraw` + флаг в `buildSnapshot` |
| `common/warehouse/WarehouseSnapshot.java` | поле `donateAllowed` в `ItemEntry` + write/read |
| `common/network/PjmNetworking.java` | бамп `VERSION` |
| `client/gui/screen/WarehouseScreen.java` | замок/строка/кнопка доната |
| `lang/*.json` (×5) | 3 новых ключа |
| `docs/WAREHOUSE.md` | строка `permission` |

## Verification

- `./gradlew compileJava` + `./gradlew compileClientJava` — компиляция common и client.
- Валидация JSON (`items.json`, 5 lang-файлов).
- Внутриигровую проверку (выдача ноды `pjmbasemod.warehouse.perm.<key>` через LuckPerms,
  замок в GUI, отказ при выдаче) выполняет пользователь из пути без `!`.

## Вне рамок (YAGNI)

- Живое отслеживание смены прав LuckPerms без релога.
- Парсинг `permission=<key>` в команде `/pjm warehouse additem` (задаётся правкой JSON).
- Внутримодовая выдача/магазин нод (это делает донат-плагин/LuckPerms).
