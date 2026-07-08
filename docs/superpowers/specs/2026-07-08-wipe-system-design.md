# Система вайпа — дизайн

**Дата:** 2026-07-08
**Статус:** одобрен, готов к плану реализации

## Цель

Дать OP-администратору команду для сброса сезонного **прогресса игроков** —
отдельно званий одной команды, званий всех, и полного вайпа всего прогресса —
не затрагивая **админ-разметку** карты (регионы, зоны базы, координаты зон приёма
склада, терминалы гаражей, скины, баны).

## Поверхность команд

```
/pjm wipe ranks              → сброс XP у всех игроков
/pjm wipe ranks <team>       → сброс XP у членов команды (онлайн + офлайн)
/pjm wipe all                → полный вайп прогресса игроков
```

- Все узлы требуют `permission level 4` (OP).
- Аргумент `<team>` — id команды scoreboard (по `FrontlineTeams.all()`), с
  автодополнением.
- Каждая команда требует **подтверждения** (см. «Поток подтверждения»).
- Полный вайп (`all`) стирает: звания, очки склада + личные бюджеты, машины в
  гаражах, флот, захваченные сектора фронтлайна, выбор фракции +
  командиры/замы/приказы, боевые роли.
- **Не трогается ни в одном режиме:** регионы (`RegionSavedData`), зоны базы
  (`BaseZoneSavedData`), настройки/зоны приёма склада (`WarehouseSettingsSavedData`),
  терминалы гаражей (`GarageTerminalSavedData`), скины (`SkinSavedData`),
  модерация/баны (`ModerationSavedData`), а также ключи гаражей и JSON-регистры
  (`config/pjmbasemod/...`).

## Архитектура (подход A)

Новый пакет `common/wipe/`:

- **`WipeService`** — оркестратор. Статические методы:
  - `wipeRanks(MinecraftServer server)` — сброс XP всех.
  - `wipeRanksForTeam(MinecraftServer server, String teamId)` — сброс XP членов
    команды (онлайн + офлайн).
  - `wipeAll(MinecraftServer server)` — полный вайп прогресса (включая звания
    всех).
  - `resyncAll(MinecraftServer server)` — переотправка sync-пакетов всем онлайн.
- **`WipeCommands`** (в `common/command/`) — дерево `/pjm wipe ...` по образцу
  `WarehouseCommands`/`ModerationCommands`. Держит in-memory карту ожидающих
  подтверждения вайпов. Подключается в `PjmCommands.register(...)`.

Соответствует шаблону подсистем мода: выделенный сервис + выделенный класс
команд.

## Операции вайпа по подсистемам

Каждому затрагиваемому `SavedData` добавляется метод массовой очистки, вызывающий
`setDirty()` при изменении. `WipeService` их дёргает.

| Подсистема | SavedData | Что чистим | Новый метод |
|---|---|---|---|
| Ranks | `RankSavedData` | весь `xpByPlayer` / UUID команды | `clearAll()`, `clearPlayers(Set<UUID>)` |
| Warehouse points | `WarehouseSavedData` | `stock` | `clearAll()` |
| Personal budgets | `WarehousePersonalBudgetSavedData` | `entries` | `clearAll()` |
| Garages | `GarageSavedData` | опустошить списки машин, ключи-гаражи оставить | `clearVehicles()` |
| Fleet | `VehicleFleetSavedData` | `active` + `lastSpawn` | `clearAll()` |
| Frontline | `FrontlineSavedData` | `chunks` + `sectors` → нейтраль | `clearAll()` |
| Faction selection | `FactionSelectionSavedData` | `selectionsByPlayer` | `clearAll()` |
| Faction commander | `FactionCommanderSavedData` | `commandersByTeam` | `clearAll()` |
| Faction deputy | `FactionDeputySavedData` | `deputiesByTeam` | `clearAll()` |
| Faction order | `FactionOrderSavedData` | `ordersByTeam` | `clearAll()` |
| Roles | `RoleSavedData` | `rolesByPlayer` | `clearAll()` |

### Резолв членов команды (офлайн)

