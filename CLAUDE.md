# CLAUDE.md

Инструкции для Claude Code при работе в этом репозитории.

## Project

Ты Senior разработчик, PJM BaseMod (`pjmbasemod`, group `ru.liko`) — тактический PvP-мод для **Minecraft 1.21.1 / NeoForge 21.1.172 / Java 21**. Базовый код-нейм `WRB-BaseMod`. Основной язык общения и комментариев — **русский**.

## Build & verify commands

```bash
./gradlew compileJava        # сборка common (src/main)
./gradlew compileClientJava  # сборка client (src/client)
./gradlew build              # полная сборка трёх JAR + datagen
./gradlew runData            # datagen → src/generated/resources (вливается обратно в main)
```

**`runClient` / `runServer` НЕ РАБОТАЮТ из этого каталога.** Путь содержит `!Curseforge Mods`, символ `!` ломает dev-лаунчер NeoForge на bootstrap: `IllegalArgumentException: Invalid package name: '!MSD'`. Ограничение пути, не кода. **Верификация — `compileJava` + `compileClientJava` + валидация JSON.** Внутриигровую проверку (GUI, телепорт, кастомные дименшены) выполняет пользователь из пути без `!`.

## Source set architecture (критично)

Три source set'а → три JAR:

| Source set | Путь | Назначение |
|------------|------|------------|
| `common` | `src/main` | Сервер-логика, сеть, данные, регистры |
| `client` | `src/client` | GUI, рендер, ввод — зависит от `main` |
| `server` | `src/server` | Серверная точка входа |

**Жёсткое правило:** `client`/`server` могут импортировать `main`, но **`main` НИКОГДА не импортирует `client`** (ломается dedicated-server JAR).

Развязка через **прокси**: `PjmNetworking.CLIENT` — интерфейс `ClientPacketProxy` (по умолчанию `NOOP`). `ClientInit.onClientSetup` подменяет его на `ClientPacketHandlersImpl`. Весь S→C код в common вызывает `CLIENT.someMethod(packet)`, не зная о клиентских классах.

## Точки входа

- `ru.liko.pjmbasemod.Pjmbasemod` (`@Mod`, MODID `pjmbasemod`) — регистрирует `DeferredRegister`-ы (`PjmItems`, `PjmEntities`, `PjmSounds` в `common/init/`), сетевые пакеты, config.
- `client/ClientInit` (`@EventBusSubscriber Dist.CLIENT`) — ставит клиентский прокси, регистрирует рендеры сущностей, кейбайндинги.
- `server/ServerInit` — серверная точка входа.
- `Config.java` (корень пакета, не в `common/init/`) — NeoForge Config с секциями: `general`, `hud`, `teams` (TEAM_JOIN_COMMANDS), `garage`, `fleet`, `faction`, `antigrief`, `moderation`, `web`, `logging`, `baseZone`, `commands`, `events`, `capturePoints` (в т.ч. пассивный доход складов), `weapons` (лимит переноса: 1 основной + 1 вторичный ствол TACZ, вторичка задаётся `secondaryGunTypes`).

## Сетевой слой

`common/network/PjmNetworking` регистрирует все пакеты. **`VERSION = "12"` — бампать при изменении состава пакетов.** Пакеты — records в `common/network/packet/` со `STREAM_CODEC`.

- **C→S**: обрабатываются в `common/network/handler/ServerPacketHandlers` или напрямую в менеджерах (`GarageManager`, `WarehouseManager`).
- **S→C**: маршрутизируется через `ClientPacketProxy` → `client/network/ClientPacketHandlersImpl`.

Добавляя пакет: создать record-payload → зарегистрировать в `PjmNetworking` → (для S→C) добавить метод в `ClientPacketProxy` + реализацию.

## События

Всё через `@EventBusSubscriber`. Главный server-side хаб — `common/event/PjmServerEvents`:

- `onLogin` — рассылает первичную синхронизацию подсистем, вызывает `*.onPlayerLogin`.
- `onPlayerTick` (`PlayerTickEvent.Post`) — тик-сервисы: `FactionCommanderService`, `RoleService`, `LobbyService`, `FactionMenuService`. **Отложенные действия (телепорт при входе) — здесь, не в `onLogin`**: на логине entity ещё не полностью заспавнен.
- `onServerTick` (`ServerTickEvent.Post`) — `FactionOrderManager`, `ModerationService`, `VehicleFleetManager`, `ServerEventManager`.
- `onServerStarted` — `reload()` датапак-регистров + startup-команды из конфига, `RankService.onServerStarted`.

## Подсистемы (`common/<package>/`)

### faction
`FactionSelectionSavedData`, `FactionSelectionSnapshot` — выбор фракции (scoreboard-команда).
`FactionMenuService` — управляет открытием GUI выбора фракции.
`FactionCommanderService`, `FactionCommanderSavedData` — роль командира.
`FactionJoinActions` — действия при вступлении в фракцию.
`FactionPermissions`, `FactionManagementSnapshot` — права и синхронизация.
`FactionDeputySavedData` + enum `DeputyPermission` (ASSIGN_ROLES/SET_ORDER/OPEN_GUI/INVITE) — заместители фракции (teamId → playerId → битмаска), лимит `faction.maxDeputies`.
`FactionInviteSavedData` — приглашения в закрытые фракции (`teams.inviteOnly` в конфиге): teamId → (ник → срок, TTL `faction.inviteTtlMinutes`). Выдаёт командир/зам с правом INVITE/админ — вкладка «Приглашения» в GUI управления или `/pjm faction invite|uninvite|invites` (OP может указать фракцию явно). Закрытая фракция на экране выбора рисуется с замком; серверная проверка — в `FactionMenuService.handleSelection`, приглашение одноразовое. Помимо приглашений — постоянный whitelist в конфиге (`teams.whitelist`: id фракции → список ников): вписанные игроки вступают без приглашения (переживает вайп кампании).
`FactionOrderSavedData`, `FactionOrderManager` — приказ фракции (текст + TTL): разовое уведомление + постоянная HUD-плашка у всех членов команды. Клиент: `ClientFactionOrderState`, `FactionOrderHudOverlay`.
`FactionMenuService.Authority` — права зрителя. Конфиг-секция `faction` (maxDeputies, orderMaxLength, orderDefaultTtlMinutes, orderMaxTtlMinutes). Экран: вкладки Роль/Зам/Приказ, недоступные скрыты; список членов строится по scoreboard-команде и включает **оффлайн**-игроков (роль и зам выдаются им наравне с онлайн), сверху — поиск по нику — [`docs/FACTION_MANAGEMENT.md`](./docs/FACTION_MANAGEMENT.md).

### role
`RoleService`, `RoleSavedData` — боевая роль игрока (enum `CombatRole`).
`RoleLimitRegistry` — JSON-лимиты ролей из `config/pjmbasemod/roles/`.
`RolePermissions` — проверки прав роли.

### rank
`RankService`, `RankSavedData`, `RankRegistry`, `RankDefinition`, `RankConfig`, `RankSnapshot` — система XP и ранга. JSON-конфиг из `config/pjmbasemod/ranks/`. Синхронизируется пакетами `RankSyncPacket` / `RankXpPacket`.

### garage
`GarageManager`, `GarageSavedData`, `GarageTerminalSavedData`, `GarageTerminalSettings` — управление гаражами.
`VehicleRegistry`, `VehicleDefinition`, `StoredVehicle`, `CostEntry` — JSON-реестр техники.
`GaragePermissions`, `GarageSnapshot`, `GarageType` — права и снапшот для GUI.
`GarageType`: `GROUND` / `AVIATION` — два типа гаражей с раздельными слотами.
`NotebookItem` — предмет, размещающий терминал-«ноутбук» (`NotebookEntity`, GeckoLib).
Клиентский экран: `client/gui/screen/GarageScreen`.

