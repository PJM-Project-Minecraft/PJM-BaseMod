# Дизайн: «Доступы» вместо донат-ролей

Дата: 2026-07-07
Статус: одобрен к реализации

## Проблема

Донатные привилегии сейчас реализованы как **боевые роли** `UAV_OPERATOR` (Оператор
БПЛА) и `SSO` (ССО), помеченные `paid` в `config/pjmbasemod/roles/access.json` и
закрытые permission-нодой `pjmbasemod.role.unlock.<id>`. Это неверно по смыслу:

- Донат занимает единственный слот боевой роли — донатер не может быть одновременно,
  например, штурмовиком и иметь доступ к БПЛА.
- «Платная роль» смешивает тактическую роль (назначается командиром) и донат-привилегию
  (покупается/выдаётся навсегда).
- Ради платных ролей существует целая машинерия само-выдачи роли в radial-меню, которая
  работает **только** для платных ролей.

## Цель

Развести понятия. Донат перестаёт быть ролью и становится **Доступом** — постоянной
именованной привилегией (напр. «Доступ к БПЛА»), которая:

- **независима** от боевой роли: у игрока одна боевая роль ПЛЮС любое число Доступов;
- **гейтит предметы склада** (донатный БПЛА-шмот выдаётся только при наличии Доступа);
- **выдаётся permission-нодой** `pjmbasemod.access.<id>` (донат-плагин / LuckPerms),
  как и прочие донат-привилегии мода.

Боевые роли после переделки назначает только командир / заместитель (с правом
`ASSIGN_ROLES`) / админ. Само-выдача ролей игроком удаляется вместе с платными ролями.

## Подход (выбран: A — enum `AccessType`)

Новая лёгкая подсистема-первокласс, зеркалящая существующие паттерны мода
(`CombatRole` + статические ноды `RolePermissions`). Без отдельного JSON-реестра и без
динамических нод.

### 1. Новая подсистема `common/access/`

**`AccessType`** — enum по образцу `CombatRole`:

- `UAV("uav", "Доступ к БПЛА", 0x4CC4D8, aliases: drone, bpla, бпла, оператор бпла)`
- `SSO("sso", "Доступ ССО", 0x8D4CD8, aliases: sof, special_forces, ссо)`
- Поля/методы: `id()`, `displayName()`, `color()`, `aliases`, `translationKey()` →
  `access.pjmbasemod.<id>`, статические `byIdOrAlias(String)`, `values()`.

**`AccessPermissions`** — по образцу `RolePermissions`:

- `@EventBusSubscriber(modid = Pjmbasemod.MODID)`.
- Статические ноды `pjmbasemod.access.<id>` для каждого `AccessType`, дефолтный
  resolver — `player.hasPermissions(2)` (OP 2+), регистрация в
  `PermissionGatherEvent.Nodes`.
- `has(ServerPlayer, AccessType)` — единственный гейт; учитывает `PermissionReady`
  (до `PlayerLoggedInEvent` откат на ванильный OP), как в `RolePermissions.can`.
- `has(ServerPlayer, String id)` — удобная перегрузка через `byIdOrAlias`.

Статические ноды предпочтительнее ленивых (как в `WarehouseDonorPermissions`), потому
что набор Доступов известен в коде на этапе `PermissionGatherEvent`.

### 2. Интеграция со складом

- `WarehouseItemDefinition`: новое поле `access` (String id) + `accessRestricted()`
  (`access != null && !isBlank`) + геттер/сеттер. Отдельно от существующего `permission`
  (тот остаётся генерик-донат-гейтом `warehouse.perm.<key>`).
- Путь выдачи предмета в `WarehouseManager` (рядом с существующими проверками
  `donateRestricted` → `WarehouseDonorPermissions.canAccess`, `roleRestricted`,
  `rankRestricted`, `teamRestricted`): если `accessRestricted()` → требовать
  `AccessPermissions.has(player, def.access())`; иначе — отказ с сообщением о
  необходимости Доступа.
- `WarehouseSnapshot` / клиентский экран: если снапшот несёт причину блокировки
  предмета для GUI — добавить причину «нужен Доступ», по аналогии с донат/ролью
  (уточнить при реализации, изменить симметрично существующим lock-причинам).
- БПЛА-предметы в конфиге переезжают с `allowedRoles: ["uav_operator"]` на
  `access: "uav"`. Пример в `WarehouseItemRegistry` (стандартный `items.json`)
  обновляется: элемент с `allowedRoles: ["assault","sso"]` теряет `sso`; при
  необходимости добавляется пример `access`.

### 3. Удаление UAV_OPERATOR и SSO из ролей

