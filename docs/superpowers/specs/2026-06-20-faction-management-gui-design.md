# Дизайн: доработка GUI управления фракцией (командир + зам)

Дата: 2026-06-20
Подсистема: `common/faction` + `client/gui/screen/FactionManagementScreen`

## Цель

Доработать экран управления фракцией (`FactionManagementScreen`), который сейчас умеет
только назначать боевые роли членам команды. Добавить:

1. **Заместителей фракции (замы)** — несколько на фракцию (лимит из конфига), с
   настраиваемыми командиром правами.
2. **Приказ фракции** — постоянное уведомление с настраиваемым TTL, видимое на HUD
   у всех членов команды.

## Концепция ролей и прав

- **Командир** (`КМД`, уже реализован в `FactionCommanderService`) — полный доступ:
  роли, замы, приказ.
- **Зам** (новое) — несколько на фракцию. Командир галочками раздаёт права:
  - `ASSIGN_ROLES` — назначать боевые роли членам команды.
  - `SET_ORDER` — задавать/снимать приказ фракции.
  - `OPEN_GUI` — открывать экран управления (базовое право; без него остальные не имеют смысла).
- **Админ** (OP 2+, `RolePermissions.ADMIN` / `FactionPermissions.COMMANDER_ADMIN`) — как командир.
- **Управлять замами может только командир/админ.** Зам не может назначить другого зама
  и не может менять права замов.

### Матрица доступа

| Действие              | Командир | Админ | Зам                       |
|-----------------------|----------|-------|---------------------------|
| Открыть GUI           | да       | да    | если есть `OPEN_GUI`      |
| Назначать роли        | да       | да    | если есть `ASSIGN_ROLES`  |
| Задавать приказ       | да       | да    | если есть `SET_ORDER`     |
| Управлять замами      | да       | да    | нет                       |

## Данные и персистентность (`common/faction/`)

### `DeputyPermission` (enum, новый)
Значения `ASSIGN_ROLES`, `SET_ORDER`, `OPEN_GUI`. Хелперы упаковки в `int`-битмаску:
`pack(Set)`, `unpack(int)`, `has(int mask, DeputyPermission)`.

### `FactionDeputySavedData` (новый `SavedData`)
- Структура: `Map<String teamId, Map<UUID playerId, Integer perms>>`.
- Методы: `isDeputy(teamId, uuid)`, `permissions(teamId, uuid)` → int,
  `setDeputy(teamId, uuid, perms)`, `removeDeputy(teamId, uuid)`,
  `deputies(teamId)` → копия, `deputyCount(teamId)`, `deputyTeamOf(uuid)` → teamId|null,
  `clearPlayer(uuid)`.
- NBT-ключ: `pjmbasemod_faction_deputies`.

### `FactionOrderSavedData` (новый `SavedData`)
- Структура: `Map<String teamId, OrderEntry>`,
  `OrderEntry(String text, String authorName, long setAtGameTime, long expiresAtGameTime)`.
  `expiresAtGameTime == -1` → бессрочно.
- Методы: `order(teamId)`, `setOrder(teamId, entry)`, `clearOrder(teamId)`,
  `isExpired(teamId, currentGameTime)`.
- NBT-ключ: `pjmbasemod_faction_orders`.

### `FactionOrderManager` (новый сервис)
- `setOrder(actor, text, ttlMinutes)` — проверка прав, сохранение, рассылка членам
  команды (`FactionOrderSyncPacket` + разовый `NotificationPacket`).
- `clearOrder(actor)` — снять и разослать `active=false`.
- `syncTo(player)` — отправить актуальный (не истёкший) приказ при логине.
- `onServerTick(server)` — проверка истечения по `level.getGameTime()`, авто-снятие + рассылка.

### Хелперы прав в `FactionMenuService`
- `canOpenManagement(player)` — командир ∨ админ ∨ (зам ∧ `OPEN_GUI`).
- `canAssignRoles(player)` — командир ∨ админ ∨ (зам ∧ `ASSIGN_ROLES`).
- `canManageDeputies(player)` — командир ∨ админ.
- `canSetOrder(player)` — командир ∨ админ ∨ (зам ∧ `SET_ORDER`).

`openManagement` теперь использует `canOpenManagement` вместо проверки только командира/админа.

## Конфиг (новая секция `faction` в `Config.java`)

- `maxDeputies` (int, по умолчанию `3`) — макс. число замов на фракцию.
- `orderMaxLength` (int, `120`) — макс. длина текста приказа.
- `orderDefaultTtlMinutes` (int, `30`) — TTL по умолчанию в UI.
- `orderMaxTtlMinutes` (int, `240`) — потолок TTL; значение `0` в UI = бессрочно.

## Сетевой слой (бамп `PjmNetworking.VERSION`)

### Расширение `FactionManagementSnapshot`
- `MemberEntry`: добавить `boolean deputy`, `int deputyPerms`.
- Возможности зрителя: `boolean viewerCanAssignRoles`, `viewerCanManageDeputies`, `viewerCanSetOrder`.
- `int maxDeputies`, `int deputyCount`.
- Текущий приказ: `String orderText`, `String orderAuthor`, `int orderSecondsRemaining`
  (`0` = нет приказа, `-1` = бессрочно).

`FactionMenuService.managementSnapshot` заполняет новые поля. STREAM_CODEC снапшота
обновляется под новые поля (вложенные member-записи кодируются через collection-codec).

