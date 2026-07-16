# Управление фракцией (командир и заместитель)

Экран `FactionManagementScreen` — интерфейс для командира фракции и назначенных им
заместителей. Документ описывает реально реализованное в коде.

## Доступ к экрану

Экран открывается командой `/pjm faction manage` (себе) или debug-командой
`/pjm debug open faction_management [player]` (OP 2+). Сервер открывает экран только
если у игрока есть право `OPEN_GUI` в его текущей команде:

| Кто | Условие доступа |
|-----|-----------------|
| Командир | назначен командиром своей текущей команды (`FactionCommanderService`) |
| Админ | OP 2+ (`RolePermissions.ADMIN`) |
| Заместитель | назначен замом и имеет право `OPEN_GUI` |

Если ни одно условие не выполнено — сообщение `gui.pjmbasemod.faction.manage.no_access`.

Права зрителя вычисляются в `FactionMenuService.authority(player)` → record `Authority`
(`canOpen`, `canAssignRoles`, `canSetOrder`, `canManageDeputies`).

## Вкладки

В правой панели — вкладки, видимые **только по правам зрителя** (недоступные скрыты):

- **Роль** (`ASSIGN_ROLES`) — назначение/снятие боевой роли выбранному члену с учётом лимитов.
- **Зам** (только командир/админ) — назначение заместителей и их прав.
- **Приказ** (`SET_ORDER`) — установка/снятие приказа фракции.

В списке членов: бейдж `[КМД]` у командира, `[ЗАМ]` у заместителя.

## Список членов: оффлайн и поиск

Список строится по **scoreboard-команде** (`Teams.memberNames`), а не по онлайну, поэтому
показывает и вышедших игроков. UUID берётся у живого игрока, иначе из кэша профилей
(`server.getProfileCache()`); запись без профиля (в scoreboard-команду можно добавить и
не-игрока) пропускается. Роль оффлайн-игрока читается из `RoleSavedData`
(`RoleService.storedRoleId`) — живую команду у него не спросить.

- Точка справа в строке: зелёная — онлайн, серая — оффлайн. Сортировка: командир → замы →
  онлайн → по имени.
- **Роль и статус зама выдаются оффлайн-игрокам наравне с онлайн**: и то, и другое живёт в
  `SavedData`. Лимиты ролей уже считались по сохранённым записям (`RoleService.roleCount`),
  поэтому оффлайн-цель занимает слот так же. Онлайн-игрок нужен только для sync-пакета,
  уведомления и тега в TAB — оффлайн-цель получит их при входе.
- Назначение по UUID: `RoleService.assignRoleTo(actor, server, targetId, name, team, role)`.
  Команда оффлайн-цели — `Teams.resolveTeamIdByName` (scoreboard хранит членство по имени).
- Над списком — поле поиска по нику (`gui.pjmbasemod.faction.manage.search.*`), фильтр
  клиентский, регистронезависимый.

## Заместители

- Несколько на фракцию, лимит — `faction.maxDeputies` (по умолчанию 3).
- Управлять замами вправе **только командир и админ** (зам не может назначить другого зама).
- Права раздаются галочками (enum `DeputyPermission`, битмаска):
  - `ASSIGN_ROLES` — назначать боевые роли;
  - `SET_ORDER` — задавать/снимать приказ;
  - `OPEN_GUI` — открывать этот экран.
- Персистентность: `FactionDeputySavedData` (teamId → playerId → битмаска), NBT-ключ
  `pjmbasemod_faction_deputies`.
- При выходе игрока из своей команды его статус зама автоматически снимается
  (`FactionMenuService.onPlayerTick`).
- Пакет: `ManageFactionDeputyPacket(targetId, deputy, perms)` (C→S).

## Приказ фракции

- Командир/зам (`SET_ORDER`) задаёт текст приказа и срок (TTL).
- TTL-пресеты в UI: бессрочно, 15, 30, 60, 120, 240 мин. Серверный потолок —
  `faction.orderMaxTtlMinutes` (по умолчанию 240); `0` = бессрочно.
- Текст обрезается до `faction.orderMaxLength` (по умолчанию 120 символов).
- При установке: всем онлайн-членам команды приходит **разовое уведомление**
  (`NotificationPacket`) и обновляется **постоянная плашка на HUD**.
- Плашка висит у всех членов команды до истечения TTL или снятия; новые игроки
  получают актуальный приказ при входе (`FactionOrderManager.syncTo`).
- Истечение проверяется в серверном тике (`FactionOrderManager.onServerTick`, раз в ~1 с).
- Персистентность: `FactionOrderSavedData` (teamId → `OrderEntry`), NBT-ключ
  `pjmbasemod_faction_orders`; `expiresAtGameTime == -1` → бессрочно.
- Клиент: зеркало `ClientFactionOrderState`, overlay `FactionOrderHudOverlay`.
- Пакеты: `SetFactionOrderPacket(text, ttlMinutes)` (C→S; пустой текст = снять);
  `FactionOrderSyncPacket(active, text, author, teamColor, secondsRemaining)` (S→C).

## Конфиг (секция `faction` в `config/pjmbasemod/config.json`)

| Ключ | По умолчанию | Описание |
|------|--------------|----------|
| `maxDeputies` | 3 | макс. число заместителей на фракцию |
| `orderMaxLength` | 120 | макс. длина текста приказа |
| `orderDefaultTtlMinutes` | 30 | TTL по умолчанию |
| `orderMaxTtlMinutes` | 240 | потолок TTL (`0` = бессрочно) |

## Классы

| Класс | Назначение |
|-------|-----------|
| `DeputyPermission` | enum прав зама (битмаска) |
| `FactionDeputySavedData` | персистентность замов |
| `FactionOrderSavedData` | персистентность приказа |
| `FactionOrderManager` | установка/снятие/рассылка приказа, истечение |
| `FactionMenuService.Authority` | права зрителя |
| `FactionManagementSnapshot` | данные для GUI (члены, роли, права, приказ) |
| `ClientFactionOrderState` / `FactionOrderHudOverlay` | клиентские зеркало и HUD приказа |
| `FactionManagementScreen` | экран с вкладками Роль/Зам/Приказ |