- `CombatRole`: удалить константы `UAV_OPERATOR` и `SSO` (останутся `assault`,
  `machine_gunner`, `sniper`, `marksman`, `ew_specialist`, `crew`).
- `RadialMenuScreen`: убрать иконку `case UAV_OPERATOR -> COMPASS` (и SSO, если есть).
- `PjmCommands`: обновить текст ошибки и парсинг ролей (строка со списком
  `... uav_operator, sso ...`).
- Лимиты ролей `config/pjmbasemod/roles/*.json` и `RoleLimitRegistry`: если ссылаются
  на удалённые id — почистить (конфиг рантайма; отметить в плане как ручной шаг/пример).

### 4. Полный рип-аут «paid-роль» машинерии

**Удалить файлы:**

- `common/role/RoleAccessRegistry.java`
- `common/network/packet/RoleAccessSyncPacket.java`
- `common/network/packet/RequestTargetRoleAccessPacket.java`
- `common/network/packet/TargetRoleAccessPacket.java`

**Отредактировать:**

- `common/role/RolePermissions.java` — удалить `UNLOCK_NODES`, `canUseRole`, ноды
  `role.unlock.<id>` и их регистрацию; оставить только `ADMIN`.
- `common/role/RoleService.java` — удалить ветку `selfAssignPaid`, вызовы
  `RolePermissions.canUseRole`, отправку `RoleAccessSyncPacket`, методы
  `selfAssignableRoleIds` / `assignableRoleIdsFor` (self-assign paid), связанные импорты.
  Гейт назначения роли остаётся: ADMIN | командир целевой команды | заместитель с
  `ASSIGN_ROLES` (см. недавний фикс `isRoleDeputy`).
- `common/network/PjmNetworking.java` — снять регистрацию 3 пакетов; **бамп
  `VERSION` "24" → "25"**.
- `common/network/ClientPacketProxy.java` — удалить `roleAccessSync` / `targetRoleAccess`.
- `common/network/handler/ServerPacketHandlers.java` — удалить
  `handleRequestTargetRoleAccess` + импорты.
- `common/event/PjmServerEvents.java` — удалить `RoleAccessRegistry.get().reload()` + импорт.
- `common/command/PjmCommands.java` — удалить reload access-конфига ролей.
- `client/role/ClientRoleState.java` — удалить `updateAccess` / `updateTargetAccess`,
  соответствующие поля/импорты.
- `client/network/ClientPacketHandlersImpl.java` — удалить оба override + импорты.
- `client/gui/RadialMenuScreen.java` — удалить отправку `RequestTargetRoleAccessPacket`,
  `onTargetRoleAccessUpdated`, отображение платных/недоступных ролей и цели-доступа.

### 5. Локализация (5 языков)

`ru_ru`, `en_us`, `uk_ua`, `de_de`, `zh_cn`:

- Удалить ключи `role.pjmbasemod.uav_operator`, `role.pjmbasemod.sso`.
- Добавить `access.pjmbasemod.uav`, `access.pjmbasemod.sso`.
- Добавить строку-сообщение склада о необходимости Доступа (напр.
  `gui.pjmbasemod.warehouse.access_required`).

## Миграция / операционные заметки (для админа сервера)

- Донат-гранты: `pjmbasemod.role.unlock.uav_operator` → `pjmbasemod.access.uav`;
  `pjmbasemod.role.unlock.sso` → `pjmbasemod.access.sso`.
- `items.json`: донатные БПЛА/ССО-предметы перевести с `allowedRoles` на `access`.
- `config/pjmbasemod/roles/access.json` устаревает (не читается; можно удалить).
- Игроки, у кого в `RoleSavedData` сейчас сохранена роль `uav_operator`/`sso`: при
  загрузке неизвестный id должен безопасно очищаться. **Проверить при реализации**, что
  `RoleSavedData` игнорирует неизвестные роли без краша; при необходимости — миграция.

## Вне объёма (YAGNI)

- Метка Доступов в HUD/табе (можно добавить позже — задел заложен именем понятия).
- Выдача Доступом предмета в руки / кита.
- Выдача Доступа командой `/pjm access` и хранение в SavedData (только permission-нода).
- Конфиг-реестр Доступов (подход B) — набор фиксирован в enum.

## Верификация

- `./gradlew compileJava` + `./gradlew compileClientJava`.
- Валидация JSON пяти lang-файлов и примера `items.json`.
- Внутриигровую проверку (донат видит БПЛА-шмот на складе; не-донат — нет; боевые роли
  назначает только командир/зам) выполняет пользователь из пути без `!`.
