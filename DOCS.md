# PJM BaseMod — Полная документация

Этот документ — единая точка входа в документацию мода **WRB-BaseMod / PJM BaseMod** (`pjmbasemod`), NeoForge 1.21.1, Java 21. Военный тактический PvP в духе Squad / Arma Reforger / Battlefield.

> Сопутствующие документы: [MOD_OVERVIEW.md](MOD_OVERVIEW.md) — карта систем, [CLASSES.md](CLASSES.md) — классы/пермишены, [COMMANDS.md](COMMANDS.md) — Brigadier-дерево команд, [MAP_SETUP.md](MAP_SETUP.md) — авторинг карт, [FACTION_COMMANDER.md](docs/FACTION_COMMANDER.md) — роль КМД фракции, [ROLES.md](docs/ROLES.md) — боевые роли склада и техники. UI и фронтенд — см. [frontend-export/](frontend-export/).

---

## 1. Архитектура

### 1.1 Точка входа и реестры

- `Pjmbasemod` — главный мод-класс. `commonSetup` инициализирует серверные конфиги и общие системы мода.
- DeferredRegister-ы: `PjmBlocks` (блоки), `Pjmbasemod.ITEMS` (предметы), `PjmSounds` (звуки), `PjmAttachments` (Data Attachments игрока).
- Сетевые пакеты регистрируются в `PjmNetworking.register(RegisterPayloadHandlersEvent)`.
- Всё событийное — через `@EventBusSubscriber`. Главный server-side хаб — `common/event/PjmCommonEvents.java`.

### 1.2 Дистрибуция: три JAR

Из одного gradle-проекта собираются три артефакта:

| JAR | Source sets | Назначение |
|-----|-------------|------------|
| `wrb-basemod-<v>.jar` (default) | main + client | Универсальный, для одиночной/lan игры. |
| `wrb-basemod-<v>-server.jar` | main only | Для выделенного сервера без клиентских ассетов. |
| `wrb-basemod-<v>-client.jar` | main + client | Только клиент, без серверных deps. |

**Важно:** `src/client/` подключается отдельным source set-ом — никаких импортов из `src/main/` в `src/client/` запрещено. Это критично для сервер-JAR.

### 1.3 Подсистемы (краткий справочник)

| Подсистема | Корневой пакет | Ключевые классы |
|------------|----------------|-----------------|
| Карты и измерения | `common.map`, `common.dimension` | `MapManager`, `MapRotationManager`, `MapConfigManager`, `DynamicDimensionManager`, `DimensionRulesEnforcer`, `DimensionLifecycle` |
| Контрольные точки | `common.gamemode` | `ControlPoint`, `ControlPointManager`, `ControlPointSnapshot` |
| Команды и баланс | `common.player`, `common.util` | `PjmPlayerData.team`, `TeamBalanceHelper` |
| Классы и киты | `common.player`, `common.config` | `PjmPlayerClass`, `KitsConfig`, `KitDefinition`, `ClassPermissionsConfig` |
| Кастомизация | `common.customization` | `CustomizationManager`, `CustomizationOption`, `CustomizationType` |
| Чат | `common.chat` | `ChatMode`, `ChatEvents` |
| Радио | `common.audio`, `client.radio` | `RadioAudioProcessor`, `RadioManager`, `PjmVoiceChatClientPlugin` |
| Анти-гриф | `common.config`, миксины | `Config.isBlockBreakable`, `ServerPlayerGameModeMixin` |
| Туман | `common.config`, `client.render` | `SyncFogConfigPacket`, `ClientFogConfig`, `FogRenderer` |
| Техника | `common.vehicle` | `VehicleSpawnSystem`, `SuperbWarfareVehicleHelper` |
| Таймеры | `common.scheduler` | `CommandScheduler` |
| Зоны выбора класса | `common.zone` | `ClassSelectionZoneManager`, безопасные зоны |
| API / бекенд | `common.api` | `PjmApiClient`, `PjmStatsApi`, `PjmEventApi`, `PjmApiConfig` |
| Пермишены | `common.permission` | `PjmPermissions` (LuckPerms + OP fallback) |
| Сеть | `common.network` | `PjmNetworking`, ~30 пакетов |