### Новые C→S пакеты (`common/network/packet/`)
- `ManageFactionDeputyPacket(UUID targetId, boolean deputy, int perms)` →
  `ServerPacketHandlers.handleManageFactionDeputy` → `FactionMenuService.handleManageDeputy`.
  Проверки: `canManageDeputies`, target в той же команде, лимит `maxDeputies` при добавлении.
- `SetFactionOrderPacket(String text, int ttlMinutes)` →
  `ServerPacketHandlers.handleSetFactionOrder` → `FactionOrderManager.setOrder`
  (пустой/blank текст = `clearOrder`). Проверка `canSetOrder`, обрезка до `orderMaxLength`,
  TTL зажимается в `[0, orderMaxTtlMinutes]`.

Обе операции после применения переотправляют актору обновлённый `FactionManagementSyncPacket`.

### Новый S→C пакет
- `FactionOrderSyncPacket(boolean active, String text, String author, int teamColor, int secondsRemaining)`
  → всем онлайн-членам команды → `ClientPacketProxy.factionOrderSync` →
  `ClientFactionOrderState.update`.

Регистрация всех новых пакетов в `PjmNetworking.onRegisterPayloads`, методы в
`ClientPacketProxy` (default noop) + реализация в `ClientPacketHandlersImpl`.

## Клиент

### `ClientFactionOrderState` (новый)
- `volatile` поля приказа + время обновления (TTL-страховка локально, по образцу
  `ClientFrontlineState`). `update(packet)`, `reset()`, `current()`.

### `FactionOrderHudOverlay` (новый)
- `LayeredDraw.Layer`, регистрация в `ClientOverlays.onRegister`
  (`registerAbove(VanillaGuiLayers.HOTBAR, ...)`).
- Рисует плашку «Приказ: <текст>» с цветом команды и остатком времени, пока
  `ClientFactionOrderState.current()` активен. Скрывается при `mc.screen != null`,
  `mc.options.hideGui`.

## GUI: `FactionManagementScreen`

Левый список членов сохраняется. У замов — бейдж `[ЗАМ]` (по образцу `[КМД]`;
у командира приоритет бейджа `[КМД]`).

В правой панели добавляется **полоса табов** сверху. Видимы только табы, доступные
зрителю (недоступные **скрываются**):

- **Роль** (виден при `viewerCanAssignRoles`) — существующая сетка ролей для выбранного
  члена. Логика без изменений.
- **Зам** (виден при `viewerCanManageDeputies`) — для выбранного члена:
  - переключатель «Назначить замом»;
  - 3 чекбокса прав (`ASSIGN_ROLES`, `SET_ORDER`, `OPEN_GUI`), активны только когда
    член — зам;
  - счётчик `Замы: N / max`; при достижении лимита назначение нового заблокировано.
  - Изменение шлёт `ManageFactionDeputyPacket`.
- **Приказ** (виден при `viewerCanSetOrder`) — не зависит от выбранного члена:
  - многострочное/однострочное поле ввода (лимит `orderMaxLength`);
  - выбор TTL (пресеты + «бессрочно»);
  - кнопки «Отправить» (`SetFactionOrderPacket`) и «Снять» (пустой текст);
  - блок «Текущий приказ»: текст, автор, остаток времени.

Если зрителю не доступен ни один таб — сервер не открывает экран и шлёт
`displayClientMessage` («нет доступа»). Активный таб по умолчанию — первый доступный.

Реализация в рамках `PjmBaseScreen` (всё в `renderScaled` / `*Scaled`-обработчиках,
виртуальные координаты). Поле ввода — ванильный `EditBox`, позиционируется в
виртуальных координатах; учесть масштаб при фокусе/клике.

## Серверные события

- `FactionOrderManager.onServerTick(server)` вызывается из `PjmServerEvents.onServerTick`
  (рядом с `FrontlineManager.onServerTick`).
- На логине: `FactionOrderManager.syncTo(player)` из `PjmServerEvents.onLogin`
  (или в существующей первичной синхронизации подсистем).
- При выходе игрока из команды/смене команды — `FactionCommanderService` уже валидирует
  командира; аналогично добавить очистку зама через `FactionDeputySavedData` при
  необходимости (валидация в tick/login, по образцу командира).

## Команды

Новых команд **не добавляем** (по решению). Существующие `/pjm faction manage` и
`/pjm debug open faction_management` продолжают работать; доступ теперь определяется
`canOpenManagement`.

## Локализация

Новые ключи во **все 5** языков (`ru_ru`, `en_us`, `uk_ua`, `de_de`, `zh_cn`):
названия табов, «Назначить замом», подписи трёх прав, плейсхолдер/подписи приказа,
пресеты TTL, бейдж `[ЗАМ]`, текст разового уведомления о приказе, плашка HUD,
сообщения об отсутствии доступа.

## Документация

- Обновить раздел `faction` в `CLAUDE.md` (замы, права, приказ, новые пакеты, бамп VERSION).
- Создать `docs/FACTION_MANAGEMENT.md` (полное описание экрана, прав, приказа, конфига).
- Добавить строку в индекс доков в `CLAUDE.md`.

## Верификация

`runClient`/`runServer` не работают из текущего пути (символ `!`). Верификация:
- `./gradlew compileJava`
- `./gradlew compileClientJava`
- валидация JSON всех 5 lang-файлов.
Внутриигровую проверку GUI выполняет пользователь из пути без `!`.

## Вне рамок (YAGNI)

- Кик/бан членов из фракции.
- Казна/ресурсы фракции.
- История действий (audit log).
- Редактирование прав боевых ролей из этого GUI.
- Поиск/фильтр членов.