### warehouse
`WarehouseManager`, `WarehouseSavedData`, `WarehouseSettingsSavedData`, `WarehouseSettings`, `WarehouseSnapshot` — склад очков.
`WarehouseItemRegistry`, `WarehouseItemDefinition`, `CrateRegistry`, `CrateDefinition` — JSON-каталоги.
`WarehousePersonalBudgetSavedData` — личный лимит очков на игрока (анти-«пылесос», секция `warehouse` в конфиге).
`WarehousePoolCategory` — 2 пула: SUPPLY (припасы) / RAW (сырьё). Старые weapon/equipment/special схлопнуты в SUPPLY (алиасы в `byId`, суммирование в `WarehouseSavedData.load`).
`WarehousePermissions` — права.
`QuartermasterEntity` — NPC-кладовщик (`Mob` без ИИ, рендер `QuartermasterRenderer` через ванильную `PlayerModel`).
Ящики: `SupplyCrateItem` × 5 (weapon_crate/supply_crate/equipment_crate/raw_crate/special_crate).
Клиентский экран: `client/gui/screen/WarehouseScreen`.
Полная документация подсистемы (флаги конфига, `items.json`, `crates/`, команды, NPC, TACZ) — [`docs/WAREHOUSE.md`](./docs/WAREHOUSE.md).

### inventory
`InventoryLimitService`, `InventoryLimitRegistry`, `InventoryLimitConfig` — блокировка слотов инвентаря.
`SlotMixin` — инжект в `Slot.mayPickup` / `Slot.mayPlace`, запрещает взаимодействие с заблокированными слотами.
Синхронизируется `LockedSlotsPacket` (S→C). Клиентское зеркало: `client/inventory/LockedSlotsClientState`.

### teams
`Teams` (пакет `common/teams/`) — резолв боевых команд (фракций) через ванильный **scoreboard**:
- `Teams.resolvePlayerTeamId(ServerPlayer)` → id команды или null
- `Teams.all()` → `List<Config.ConfiguredTeam>` с `.id()`
- `Teams.displayName/color/normalize/isCombatTeam` — утилиты для отображения команд.
Ранее назывался `FrontlineTeams` и жил в пакете `common/frontline/`; система захвата линии фронта (`FrontlineManager`/`SavedData`/`Chunk`/`Sector`/`BlueMap`/`JourneyMap`/`HudOverlay`) и система регионов (`RegionManager`/`SavedData`) **удалены** — заменяются подсистемой точек захвата (`common/capturepoint/`, в разработке).

### campaign
`CampaignSavedData`, `CampaignManager` — недельная кампания поверх точек захвата:
удерживаемые точки тикают VP фракции раз в `campaign.vpIntervalMinutes` (реальное время,
и вне окон захвата). Через `campaign.durationDays` — победитель (максимум VP, строго больше
второго) и **полный вайп сезона**: `WipeService.wipeAll` + точки в нейтраль
(`CapturePointSavedData.resetForNewSeason`, базовые — крайние по order — сохраняют владельца,
иначе sequential-гейт заблокирует новый раунд) + все игроки снимаются со scoreboard-команд +
удаление техники SBW и предметов на карте (`campaign.wipeClearEntities`). XP-бонус победителям
(`campaign.winnerXpBonus`) начисляется ПОСЛЕ вайпа — фора нового сезона. HUD-счёт вверху по
центру: `CampaignSyncPacket` → `ClientCampaignState` → `CampaignHudOverlay`. Команды
`/pjm campaign status|on|off|restart|finish confirm`. Пассивный доход складов с точек —
в `CapturePointManager.tickIncome`: `capturePoints.incomePerPoint` очков SUPPLY за точку раз в
`incomeIntervalMinutes`, склад команды — `/pjm capturepoint warehouse <фракция> <склад>`.

### basezone
`BaseZone`, `BaseZoneSavedData`, `BaseZoneManager` — зоны базы (блочный AABB с Y +
owner-команда). Враг в чужой зоне получает полноэкранное предупреждение с отсчётом
и погибает от кастомного `DamageType` `pjmbasemod:base_zone`, если не вышел. OP и
creative/spectator игнорируют защиту. Конфиг-секция `baseZone` (enabled,
countdownSeconds). Команды `/pjm basezone`. Enforcement — серверный, в
`PjmServerEvents.onPlayerTick`. Документация — [`docs/BASE_ZONE.md`](./docs/BASE_ZONE.md).