### 1.4 Player Data (Data Attachments)

`PjmPlayerData` хранится как NeoForge Data Attachment (`PjmAttachments.PLAYER_DATA`). Поля: команда, класс, кит, кастомизация, режим чата, спец-слоты, кумулятивная статистика. Сериализация — `INBTSerializable<CompoundTag>` + Codec. Доступ:

```java
PjmPlayerData data = player.getData(PjmAttachments.PLAYER_DATA);
// или через провайдер
Optional<PjmPlayerData> opt = PjmPlayerDataProvider.get(player);
```

Синхронизация на клиент: `SyncPjmDataPacket.fromPlayerData(player, data)`.

### 1.5 Network layer

Все пакеты — `CustomPacketPayload` с явным codec. Регистрируются в `PjmNetworking`. Группы:

- **Sync (S→C):** `SyncPjmDataPacket`, `SyncTeamConfigPacket`, `SyncKitsDataPacket`, `SyncChatModePacket`, `SyncClassSelectionDataPacket`, `SyncInZoneStatusPacket`, `SyncCustomizationOptionsPacket`, `SyncFogConfigPacket`, `SyncGameModeDataPacket`, `SyncControlPointPacket`, `SyncTeamSelectionResultPacket`.
- **Open screen (S→C):** `OpenClassSelectionPacket`, `OpenTeamSelectionPacket`, `OpenSpawnMenuPacket`.
- **Select (C→S):** `SelectTeamPacket`, `SelectClassPacket`, `SelectSpawnPacket`, `SelectCustomizationPacket`.
- **Chat/radio:** `ChangeChatModePacket`, `RadioEventPacket`, `RadioSwitchPacket`.
- **Misc:** `RefillAmmunitionPacket`, `RequestRallyItemPacket`, `RequestOpenTeamSelectionPacket`.

Обработка на клиенте — `client/network/ClientPacketHandlers.java`, дисп. через `ClientPacketProxy`/`ClientPacketProxyImpl`. Серверу никогда не разрешается напрямую дёргать клиентские классы.

---

## 2. Конфиги

| Файл | Класс | Что внутри |
|------|-------|------------|
| `config/pjmbasemod-common.toml` | `Config` | general, hud, milsim, controlPoints, teams, region, frontline, garage, commands |
| `kits.json` (он же `pjm_kits.json`) | `KitsConfig` | классы × команды → список item-id (`ItemParser`) |
| `pjm_class_permissions.json` | `ClassPermissionsConfig` | джейсон-овский ACL для special-классов (sso, ew_specialist, uav_operator) |
| `maps/<id>.json` | `MapConfigManager` | bounds, spawn points, control points, vehicle points |
| `dimensions/<name>.json` | `DimensionConfig` | type (void/flat/normal), правила, PvP, мобы, граница |
| `dynamic_dimensions.json` | `DynamicDimensionManager` | список измерений для preload |
| `stats_api.json` | `PjmApiConfig` | url, apiKey, флаги, timeout |
| `warehouse/items.json` | `WarehouseItemRegistry` | предметы, доступные у кладовщика склада |
| `roles/limits.json` | `RoleLimitRegistry` | лимиты ролей по фракциям из `teams.definitions` |

Основной NeoForge-конфиг лежит в `config/pjmbasemod-common.toml`. Остальные JSON-конфиги, если используются конкретной подсистемой, лежат рядом с runtime-конфигами сервера.

Формат склада описан отдельно: [docs/WAREHOUSE_CONFIG.md](docs/WAREHOUSE_CONFIG.md).

---

## 3. Команды (Brigadier, корень `/pjm`)

Полный референс — в [COMMANDS.md](COMMANDS.md). Сводка:

| Подкоманда | Что делает |
|------------|------------|
| `/pjm team` | join, leave, balance, swap, shuffle |
| `/pjm player` | class, force open class/team/spawn, stats |
| `/pjm cp` | контрольные точки: create, delete, list, reset, setowner, tp |
| `/pjm vehicle` | spawn, despawn, point add/remove/list |
| `/pjm zone` | зоны выбора класса: create, delete, list, tp |
| `/pjm kit` | list, reload, give |
| `/pjm faction commander` | назначение, снятие и просмотр КМД фракций |
| `/pjm faction manage` | GUI управления ролями онлайн-бойцов своей фракции |
| `/pjm timer` | start, stop, broadcast |
| `/pjm dimension` | create, remove, list, tp, info, config |
| `/pjm fog` | управление туманом |
| `/pjm skin` | управление скинами |
| `/pjm config` | list, get, set, reload |
| `/pjm debug` | sync, netstat, dump |

Отдельная команда `/chat` переключает `ChatMode` (LOCAL / GLOBAL / TEAM).

---

## 4. Permissions

`PjmPermissions` объявляет узлы NeoForge `PermissionAPI`. Интеграция — LuckPerms (приоритет) + fallback на OP-уровни. Ключевые узлы:

- `team.manage`, `team.join.self`, `team.join.other`
- `class.zone.manage`
- `vehicle.spawn.manage`, `vehicle.spawn.force`
- `timer.create`, `timer.manage`, `timer.view`
- `config.reload`
- `faction.commander.admin` — управление КМД фракций (`/pjm faction commander set/clear`)
- `role.admin` — управление ролями и доступ к `/pjm faction manage` как админ текущей фракции
- Пермишены классов (через `ClassPermissionsConfig`)

Проверки в коде: `canSelectClass(...)`, `canManagePlayers(...)`, `canModifyTeam(...)` и т.д.

---

## 5. Локализация

Языки в `assets/pjmbasemod/lang/`: **en_us, ru_ru, zh_cn, de_de, fr_fr, es_es, pt_br, uk_ua, pl_pl**. RU и EN — обязательный минимум для любого нового UI-текста. Никаких сырых строк в чат/HUD — только `Component.translatable(...)`.

---

## 6. Миксины

| Сторона | Класс | Назначение |
|---------|-------|------------|
| common | `MinecraftServerAccessor` | внутренности сервера (dynamic dimensions) |
| common | `ServerPlayerGameModeMixin` | анти-гриф (break/place/interact) |
| common | `PlayerMixin` | привязка данных мода к игроку |
| common | `FoodDataMixin` | отключение голода (MilSim) |
| common | `ItemEntityMixin` | ограничения дропа |
| common | `ChunkMapAccessor` | `ChunkMap.move(player)` для resync entity trackers |
| client | `ConnectScreenMixin` | замена экрана подключения |
| client | `DeathScreenMixin` | кастомный экран смерти |
| client | `PlayerTabOverlayMixin`, `PlayerInfoMixin` | таб-лист (команды) |
| client | `MouseHandlerMixin`, `KeyboardHandlerMixin` | ввод (радиальное меню) |
| client | `ViewBobbingLockMixin` | блокировка качания камеры |
| client | `InventoryScreenMixin` | инвентарь |
| client | `WindowMixin` | окно |
| client | `SoundEngineMixin` | звук (радио-эффект) |
| client | `VoicechatRenderEventsMixin` (SVC) | рендер голос. чата |

Refmap **отключён** (`-Amixin.env.disableRefMap=true`); ремаппит moddev-плагин. Не включать.

---

## 7. Клиент (frontend)

Полный референс UI — отдельной выгрузкой в [frontend-export/](frontend-export/):

- [frontend-export/README.md](frontend-export/README.md) — структура
- [frontend-export/docs/GUI_CATALOG.md](frontend-export/docs/GUI_CATALOG.md) — каждый Screen/Overlay
- [frontend-export/docs/DEPENDENCIES.md](frontend-export/docs/DEPENDENCIES.md) — что нужно из common/
- [frontend-export/docs/PORTING.md](frontend-export/docs/PORTING.md) — гайд по переносу