Звания хранятся по UUID, членство scoreboard — по именам. Для
`wipeRanksForTeam`:

1. `server.getScoreboard().getPlayerTeam(teamId)` → `getPlayers()` (имена).
2. Для каждого имени: онлайн → берём UUID напрямую; иначе
   `server.getProfileCache().get(name)` → `GameProfile` → UUID.
3. Собираем `Set<UUID>` и передаём в `RankSavedData.clearPlayers(...)`.
4. Имена, которых нет в кэше профилей, пропускаем и логируем в чат-выводе
   команды («не найдено в кэше: N»).

## Поток подтверждения

`WipeCommands` держит `static Map<String, PendingWipe> pending`, где
`PendingWipe(String scope, @Nullable String teamId, long expiresAtMs)`.

- `sourceKey` = UUID игрока, либо `"__console__"` для консоли/командного блока.
- Первый вызов `/pjm wipe <scope> [team]`:
  - Печатает ⚠-предупреждение со списком того, что будет стёрто.
  - Кладёт `PendingWipe` с `expiresAtMs = System.currentTimeMillis() + 15_000`.
  - Просит повторить ту же команду для подтверждения.
- Повтор той же команды (тот же scope + teamId) в окне → выполняет вайп, удаляет
  запись, печатает итог.
- Просрочка, иной scope/team, или отсутствие записи → трактуется как новый первый
  вызов (перезапись предупреждением).

Окно на `System.currentTimeMillis()` (обычный серверный код, ограничение на
`Date.now()` относится только к workflow-скриптам).

## Ре-синхронизация онлайн-игроков

`WipeService.resyncAll(server)` переиспользует точки sync из
`PjmServerEvents.onLogin` для каждого онлайн-игрока:

- `RankService.syncAll(server)` (HUD званий, tab-префиксы).
- Синхронизация склада (снапшот бюджетов/очков).
- `FactionCommanderService`, `RoleService`, `FactionMenuService`,
  `FactionOrderManager.syncTo(...)` — сброшенные роли/командиры/приказы.
- `FrontlineManager` initial sync + BlueMap обновление — карта фронтлайна в
  нейтраль.

Цель: клиенты обновляются мгновенно, без релога.

## Локализация

Ключи `pjmbasemod.wipe.*` во все 5 lang-файлов
(`ru_ru`, `en_us`, `uk_ua`, `de_de`, `zh_cn`):

- `pjmbasemod.wipe.warn.ranks` / `.warn.ranks_team` / `.warn.all` — предупреждение.
- `pjmbasemod.wipe.confirm_hint` — «повторите команду для подтверждения».
- `pjmbasemod.wipe.success.ranks` / `.success.ranks_team` / `.success.all` — итог.
- `pjmbasemod.wipe.expired` — окно подтверждения истекло.
- `pjmbasemod.wipe.unknown_team` — команда не найдена.
- `pjmbasemod.wipe.team_offline_skipped` — N имён не в кэше профилей.

## Файлы

**Новые:**
- `src/main/java/ru/liko/pjmbasemod/common/wipe/WipeService.java`
- `src/main/java/ru/liko/pjmbasemod/common/command/WipeCommands.java`

**Правки:**
- `common/command/PjmCommands.java` — подключить узел `WipeCommands.build()`.
- 11 `SavedData`-классов — добавить методы массовой очистки (см. таблицу).
- 5 lang-файлов — ключи `pjmbasemod.wipe.*`.

## Верификация

- `./gradlew compileJava`
- `./gradlew compileClientJava`
- Валидация JSON всех 5 lang-файлов.
- Внутриигровую проверку (запуск вайпа, обновление HUD/карты без релога)
  выполняет пользователь из пути без `!`.

## Вне объёма (YAGNI)

- Вайп ванильного мира/инвентарей/scoreboard-членства.
- Вайп админ-разметки и JSON-регистров.
- Кнопка вайпа в веб-панели (можно добавить позже отдельной задачей).
- Персистентность «ожидающих подтверждения» между рестартами (in-memory
  достаточно).
