# Система логирования действий игроков (`pjmlogs`)

**Дата:** 2026-07-07
**Статус:** дизайн утверждён, готов к плану реализации

## Цель

Читаемый лог действий игроков сервера в отдельную папку `pjmlogs`: убийства
(PvP), уничтожение техники SuperBWarfare (SBW), вход/выход игроков и ключевые
действия подсистем мода. Логи должны легко читаться администратором глазами.

## Область (что логируем)

| Категория | Тег | Источник события |
|-----------|-----|------------------|
| Убийства игроков (PvP) | `[KILL]` | `LivingDeathEvent`, жертва — `ServerPlayer` |
| Уничтожение техники SBW | `[VEHICLE]` | `LivingDeathEvent`, тип сущности namespace `superbwarfare` |
| Вход/выход | `[JOIN]` / `[LEFT]` | `PlayerLoggedInEvent` / `PlayerLoggedOutEvent` |
| Склад (выдача/сдача) | `[WAREHOUSE]` | `WarehouseManager` |
| Гараж (спавн техники) | `[GARAGE]` | `GarageManager` |
| Захват сектора фронта | `[FRONTLINE]` | `FrontlineManager` |
| Смерть в базовой зоне | `[BASEZONE]` | enforcement базовой зоны |
| Модерация (кик/бан) | `[MOD]` | `ModerationService` |

## Формат файлов

- Папка: `<game>/pjmlogs/` (корень сервера, через `FMLPaths.GAMEDIR`).
- Один файл на день: `pjmlogs/YYYY-MM-DD.log`, режим `append`.
- Строка: `[HH:mm:ss] [TAG] <сообщение на русском>`.
- Тексты сообщений — **на русском**.

Примеры:

```
[12:30:05] [KILL]      Steve → Alex (AK-47) @ 100,64,-200 [RED→BLUE]
[12:31:10] [VEHICLE]   Steve уничтожил технику T-90 @ 120,63,-180
[12:32:00] [JOIN]      Notch вошёл в игру
[12:33:45] [WAREHOUSE] Alex взял weapon_crate (-50 очков)
[12:40:12] [FRONTLINE] сектор (7,-3) захвачен командой BLUE
[12:41:00] [BASEZONE]  Alex погиб в базовой зоне команды RED
```

## Архитектура

### `common/logging/PjmActionLogger` (синглтон)

Единственная точка записи. Обязанности:

- **Асинхронная запись.** События кладутся в `ConcurrentLinkedQueue<String>`.
  Отдельный daemon-поток (`pjm-action-logger`) дренажит очередь и флашит в файл.
  Игровой поток никогда не блокируется на дисковом I/O — тот же инвариант, что у
  `WebState`/`WebActions` в веб-панели (игровые потоки не ждут внешний I/O).
- **Ротация по дате.** Перед записью строки сверяется текущая дата; если день
  сменился — закрывается старый `BufferedWriter`, открывается новый файл дня.
- **Жизненный цикл.** Инициализация на `ServerStartedEvent`; финальный флаш +
  закрытие writer'а на `ServerStoppingEvent`.
- **Конфиг-гейт.** Если `logging.enabled == false`, все методы логирования —
  no-op (ничего не пишется, поток не стартует).

Публичный API (вызывается из игрового потока, дёшево — только enqueue):

```java
PjmActionLogger.instance().logKill(killer, victim, weaponOrSource);
PjmActionLogger.instance().logVehicleDestroyed(attacker, vehicleType, pos);
PjmActionLogger.instance().logSession(player, joined /* true=вход */);
PjmActionLogger.instance().logSubsystem(LogCategory.WAREHOUSE, "Alex взял weapon_crate (-50 очков)");
```

`enum LogCategory` — задаёт `[TAG]`-префикс. Форматирование строки (метка
времени + тег + текст) целиком внутри логгера, вызывающий код передаёт только
данные/готовый текст сообщения.

### Ловля событий

- **`PjmServerEvents.onLivingDeath` (строка 84)** — жертва `ServerPlayer` →
  `logKill`: киллер (из `getEntity()`/`getKillCredit()`), жертва, оружие в руке
  киллера либо тип урона, координаты, команды сторон через
  `FrontlineTeams.resolvePlayerTeamId`. Существующий вызов
  `RankService.handlePlayerKill` сохраняется без изменений.
- **Уничтожение техники SBW — через Mixin, НЕ `LivingDeathEvent`.** Техника SBW
  `com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity` наследует
  `Entity`, а не `LivingEntity`, поэтому `LivingDeathEvent` для неё не срабатывает.
  Метод `VehicleEntity.destroy()` вызывается ровно один раз при `health <= 0` —
  точный момент уничтожения, где доступен `lastAttacker`. `SbwVehicleDestroyMixin`
  инжектит в `destroy()` (HEAD) и вызывает `logVehicleDestroyed(getLastAttacker(), this)`.
  - Таргет — строковый (`@Mixin(targets = "…")`, `remap = false`), `@Shadow`-метод
    `getLastAttacker()` возвращает ванильный `Entity` → **SBW не нужен в
    compileOnly**, build.gradle не меняется.
  - Отдельный конфиг `pjmbasemod.sbw.mixins.json` (`required=false`,
    `defaultRequire=0`), зарегистрирован вторым `[[mixins]]` в `neoforge.mods.toml`.
    Без SuperbWarfare класс-таргет не загружается и миксин просто не применяется.
- **`PjmServerEvents.onLogin` / `onLogout`** — добавляется `logSession`.
- **Подсистемы** — точечные вызовы `logSubsystem(...)` в местах фактического
  действия: `WarehouseManager` (выдача/сдача), `GarageManager` (спавн техники),
  `FrontlineManager` (захват сектора), enforcement базовой зоны (смерть),
  `ModerationService` (кик/бан).

### Конфиг

Новая секция `logging` в `Config.java` с единственным флагом:

```
logging.enabled = true   // включает/выключает всю запись в pjmlogs
```

По умолчанию `true`. Читается логгером на `ServerStartedEvent`.

## Ограничения по слоям

- `PjmActionLogger` и всё логирование — только в `common` (`src/main`).
  `client` не участвует. Нарушения инварианта «main не импортирует client» нет.
- Логгер не зависит от SBW на этапе компиляции: `SbwVehicleDestroyMixin` бьёт по
  классу SBW строковым таргетом и шэдоуит только ванильно-типизированный
  `getLastAttacker()`. SBW не в `build.gradle`; без мода миксин не применяется.
- Миксин в `src/main` (common) — применяется на обеих сторонах, но `destroy()`
  отрабатывает только на сервере (гвард `!level().isClientSide()`).

## Обработка ошибок

- Ошибки открытия/записи файла ловятся внутри daemon-потока и пишутся в
  основной `Pjmbasemod.LOGGER.warn/error` один раз (без спама), запись действий
  при этом не роняет сервер.
- Если папку `pjmlogs` не удалось создать — логгер деградирует в no-op с
  предупреждением в консоль.

## Верификация

- `./gradlew compileJava` + `./gradlew compileClientJava`.
- `runClient`/`runServer` не работают из этого пути (символ `!`), внутриигровую
  проверку (реальные строки в `pjmlogs/*.log`) выполняет пользователь.

## Явно вне области (YAGNI)

- Пер-категорийные тумблеры конфига (решено: один общий выключатель).
- JSON/машиночитаемый формат (решено: человекочитаемый текст).
- Ротация по размеру, сжатие старых логов, отправка во внешние системы.
- Интеграция логов в веб-панель (может стать отдельной задачей позже).