### radiospawn
`RadioSpawnManager` — спавн на радейках (как Radio Backpack в Arma Reforger): живой сокомандник
с надетым `warbornrenewed:backpack-ussr-radio` в curio-слоте «back» — мобильная точка возрождения
фракции. Слоты (`MAX_ACTIVE = 3` на фракцию) занимают онлайн-носители в порядке появления,
пересчёт раз в секунду в `onServerTick`; вытесненные видят статус в actionbar. При смерти уходит
`RadioSpawnListPacket` со списком носителей, выбор возвращается `RadioSpawnSelectPacket` и
применяется телепортом в `PlayerRespawnEvent`; носитель получает `CARRIER_XP = 50`.
После возрождения рация уходит на перезарядку `SPAWN_COOLDOWN_SECONDS = 20` секунд (карта
`COOLDOWNS`, проверка авторитетно серверная) — на экране смерти такая рация остаётся в списке
серой кнопкой с локальным отсчётом. Состояние **не персистентно**: живёт в памяти, слоты
восстанавливаются по факту онлайна. Curios — мягкая зависимость, без мода спавн просто не работает.

### vanish
`VanishService` — ваниш админа: игрок пропадает из TAB и из мира для всех, кроме себя и других
админов (`hasPermissions(2)`). Админы видят ванишнутых в мире и в TAB с приставкой `[V]`
(через `PlayerEvent.TabListNameFormat` + `refreshTabListName`), поэтому к ним работает обычный `/tp`.
Сущность скрывается через `ServerPlayerMixin` (`broadcastToPlayer` → `false`, `ChunkMap`
снимает парность сам); TAB — рассылкой `ClientboundPlayerInfoRemovePacket`. Состояние —
сессионный набор + флаг в `PERSISTED_NBT_TAG` (переживает релог). Команда `/pjm vanish [цель]`
(алиас `/vanish`).

### dimension
`PjmDimensions`, `LobbyService` — кастомные дименшены через datapack JSON.
Пара файлов: `data/pjmbasemod/dimension/<name>.json` + `data/pjmbasemod/dimension_type/<name>.json`.
В Java: `ResourceKey<Level>` → `server.getLevel(key)`.
Платформы/структуры ставятся кодом (`LobbyService.ensurePlatform`), не генератором.

### chat
`ChatService`, `ChatMode` — режимы чата.
Клиент: `client/chat/ClientChatModeState`.

### voice / radio (client-only)
`common/voice/` — `PjmVoiceChatPlugin`, `VoicechatBridge` (Simple Voice Chat integration, `compileOnly`).
`client/radio/` — `RadioManager`, `PjmVoiceChatClientPlugin`, `RadioStaticSoundInstance`, `VoiceChatBridge`, `VoiceChatActionBarHud`.
Сервер: `RadioAudioProcessor` (`common/audio/`), пакеты `RadioEventPacket`, `RadioSwitchPacket`.

### customization
`CustomizationManager`, `CustomizationOption`, `CustomizationType` — кастомизация (скины/цвета техники).

### compat
`SbwVehicleClassifier` — SuperBWarfare определение типа техники.
`TaczWarehouseCompat`, `TaczWarehouseIntegration` — интеграция TACZ с системой склада.
`client/compat/WarBornGuardCompat` — клиентская совместимость WarBorn Guard.

### web
`WebPanelService` — встроенная веб-панель админа (Javalin 6 через jarJar, порт из `web.port`).
`WebAuthService`, `WebSessions`, `LoginCodes`, `RateLimiter` — вход через `/pjm web login` (одноразовый код → сессия-cookie).
`metrics/MetricsCollector`, `metrics/MetricsHistory` — TPS/MSPT/heap/онлайн раз в секунду в ring buffer.
`metrics/EntityProfiler`, `metrics/ProfilerWindow` — замер тика entity (EntityTickEvent.Pre/Post), тумблер из панели.
`WebState` — volatile-снапшоты для HTTP-потоков (инвариант: веб-потоки НЕ трогают игровое состояние).
`WebActions` — действия (кик/наказания/телепорт/удаление entity) через `server.execute()`.
`api/WebApiRoutes`, `api/WebSocketHub` — REST + WS-пуш. Фронтенд: `web/` (React+Vite), dist коммитится в `src/main/resources/web/`.
Документация: [`docs/WEBPANEL.md`](./docs/WEBPANEL.md).

