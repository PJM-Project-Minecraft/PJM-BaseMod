# PJM BaseMod — Agent Instructions

Тактический PvP-мод для **Minecraft 1.21.1 / NeoForge 21.1.172 / Java 21**. MODID `pjmbasemod`, group `ru.liko`.

**Язык общения, комментариев и документации — русский.**

> Подробная архитектура описана в [`CLAUDE.md`](./CLAUDE.md). Этот файл — краткая выжимка критичных для агента правил. При расхождении доверяй реальному коду, а не [`DOCS.md`](./DOCS.md)/[`docs/`](./docs) (там идеализированная документация с несуществующими классами).

## Build & verify

```bash
./gradlew compileJava        # сборка common (src/main)
./gradlew compileClientJava  # сборка client (src/client)
./gradlew build              # полная сборка трёх JAR + datagen
./gradlew runData            # datagen → src/generated/resources
```

**`runClient` / `runServer` НЕ РАБОТАЮТ** из этого каталога: путь содержит `!Curseforge Mods`, символ `!` ломает dev-лаунчер NeoForge. Верификация агента = `compileJava` + `compileClientJava` + валидация JSON. Внутриигровую проверку выполняет пользователь.

## Архитектура: три source set'а (критично)

| Source set | Путь | Назначение |
|------------|------|------------|
| `common` | `src/main` | Сервер-логика, сеть, данные, регистры |
| `client` | `src/client` | GUI, рендер, ввод (зависит от `main`) |
| `server` | `src/server` | Серверная точка входа |

**Жёсткое правило:** `client`/`server` импортируют `main`, но **`main` НИКОГДА не импортирует `client`** (иначе ломается dedicated-server JAR). Развязка S→C через прокси `PjmNetworking.CLIENT` (интерфейс `ClientPacketProxy`, по умолчанию `NOOP`), который `ClientInit` подменяет на `ClientPacketHandlersImpl`.

## Ключевые конвенции

- **Сеть:** пакеты регистрируются в `common/network/PjmNetworking`; при изменении состава пакетов **бампать `VERSION`**. C→S → `ServerPacketHandlers`/менеджеры; S→C → метод в `ClientPacketProxy` (default noop) + реализация в `ClientPacketHandlersImpl`.
- **События:** всё через `@EventBusSubscriber`. Server-хаб — `common/event/PjmServerEvents`. Отложенные действия на входе (телепорт) делать в `onPlayerTick`, **не** в `onLogin` (entity ещё не заспавнен).
- **Персистентность:** ванильный `SavedData` (`*SavedData` классы, per-world).
- **JSON-регистры** грузятся из `config/pjmbasemod/<subsystem>/` (НЕ из ресурсов мода), перезагрузка на `ServerStartedEvent` и `/pjm ... reload`.
- **Команды/состояние игроков:** через ванильный **scoreboard** (`FrontlineTeams.resolvePlayerTeamId`, `FrontlineTeams.all()`). Классов `PjmPlayerData`/`PjmPermissions` нет.
- **GUI:** кастомные `Screen` **без `AbstractContainerMenu`**. Полноэкранные меню наследуют `PjmBaseScreen` (масштабирование, чтобы не вылезать за края) — см. [`docs/GUI_SCREENS.md`](./docs/GUI_SCREENS.md).
- **Звуки:** `SoundEvents` в моде = собственный `SoundEvent`, передавай напрямую в `level.playSound(...)` без `.value()`.
- **Mixins:** единственный — `SlotMixin`. Новые добавлять в пакет `mixin/`.
- **Локализация:** при добавлении ключа — **во все 5 языков** (`src/client/resources/assets/pjmbasemod/lang/`: `ru_ru`, `en_us`, `uk_ua`, `de_de`, `zh_cn`).

## Шаблон новой подсистемы

`common/<name>/`: `*Manager` + `*Registry` (JSON) + `*SavedData` + `*Snapshot` + `*Permissions` → пакеты в `common/network/packet/` → регистрация в `PjmNetworking` (+ бамп `VERSION`) → метод в `ClientPacketProxy` + реализация в `ClientPacketHandlersImpl` → `client/gui/screen/*Screen` → клиентский `client/<name>/Client*State`.

## Тесты и Git

- Изменения кода по возможности сопровождай тестами; для багфиксов — регрессионные тесты.
- После каждой логически завершённой итерации предлагай закоммитить изменения с чётким сообщением (проблема + решение).

## Документация подсистем

- [`docs/DEPENDENCIES.md`](./docs/DEPENDENCIES.md) — опциональные зависимости
- [`docs/GUI_SCREENS.md`](./docs/GUI_SCREENS.md) — как делать GUI-экраны (`PjmBaseScreen`, масштабирование)
- [`docs/FACTION_COMMANDER.md`](./docs/FACTION_COMMANDER.md) — роль командира фракции
- [`docs/ROLES.md`](./docs/ROLES.md) — боевые роли и лимиты
- [`docs/WAREHOUSE_CONFIG.md`](./docs/WAREHOUSE_CONFIG.md) — конфигурация склада
