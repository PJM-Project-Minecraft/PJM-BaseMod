# Зоны базы (BaseZone) — дизайн

**Дата:** 2026-07-04
**Статус:** утверждён (дизайн), ожидает ревью спека

## Задача

Дать возможность объявить зону базы, привязанную к команде-владельцу. Когда
игрок вражеской команды залетает/заходит в чужую зону базы, ему на весь экран
показывается предупреждение с обратным отсчётом: он должен покинуть зону, иначе
через 5 секунд его убивает. OP имеет приоритет — проходит свободно.

## Ключевые решения (из брейншторма)

- **Отдельная подсистема** `common/basezone/`, а не расширение `Region`. Причина:
  существующий `Region` хранит границы в **чанках без Y** и завязан на chunk-based
  frontline (захват территории, синхронизация карты). Зона базы требует **блочной
  точности + ограничения по высоте (Y)**, поэтому смешивать нельзя.
- **Границы — точный AABB в блоках**, включая Y (`min/max` по каждой оси).
- **Владелец зоны** — `owner` = teamId (резолв через `FrontlineTeams`). Игрок
  команды-владельца входит свободно. **Все остальные — враги**, включая игроков
  без команды / нейтралов.
- **Реакция на врага:** полноэкранный титр + отсчёт 5с → смерть.
- **Отсчёт сбрасывается при выходе** из зоны; повторный вход — отсчёт с начала.
- **OP-приоритет:** OP (`hasPermissions(2)`), а также creative/spectator —
  полностью игнорируют защиту (админ-осмотр).
- **Смерть:** кастомный `DamageSource` со своим death-сообщением
  («погиб в зоне вражеской базы»).

## Архитектура

Новая подсистема `common/basezone/` по шаблону подсистем мода
(`*Manager` + `*SavedData`), но **без** `*Registry`/`*Snapshot`/`*Permissions` и
**без клиентского state-класса** — enforcement полностью серверный, титры шлются
ванильными пакетами.

### `BaseZone`

Данные одной зоны:

- `String name` — неизменяемый внутренний id (lowercased для ключей карты).
- `String displayName` — человекочитаемое имя.
- `String dimension` — id размерности (напр. `minecraft:overworld`), пусто до
  первой заданной точки.
- `Integer minX, minY, minZ, maxX, maxY, maxZ` — точный AABB в **блочных**
  координатах (nullable = не задано). Нормализуются при чтении границ.
- `String owner` — teamId команды-владельца (пусто = не назначен).

Методы:

- конструктор `BaseZone(String name)`;
- `name()`, `displayName()`, `setDisplayName(String)`, `dimension()`;
- `owner()`, `setOwner(String)`;
- `void setPos1(String dimension, BlockPos)` — привязывает размерность + первый угол;
- `void setPos2(String dimension, BlockPos)` — второй угол (размерность ставит,
  только если ещё пусто);
- `boolean isComplete()` — true, когда заданы размерность + все 6 координат + owner;
- `int minX()/minY()/minZ()/maxX()/maxY()/maxZ()` — нормализованные границы;
- `boolean contains(String dimension, BlockPos pos)` — блочная проверка вхождения,
  учитывает размерность и Y;
- `CompoundTag save()` / `static BaseZone load(CompoundTag)` — NBT-сериализация.

### `BaseZoneSavedData`

`extends SavedData`, хранится на data storage оверворлда под именем
`pjmbasemod_basezones`. Держит `LinkedHashMap<String, BaseZone>` по lowercased-имени.

- `static BaseZoneSavedData get(MinecraftServer)`;
- `Collection<BaseZone> zones()`;
- `BaseZone zone(String name)` — поиск по имени;
- `BaseZone getOrCreateZone(String name)` — создаёт при отсутствии, помечает dirty;
- `boolean deleteZone(String name)`;
- `BaseZone findZoneAt(String dimension, BlockPos pos)` — обратный поиск: первая
  **завершённая** зона, содержащая точку (для enforcement).

### `BaseZoneManager`

Вся enforcement-логика. Держит `Map<UUID, Integer> countdownTicks` — оставшиеся
тики отсчёта для игроков с активным предупреждением.

- `static void onPlayerTick(ServerPlayer player)` — вызывается из
  `PjmServerEvents.onPlayerTick`.
- `static void onPlayerLogout(ServerPlayer player)` — очистка состояния отсчёта.
- Внутренние помощники: старт/сброс отсчёта, отправка титра, снятие титра, смерть.

Алгоритм `onPlayerTick`:

1. Если система выключена в конфиге (`baseZone.enabled == false`) — выходим.
2. **Bypass:** если `player.hasPermissions(2)` **или** режим creative/spectator —
   если был активный отсчёт, отменяем и снимаем титр; выходим.
3. `zone = findZoneAt(player.level().dimension(), player.blockPosition())`.
4. Если зоны нет **или** `zone.owner()` == команда игрока
   (`FrontlineTeams.resolvePlayerTeamId`) → отменяем отсчёт (если был) + снимаем
   титр; выходим.
5. Игрок-враг внутри чужой зоны:
   - если отсчёта ещё нет — стартуем: `countdownTicks = baseZone.countdownSeconds * 20`;
   - каждую **секунду** (когда `remaining % 20 == 0`) шлём полноэкранный титр:
     заголовок = локализованное «⚠ Чужая база», подзаголовок =
     «Покиньте зону! Смерть через N…» (N = оставшиеся секунды);
   - декрементим `remaining`; при `remaining <= 0` → наносим смертельный урон
     кастомным `DamageSource`, очищаем состояние.

Титры — ванильные `ClientboundSetTitlesAnimationPacket` (times, чтобы не гас между
обновлениями) + `ClientboundSetTitleTextPacket` + `ClientboundSetSubtitleTextPacket`.
Снятие — `ClientboundClearTitlesPacket`. **Нового пакета мода и клиентского
state-класса не требуется.**

Смерть: кастомный `DamageSource` (например через `DamageSources`/кастомный
`DamageType` в datapack, либо `player.hurt(...)` с достаточным уроном и generic-
источником) так, чтобы в чат ушло death-сообщение «погиб в зоне вражеской базы».

### Интеграция

- `PjmServerEvents.onPlayerTick(PlayerTickEvent.Post)` — добавить вызов
  `BaseZoneManager.onPlayerTick(player)` в список тик-сервисов.
- Логаут игрока (`PlayerLoggedOutEvent` или существующий хук) →
  `BaseZoneManager.onPlayerLogout` для очистки `countdownTicks`.

### Команды

Поддерево `/pjm basezone ...` в `PjmCommands` (permission level 2), рядом с
`/pjm region`:

- `create <name> [displayName]` — создать зону;
- `delete <name>` — удалить;
- `list` — список зон;
- `info <name>` — детали (границы, размерность, владелец, complete?);
- `pos1 <name>` / `pos2 <name>` — берут точную `blockPosition()` игрока (вкл. Y)
  и его размерность;
- `owner <name> <team>` — назначить команду-владельца (валидация через
  `FrontlineTeams.exists`);
- `displayname <name> <displayName>` — сменить отображаемое имя.

### Конфиг

Новая секция `baseZone`:

- `enabled` (bool, дефолт `true`) — вкл/выкл всю систему;
- `countdownSeconds` (int, дефолт `5`) — время до смерти после входа врага.

### Локализация

Новые ключи — **во все 5 языков** (`ru_ru`, `en_us`, `uk_ua`, `de_de`, `zh_cn`):

- заголовок и подзаголовок титра предупреждения (с плейсхолдером `%s`/`%d` для N);
- death-сообщение кастомного `DamageSource`;
- сообщения команд `/pjm basezone` (создано/удалено/не найдено/владелец назначен/
  границы заданы/список/инфо).

## Персистентность

Ванильный `SavedData` (`BaseZoneSavedData`, имя `pjmbasemod_basezones`) на data
storage оверворлда, по образцу `RegionSavedData`. `countdownTicks` в
`BaseZoneManager` — **рантайм-состояние, не персистится** (при рестарте отсчёт
просто начинается заново при входе врага).

## Верификация

- `./gradlew compileJava` — сборка common.
- `./gradlew compileClientJava` — сборка client (в этой фиче клиентских классов
  нет, но проверяем целостность).
- Валидация JSON пяти langs.
- Внутриигровую проверку (вход врага → титр → отсчёт → смерть; выход → сброс;
  OP/creative байпас; владелец входит свободно) выполняет пользователь из пути
  без `!` (dev-лаунчер не стартует из `!Curseforge Mods`).

## Вне области (YAGNI)

- Отображение зон базы на карте (JourneyMap/BlueMap) — не требуется.
- Клиентский state-класс и sync-пакеты — не нужны (enforcement серверный).
- Права заместителей/командиров на управление зонами — только OP (level 2).
- Несколько владельцев у одной зоны — одна команда-владелец.