## Клиентские state-классы (зеркала)

Каждая подсистема имеет клиентский singleton-state в `src/client/.../client/<subsystem>/`:
`ClientFactionCommanderState`, `ClientFrontlineState`, `ClientRankState`, `ClientRoleState`,
`ClientRegionState`, `LockedSlotsClientState`, `ClientChatModeState`.
Обновляются из `ClientPacketHandlersImpl` при получении sync-пакетов.

## Клиентские GUI

**Экраны** (`client/gui/screen/`): `FactionSelectionScreen`, `FactionManagementScreen`, `GarageScreen`, `WarehouseScreen`, `TacticalMainMenuScreen`, `RadialMenuScreen` (радиальное меню выбора).
GUI — кастомные `Screen` **без `AbstractContainerMenu`**.
Полноэкранные меню наследуют **`PjmBaseScreen`** (`client/gui/screen/`): автоматическое масштабирование панели через `PoseStack`, чтобы не вылезала за края при любом разрешении. Рисуй в `renderScaled()`, мышь — в `*Scaled`-методах. Подробности и шаблон — [`docs/GUI_SCREENS.md`](./docs/GUI_SCREENS.md).

**Оверлеи** (`client/gui/overlay/`): `FrontlineHudOverlay`, `RankHudOverlay`, `HudOverlay`, `NotificationOverlay`, `VoiceChatOverlay`, `CustomHotbarOverlay`, `CancelVanillaHotbar`.

**Утилиты**: `PjmGuiUtils`, `PjmUiSounds`, `PjmUiButton` (виджет), `GuiItemIcons`.

**Кейбайндинги**: `client/input/ModKeyBindings`.

## Персистентность

Состояние мира — ванильный **`SavedData`**: `*SavedData` классы. Доступ через `level.getDataStorage().computeIfAbsent(...)`. Каждый класс — per-world сохранение. Примеры: `FactionSelectionSavedData`, `GarageSavedData`, `GarageTerminalSavedData`, `RankSavedData`, `RoleSavedData`, `WarehouseSavedData`, `WarehouseSettingsSavedData`, `FactionCommanderSavedData`.

## Конфигурируемые JSON-регистры

Загружаются из `config/pjmbasemod/<subsystem>/` (НЕ из ресурсов мода), читаются через Gson:
`VehicleRegistry`, `WarehouseItemRegistry`, `CrateRegistry`, `RoleLimitRegistry`, `RankRegistry`.
Перезагружаются на `ServerStartedEvent` и через `/pjm ... reload`.

## Команды

Brigadier-дерево: `common/command/PjmCommands` (корень `/pjm`) + `WarehouseCommands`.
Регистрация через `RegisterCommandsEvent`.

## Локализация

5 языков: `src/client/resources/assets/pjmbasemod/lang/ru_ru.json`, `en_us.json`, `uk_ua.json`, `de_de.json`, `zh_cn.json`. При добавлении ключа — **во все пять**.

## Опциональные зависимости

`geckolib` (required — используется для `NotebookEntity`), `voicechat`/Simple Voice Chat (`compileOnly`), `journeymap` (client), `bluemap`, `superbwarfare`, `tacz` (локальный jar по абсолютному пути в `build.gradle`). Soft-deps загружаются через `common/compat/` и `client/compat/`.

## Шаблон новой подсистемы (garage / warehouse)

