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
- `Config.java` (корень пакета, не в `common/init/`) — NeoForge Config с секциями: `general`, `hud`, `milsim`, `controlPoints`, `teams` (TEAM_JOIN_COMMANDS), `region`, `frontline` (с окном реального времени захвата).

## Сетевой слой

`common/network/PjmNetworking` регистрирует все пакеты. **`VERSION = "12"` — бампать при изменении состава пакетов.** Пакеты — records в `common/network/packet/` со `STREAM_CODEC`.

- **C→S**: обрабатываются в `common/network/handler/ServerPacketHandlers` или напрямую в менеджерах (`GarageManager`, `WarehouseManager`).
- **S→C**: маршрутизируется через `ClientPacketProxy` → `client/network/ClientPacketHandlersImpl`.

Добавляя пакет: создать record-payload → зарегистрировать в `PjmNetworking` → (для S→C) добавить метод в `ClientPacketProxy` + реализацию.

## События

Всё через `@EventBusSubscriber`. Главный server-side хаб — `common/event/PjmServerEvents`:

- `onLogin` — рассылает первичную синхронизацию подсистем, вызывает `*.onPlayerLogin`.
- `onPlayerTick` (`PlayerTickEvent.Post`) — тик-сервисы: `FactionCommanderService`, `RoleService`, `LobbyService`, `FactionMenuService`. **Отложенные действия (телепорт при входе) — здесь, не в `onLogin`**: на логине entity ещё не полностью заспавнен.
- `onServerTick` (`ServerTickEvent.Post`) — `FrontlineManager.onServerTick`, `FrontlineBlueMapService.onServerTick`.
- `onServerStarted` — `reload()` датапак-регистров + startup-команды из конфига, `RankService.onServerStarted`.

## Подсистемы (`common/<package>/`)

### faction
`FactionSelectionSavedData`, `FactionSelectionSnapshot` — выбор фракции (scoreboard-команда).
`FactionMenuService` — управляет открытием GUI выбора фракции.
`FactionCommanderService`, `FactionCommanderSavedData` — роль командира.
`FactionJoinActions` — действия при вступлении в фракцию.
`FactionPermissions`, `FactionManagementSnapshot` — права и синхронизация.
`FactionDeputySavedData` + enum `DeputyPermission` (ASSIGN_ROLES/SET_ORDER/OPEN_GUI) — заместители фракции (teamId → playerId → битмаска), лимит `faction.maxDeputies`.
`FactionOrderSavedData`, `FactionOrderManager` — приказ фракции (текст + TTL): разовое уведомление + постоянная HUD-плашка у всех членов команды. Клиент: `ClientFactionOrderState`, `FactionOrderHudOverlay`.
`FactionMenuService.Authority` — права зрителя. Конфиг-секция `faction` (maxDeputies, orderMaxLength, orderDefaultTtlMinutes, orderMaxTtlMinutes). Экран: вкладки Роль/Зам/Приказ, недоступные скрыты — [`docs/FACTION_MANAGEMENT.md`](./docs/FACTION_MANAGEMENT.md).

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
`WarehousePoolCategory` — 5 пулов: WEAPON/SUPPLY/EQUIPMENT/RAW/SPECIAL.
`WarehousePermissions` — права.
`QuartermasterEntity` — NPC-кладовщик (`Mob` без ИИ, рендер `QuartermasterRenderer` через ванильную `PlayerModel`).
Ящики: `SupplyCrateItem` × 5 (weapon_crate/supply_crate/equipment_crate/raw_crate/special_crate).
Клиентский экран: `client/gui/screen/WarehouseScreen`.
Полная документация подсистемы (флаги конфига, `items.json`, `crates/`, команды, NPC, TACZ) — [`docs/WAREHOUSE.md`](./docs/WAREHOUSE.md).

### inventory
`InventoryLimitService`, `InventoryLimitRegistry`, `InventoryLimitConfig` — блокировка слотов инвентаря.
`SlotMixin` (единственный mixin) — инжект в `Slot.mayPickup` / `Slot.mayPlace`, запрещает взаимодействие с заблокированными слотами.
Синхронизируется `LockedSlotsPacket` (S→C). Клиентское зеркало: `client/inventory/LockedSlotsClientState`.

### frontline
`FrontlineManager`, `FrontlineSavedData`, `FrontlineChunkKey/State`, `FrontlineSectorKey/State` — система захвата секторов по чанкам.
`FrontlineTeams` — резолв команды игрока через ванильный **scoreboard** (не кастомный enum):
- `FrontlineTeams.resolvePlayerTeamId(ServerPlayer)` → id команды или null
- `FrontlineTeams.all()` → `List<Config.ConfiguredTeam>` с `.id()`
`FrontlineBlueMapRuntime`, `FrontlineBlueMapService` — интеграция с BlueMap.
Клиентское зеркало: `client/frontline/ClientFrontlineState`.
JourneyMap: `client/frontline/journeymap/` — плагин карты.
HUD: `client/gui/overlay/FrontlineHudOverlay`.

### region
`RegionManager`, `RegionSavedData`, `Region` — именованные регионы мира.
Клиентское зеркало: `client/region/ClientRegionState`.

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

Состояние мира — ванильный **`SavedData`**: `*SavedData` классы. Доступ через `level.getDataStorage().computeIfAbsent(...)`. Каждый класс — per-world сохранение. Примеры: `FactionSelectionSavedData`, `GarageSavedData`, `GarageTerminalSavedData`, `RankSavedData`, `RoleSavedData`, `WarehouseSavedData`, `WarehouseSettingsSavedData`, `FrontlineSavedData`, `RegionSavedData`, `FactionCommanderSavedData`.

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

## Предостережение по DOCS.md

`DOCS.md` и `docs/` — **идеализированная** документация: описывают классы и команды (`DynamicDimensionManager`, `PjmCommonEvents`, `/pjm dimension`), которых в коде **нет**. Сверяйся с реальным кодом, а не с DOCS.

## Conventions

- Команды/состояние игроков — через ванильный **scoreboard** (`FrontlineTeams.resolvePlayerTeamId`, `FrontlineTeams.all()`). Классов `PjmPlayerData`/`PjmPermissions` нет.
- `SoundEvents` в моде = собственный `SoundEvent` (не ванильный `Holder<SoundEvent>`). Передавай напрямую в `level.playSound(...)`, без `.value()`.
- Гаражи двух типов: `GarageType.GROUND` и `GarageType.AVIATION` — раздельные слоты и терминалы.
- Единственный mixin — `SlotMixin` (блокировка инвентаря). Добавляй новые mixins в тот же пакет `mixin/`.

## Git и контроль версий

После каждой успешной итерации предлагай закоммитить изменения.

---

## Семантический поиск по коду (Qdrant)

Кодовая база проиндексирована в локальном Qdrant. Для поиска «по смыслу» (рус/англ):

```bash
/home/liko/Разработка/Qdrant/qdrant.sh search -p pjm-basemod "захват контрольной точки"
/home/liko/Разработка/Qdrant/qdrant.sh search -p pjm-basemod -n 10 --full "телепорт между измерениями"
```

Выдаёт путь:строку и фрагмент кода. Дополняет Grep, не заменяет: хорош для вопросов
«где обрабатывается X», когда точное имя символа неизвестно. После крупных изменений
переиндексация: `qdrant.sh index pjm-basemod`. Требует запущенный Qdrant (`qdrant.sh start`).
