# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Ты Senior разработчик, PJM BaseMod (`pjmbasemod`, group `ru.liko`) — тактический PvP-мод для **Minecraft 1.21.1 / NeoForge 21.1.172 / Java 21**. Базовый код-нейм `WRB-BaseMod`. Основной язык общения и комментариев — русский.

## Build & verify commands

```bash
./gradlew compileJava        # сборка common (src/main)
./gradlew compileClientJava  # сборка client (src/client)
./gradlew build              # полная сборка трёх JAR + datagen
./gradlew runData            # datagen → src/generated/resources (вливается обратно в main)
```

**`runClient` / `runServer` НЕ РАБОТАЮТ из этого каталога.** Путь содержит `!Curseforge Mods`, и символ `!` ломает dev-лаунчер NeoForge (ModLauncher/ASM) на bootstrap: `IllegalArgumentException: Invalid package name: '!MSD' ... is not a Java identifier`. Это ограничение пути, не кода. **Верификацию делай через `compileJava` + `compileClientJava` + валидацию JSON.** Внутриигровую проверку (GUI, телепорт, кастомные дименшены) выполняет пользователь у себя или из пути без `!`.

## Source set architecture (критично)

Три source set'а из одного gradle-проекта собирают три JAR:

| Source set | Назначение |
|------------|------------|
| `src/main` (`common`) | Общий код: сервер-логика, сеть, данные, регистры |
| `src/client` | Только клиент: GUI, рендер, ввод. Зависит от `main` |
| `src/server` | Серверная точка входа |

**Жёсткое правило направления зависимостей:** `client`/`server` могут импортировать `main`, но **`main` НИКОГДА не импортирует `client`** (иначе ломается dedicated-server JAR без клиентских ассетов).

Развязка main→client сделана через **прокси**: `PjmNetworking.CLIENT` — это `ClientPacketProxy` (по умолчанию `NOOP`). `ClientInit.onClientSetup` подменяет его на `ClientPacketHandlersImpl`. Весь Server→Client код в common вызывает `CLIENT.someMethod(packet)`, не зная о клиентских классах.

## Точки входа

- `ru.liko.pjmbasemod.Pjmbasemod` (`@Mod`, MODID `pjmbasemod`) — регистрирует `DeferredRegister`-ы (`PjmItems`, `PjmEntities`, `PjmSounds` в `common/init/`), сетевые пакеты и common config.
- `client/ClientInit` (`@EventBusSubscriber Dist.CLIENT`) — ставит клиентский прокси.
- `server/ServerInit` — серверная точка входа.

## Сетевой слой

`common/network/PjmNetworking.onRegisterPayloads` регистрирует **все** ~30 пакетов (`VERSION` бампается при изменении состава). Пакеты — records в `common/network/packet/` со `StREAM_CODEC`.

- **Client → Server**: обрабатывается в `common/network/handler/ServerPacketHandlers` или напрямую в менеджерах (`GarageManager`, `WarehouseManager`).
- **Server → Client**: маршрутизируется через `ClientPacketProxy` (см. выше), реализация в `client/network/ClientPacketHandlersImpl`.

Добавляя пакет: создать record-payload → зарегистрировать в `PjmNetworking` → (для S→C) добавить метод в `ClientPacketProxy` интерфейс + реализацию.

## События

Всё событийное — через `@EventBusSubscriber`. Главный server-side хаб — **`common/event/PjmServerEvents`**:
- `onLogin` — рассылает первичную синхронизацию подсистем и вызывает `*.onPlayerLogin`.
- `onPlayerTick` (`PlayerTickEvent.Post`) — пер-тик сервисы: `LobbyService`, `FactionMenuService`, `RoleService`, `FactionCommanderService`. **Отложенные действия (телепорт при входе) делать здесь, не в `onLogin`** — на логине entity ещё не полностью заспавнен.
- `onServerStarted` — `reload()` датапак-регистров + выполнение startup-команд из конфига.

## Подсистемы (`common/<package>/`)

`faction`, `role`, `rank`, `garage` (техника), `warehouse` (склад/NPC-кладовщик), `frontline` (+`bluemap` интеграция), `region`, `chat`, `voice` (Simple Voice Chat), `dimension`, `customization`, `audio`. Клиентские зеркала — в `src/client/.../client/`.

## Персистентность

Состояние мира — через ванильный **`SavedData`**-паттерн: `*SavedData` классы (`FactionSelectionSavedData`, `GarageSavedData`, `RankSavedData`, `WarehouseSavedData`, …). Каждый — per-world сохранение, доступ через `level.getDataStorage().computeIfAbsent(...)`.

## Конфигурируемые JSON-регистры

Часть контента грузится из **рантайм-JSON** в `config/pjmbasemod/<subsystem>/` (НЕ из ресурсов): `VehicleRegistry`, `WarehouseItemRegistry`, `CrateRegistry`, `RoleLimitRegistry`. Перезагружаются в `onServerStarted` и через `/pjm ... reload`.

## Кастомные дименшены

Через **datapack JSON**, не `DeferredRegister`: пара файлов `data/pjmbasemod/dimension/<name>.json` + `data/pjmbasemod/dimension_type/<name>.json`. В Java на них ссылаются через `ResourceKey<Level>` (см. `common/dimension/PjmDimensions`), доступ — `server.getLevel(key)`. Блоки платформ/структур ставятся кодом (`LobbyService.ensurePlatform`), а не генератором.

## Команды

Brigadier-дерево в `common/command/PjmCommands` (корень `/pjm`) и `WarehouseCommands`. Регистрация через `RegisterCommandsEvent`.

## Локализация

5 языков в `src/client/resources/assets/pjmbasemod/lang/`: `ru_ru`, `en_us`, `uk_ua`, `de_de`, `zh_cn`. При добавлении ключа — **во все пять** файлов.

## Опциональные зависимости

`geckolib` (required), `voicechat`/Simple Voice Chat (optional, `compileOnly`), `journeymap` (client), `bluemap`, `superbwarfare`, `tacz` (локальный jar по абсолютному пути в `build.gradle`). Soft-deps грузятся через `common/compat/`.

## Предостережение по DOCS.md

`DOCS.md` и `docs/` — **идеализированная** документация: описывают классы и команды (`common.map`, `DynamicDimensionManager`, `PjmCommonEvents`, `/pjm dimension`), которых в коде может не быть. Сверяйся с реальным кодом, а не с DOCS.

## Conventions

- Команды/состояние нередко проводятся через scoreboard (см. память проекта).
- `SoundEvents` в моде = собственный `SoundEvent` (не ванильный класс).
- Новые подсистемы делать по шаблону существующих (garage / warehouse): регистр + менеджер + SavedData + пакеты + клиентский экран.

## Git и контроль версий

- После каждой успешной итерации ты предлагаешь закомитить изменения