1. `common/<name>/` — `*Manager` + `*Registry` (JSON) + `*SavedData` + `*Snapshot` + `*Permissions`
2. Пакеты: `Open*Packet` (S→C), `*SyncPacket` (S→C), action-пакеты (C→S) в `common/network/packet/`
3. Регистрация в `PjmNetworking`, бамп `VERSION`
4. Метод в `ClientPacketProxy` (default noop) + реализация в `ClientPacketHandlersImpl`
5. `client/gui/screen/*Screen` (extends `Screen`, без ContainerMenu)
6. Клиентский state-класс `client/<name>/Client*State`

## Документация (`docs/`)

Актуальная документация по подсистемам, сверенная с кодом:

| Файл | О чём |
|------|-------|
| [`docs/WAREHOUSE.md`](./docs/WAREHOUSE.md) | Система склада: флаги конфига, `items.json`, ящики `crates/`, пулы очков, NPC-кладовщик, зона приёма, личный бюджет, сдача, команды, права, TACZ. |
| [`docs/WAREHOUSE_CONFIG.md`](./docs/WAREHOUSE_CONFIG.md) | Краткий редирект на `WAREHOUSE.md`. |
| [`docs/ROLES.md`](./docs/ROLES.md) | Система боевых ролей: назначение через radial menu, лимиты. |
| [`docs/FACTION_COMMANDER.md`](./docs/FACTION_COMMANDER.md) | Роль командира фракции (`КМД`), отдельная от XP-званий. |
| [`docs/FACTION_MANAGEMENT.md`](./docs/FACTION_MANAGEMENT.md) | Экран управления фракцией: командир/зам, права заместителей (`DeputyPermission`), приказ фракции с TTL, конфиг `faction`. |
| [`docs/GUI_SCREENS.md`](./docs/GUI_SCREENS.md) | Руководство по полноэкранным `Screen`-меню с масштабированием (`PjmBaseScreen`). |
| [`docs/DEPENDENCIES.md`](./docs/DEPENDENCIES.md) | Зависимости фронтенда: что UI ожидает от мода, импорты по слоям. |
| [`docs/WEBPANEL.md`](./docs/WEBPANEL.md) | Веб-панель: конфиг `web`, вход через `/pjm web login`, метрики, профайлер entity, API, reverse proxy. |

## Предостережение по DOCS.md

`DOCS.md` и `docs/` — **идеализированная** документация: описывают классы и команды (`DynamicDimensionManager`, `PjmCommonEvents`, `/pjm dimension`), которых в коде **нет**. Сверяйся с реальным кодом, а не с DOCS.

## Conventions

- Команды/состояние игроков — через ванильный **scoreboard** (`Teams.resolvePlayerTeamId`, `Teams.all()`, пакет `common/teams/`). Классов `PjmPlayerData`/`PjmPermissions` нет.
- `SoundEvents` в моде = собственный `SoundEvent` (не ванильный `Holder<SoundEvent>`). Передавай напрямую в `level.playSound(...)`, без `.value()`.
- Гаражи двух типов: `GarageType.GROUND` и `GarageType.AVIATION` — раздельные слоты и терминалы.
- Mixins (пакет `mixin/`, конфиг `pjmbasemod.mixins.json`): `SlotMixin` (блокировка инвентаря), `ServerPlayerMixin` (ваниш через `broadcastToPlayer`), клиентский `AbstractClientPlayerMixin`.

## Git и контроль версий

После каждой успешной итерации предлагай закоммитить изменения.

---

## Семантический поиск по коду (Qdrant) — ОБЯЗАТЕЛЬНО

Кодовая база проиндексирована в локальном Qdrant. **Любой поиск «по смыслу»
(«где реализовано X», «как работает Y») начинай со скилла `semantic-code-search`**,
а не с перебора grep/Glob по каталогам:

```bash
/home/liko/Разработка/Qdrant/qdrant.sh search -p pjm-basemod "захват контрольной точки"
```

Выдаёт путь:строку и фрагмент — дальше читай файлы точечно. Grep используй только
для точных идентификаторов/строк, которые уже известны (`GarageSyncPacket`, `PjmDimensions.LOBBY`).
После крупных изменений переиндексируй: `qdrant.sh index pjm-basemod`. Если Qdrant не
запущен (`qdrant.sh status`) — подними его (`qdrant.sh start`), а не откатывайся на grep.
