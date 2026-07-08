# Ограничение и очистка техники гаража (подсистема `fleet`)

**Дата:** 2026-07-08
**Статус:** дизайн утверждён, готов к плану реализации

## Проблема

Заспавненная из гаража техника накапливается в мире без ограничений: игроки
спамят спавн (нет лимита на одновременно активную технику), брошенная пустая
техника висит вечно, и в итоге на карте «1000 танков и джипов». Нужно: сократить
общее число активной техники, сделать её ценнее (меньше народу катается) и
автоматически убирать брошенное.

Роль-гейт на тяжёлую технику (танки) **уже реализован** через `RoleService` — в
этой работе не трогаем.

## Ключевой факт по текущему коду

При спавне (`GarageManager.performSpawn`, ~строки 477-498) сущность создаётся из
NBT-снимка `StoredVehicle`, получает новый UUID, добавляется в мир и снимается со
склада. **Никакого владельца/команды/времени на неё не вешается, активная техника
нигде не трекается.** Ручной возврат в гараж (`handleStore`/`handleStoreSelected`)
уже существует. Уничтожение SBW-техники перехватывается `SbwVehicleDestroyMixin`.

## Решение: реестр активной техники (`SavedData`) + метка на сущности

Новая подсистема `common/fleet/`. Авторитетный реестр в `SavedData` считает лимиты
даже при выгруженных чанках; persistent-NBT-метка на сущности переживает рестарт и
позволяет «усыновить» технику при реконсиляции.

### Компоненты `common/fleet/`

| Класс | Назначение |
|-------|-----------|
| `FleetRecord` | record: `entityUUID, ownerUUID, teamId, defId, GarageType type, long spawnGameTime, long lastOccupiedGameTime, ResourceKey<Level> dimension, boolean warned` |
| `VehicleFleetSavedData` | персистентность: `Map<UUID, FleetRecord>` (активная техника) + `Map<UUID ownerUUID, Long lastSpawnGameTime>` (кулдаун). Доступ через `level.getDataStorage()` (overworld/боевое измерение как якорь — как у прочих `*SavedData`). |
| `VehicleFleetManager` | вся логика: `canSpawn()`, `register()`, `unregister()`, подсчёт лимитов, реконсиляция/очистка, `onServerTick()`. Статические методы, паттерн как у `GarageManager`. |
| `FleetLimits` | резолв лимитов из `Config` по `GarageType`. |

- Команда владельца: `FrontlineTeams.resolvePlayerTeamId(player)` (может быть null →
  трактуем как «без команды», лимит команды к нему не применяется, только лимит игрока).
- Метка на сущность: `entity.getPersistentData()` в под-тег `"PjmFleet"`
  (ownerUUID, teamId, defId, spawnGameTime). Используется при реконсиляции для
  восстановления записи, если реестр рассинхронизировался.
- Игровое время — `server.overworld().getGameTime()` (монотонно, переживает рестарт).

### Точки интеграции (минимальные правки существующего)

- **`GarageManager.performSpawn`** — перед `type.create(level)`:
  `VehicleFleetManager.canSpawn(player, def, type)` → при отказе сообщение игроку,
  `return` **без снятия техники со склада** (важно: `data.remove(...)` не вызывается).
  После `level.addFreshEntity(entity)` → `VehicleFleetManager.register(entity, player, def, garageType)`
  (пишет запись + метку + обновляет `lastSpawnGameTime` для кулдауна).
- **`GarageManager.handleStoreSelected`** — при возврате в гараж
  `VehicleFleetManager.unregister(entityUUID)` (слот освобождается).
- **`SbwVehicleDestroyMixin.pjm_onVehicleDestroy`** — добавить
  `VehicleFleetManager.unregister(self.getUUID())` (подбитый танк сразу освобождает слот).
- **`PjmServerEvents.onServerTick`** — `VehicleFleetManager.onServerTick(server)`.

### canSpawn — порядок проверок

1. `fleet.enabled == false` → пропустить все проверки (true).
2. **Кулдаун игрока:** `now - lastSpawnGameTime(owner) < spawnCooldownSeconds*20` →
   отказ с остатком времени.