Краткий список:

**Screens:** `TacticalMainMenuScreen`, `TeamSelectionScreen`, `ClassSelectionScreen`, `SpawnSelectionScreen`, `PlayerStatsScreen`, `PostShaderSettingsScreen`, `RadialMenuScreen`.

**Overlays:** `HudOverlay` (компас), `CustomHotbarOverlay`, `CancelVanillaHotbar`, `CustomTabOverlay`, `NotificationOverlay`, `VoiceChatOverlay`, `ItemSwitchPanel`, `GameModeHudOverlay`, `CaptureStatusBar`. Регистрация — `ClientOverlays.onRegisterGuiLayers(RegisterGuiLayersEvent)`.

---

## 8. Звуки

`PjmSounds` (registered events):

- **UI:** `ui.menu.press`, `ui.menu.shared`, `ui.class.change`
- **Меню:** `menu.loading`, `menu.music`, `menu.join`, `menu.promoted`
- **Радио:** `radio.start`, `radio.end`, `radio.background`, `radio.crackle`
- **Классы/деплоймент:** см. полный список в `PjmSounds.java`

---

## 9. Сборка

```bash
./gradlew build              # три JAR-а в build/libs/
./gradlew compileJava        # быстрая проверка компиляции
./gradlew runClient          # дев-клиент с модом
./gradlew runServer          # дев-сервер (--nogui)
./gradlew runGameTestServer  # игровые тесты (namespace pjmbasemod)
./gradlew runData            # datagen → src/generated/resources/
```

JUnit-тесты не подключены (хотя `src/test/` существует — пустой). Покрытие — `runGameTestServer`.

---

## 10. Конвенции (важное)

- **Локализация обязательна.** RU + EN минимум, плюс существующие локали. Только `Component.translatable(...)`.
- **Cross-dim teleport требует живой entity.** Vanilla `Entity.teleportTo` молча `no-op`-ит на `isDeadOrDying`/`isRemoved`. Для проблемных переносов используй паттерн `playerList.respawn(...)`.
- **Не итерируй `level.getAllEntities()` с одновременным `discard()`.** Это live-view над fastutil-мапой. Собирай ID в список и удаляй пачкой.
- **Forge 1.20.1-паттерны устарели.** Capability API → Data Attachments; `getCapability(...)` → `getData(...)`; IGuiOverlay → `LayeredDraw.Layer`. Проверяй современный NeoForge 1.21 API.
- **Geckolib — hard dependency.** Заявлен `required` в `neoforge.mods.toml`, хотя часть кода делает runtime-проверки. Без Gecko мод не загрузится.
- **Style:** русский язык в общении, без эмодзи, без коммитов `build/` / временных файлов.

---

## 11. Известные проблемные зоны

- **Cross-dim TP race на свежем `ServerLevel`** — частая причина "frozen remote players". Mitigations: pre-create dimensions, проверка живой entity, `forceEntityTrackerResync`.
- **Refmap mixins** — отключён намеренно. Если включить — moddev-ремапер начнёт давать имена в обход.

---

## 12. Связанные документы

- [MOD_OVERVIEW.md](MOD_OVERVIEW.md) — высокоуровневая карта систем (RU)
- [CLASSES.md](CLASSES.md) — список классов, пермишены, лимиты
- [COMMANDS.md](COMMANDS.md) — Brigadier-дерево всех команд
- [MAP_SETUP.md](MAP_SETUP.md) — как создавать карты и измерения
- [CLAUDE.md](CLAUDE.md) — рабочие заметки для AI-ассистента (архитектурные подсказки)
- [frontend-export/](frontend-export/) — UI-снимок для портирования

---

*Документ сгенерирован на основе кодовой базы WRB-BaseMod, дата актуальности — см. git log.*