3. **Лимит игрока:** число записей реестра с этим `ownerUUID` и этим `GarageType`
   ≥ `maxActivePerPlayer` → отказ.
4. **Лимит команды:** если у игрока есть команда — число записей с этим `teamId` и
   `GarageType` ≥ `maxActivePerTeam` → отказ. Подсчёт **глобальный** (по всему миру);
   `dimension` в записи хранится, переход на подсчёт по измерению — одна строка фильтра.
5. `-1` в любом лимите = «без ограничения».

Все отказы — переведённые сообщения (`gui.pjmbasemod.fleet.*`) во все 5 языков.

### Реконсиляция и очистка (тик раз в ~20 тиков в `onServerTick`)

Для каждой записи реестра:
1. Резолв сущности по UUID в её `dimension` (`server.getLevel(dim).getEntity(uuid)`).
   Сущность отсутствует / `isRemoved()` / стала wreck (SBW `isWreck`, определяем через
   `SbwVehicleClassifier`/рефлексию или проверку тега) → `unregister` (тихо).
2. Есть пассажиры (`!entity.getPassengers().isEmpty()`) → `lastOccupiedGameTime = now`,
   `warned = false`.
3. Пусто и `now - lastOccupiedGameTime > abandonTimeoutSeconds*20`:
   - вошли в окно `abandonWarningSeconds` до удаления и `!warned` → предупреждение
     владельцу (если онлайн) + actionbar игрокам рядом, `warned = true`;
   - `now - lastOccupiedGameTime > (abandonTimeout + abandonWarning)*20` →
     `entity.discard()` + `unregister`, лог `PjmActionLogger` (категория `GARAGE`).

Техника в выгруженном чанке не тикает и слот не освобождает до загрузки чанка — это
приемлемо (слот честно занят).

### Конфиг — новая секция `fleet` в `Config.java`

```
fleet.enabled                     = true
fleet.maxActivePerTeam            = 8     # -1 = без лимита
fleet.maxActivePerPlayer          = 1
fleet.spawnCooldownSeconds        = 120
fleet.abandonTimeoutSeconds       = 180   # пусто без водителя → старт отсчёта
fleet.abandonWarningSeconds       = 30    # окно предупреждения перед удалением
fleet.aviation.maxActivePerTeam   = 3     # раздельно для AVIATION
fleet.aviation.maxActivePerPlayer = 1
```

Лимиты раздельные для `GROUND` и `AVIATION` (по существующему `GarageType`, тип
резолвится через `SbwVehicleClassifier.classify`). Разделение «танки vs джипы»
отдельным классом — **вне scope** (YAGNI): роль-гейт танков уже есть.

## Что НЕ входит (YAGNI)

- Отдельный лимит по классу техники (танк/джип) — только `GROUND`/`AVIATION`.
- Возврат брошенной техники в гараж вместо удаления (выбрано удаление с
  предупреждением — стимул сдавать технику вручную).
- Подсчёт лимита по измерению (пока глобально, поле `dimension` заложено на будущее).
- Очистка wreck-обломков как отдельная фича (wreck просто снимается с учёта; его
  дальнейшую судьбу решает SBW).
- Клиентский HUD со счётчиком слотов команды (возможное расширение позже).

## Тестирование / верификация

`runClient`/`runServer` в этом каталоге не работают (символ `!` в пути). Верификация:
- `./gradlew compileJava` + `./gradlew compileClientJava` — сборка.
- Валидация JSON-локализации (5 языков).
- Внутриигровую проверку (лимиты, кулдаун, удаление брошенного, рестарт-персистентность)
  выполняет пользователь из пути без `!`.

## Затрагиваемые файлы

**Новые:** `common/fleet/FleetRecord.java`, `VehicleFleetSavedData.java`,
`VehicleFleetManager.java`, `FleetLimits.java`.
**Правки:** `GarageManager.java` (performSpawn, handleStoreSelected),
`SbwVehicleDestroyMixin.java`, `PjmServerEvents.java` (onServerTick), `Config.java`
(секция `fleet`), 5 файлов локализации (`gui.pjmbasemod.fleet.*`).
