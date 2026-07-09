# Событие «Радиоразведка» (`signal_hunt`)

Серверное событие: в зоне прячется N невидимых «маяков». Игроки со
**Радио-детектором** в руке ищут их по actionbar-индикации (направление + сила
сигнала). Найдя маяк, игрок запускает перехват ПКМ по детектору; удержание
позиции даёт XP именно нашедшему. Завершается, когда все маяки перехвачены (или
таймаут).

> Это второй тип серверного события после «налёта дронов». Типы диспетчеризируются
> через `ServerEventType` — менеджер агностичен к конкретным классам событий.

## Состав подсистемы (`common/serverevent/`)

| Файл | Назначение |
|------|------------|
| `SignalHuntDefinition.java` | Gson-модель конфига: `SignalHuntZone` + `SignalHuntSettings` + `BeaconSnapshot` |
| `SignalHuntRegistry.java` | JSON-реестр зон/параметров из `config/pjmbasemod/events/signal_hunt.json` |
| `SignalHuntEvent.java` | Реализация `ServerEvent` (`typeId="signal_hunt"`): маяки, канал перехвата, HUD-данные |
| `SignalHuntEventType.java` | Фабрика типа (`create`/`load`/`available`) — без внешних мод-зависимостей |
| `SignalHuntService.java` | Серверный тик детектора: каждые 5 тиков шлёт `SignalHuntHudPacket` держателям |

## Предмет и клиент

- `common/item/RadioDetectorItem.java` — `Item implements GeoItem` (GeckoLib). `use()`
  запускает канал перехвата через активный `SignalHuntEvent`.
- Регистрация в `PjmItems.RADIO_DETECTOR` + креативная вкладка `main`.
- BEWLR-рендер: `client/renderer/item/RadioDetectorModel` + `RadioDetectorRenderer`,
  подключается через `RegisterClientExtensionsEvent` в `ClientInit`.
- Geo-ассеты (placeholder, **заменить на боевую модель**):
  `assets/pjmbasemod/geo/item/radio_detector.geo.json`,
  `assets/pjmbasemod/animations/item/radio_detector.animation.json`.
  Текстура `assets/pjmbasemod/textures/item/radio_detector.png` — **отсутствует,
  предоставляется пользователем**.

## Сеть (`PjmNetworking` VERSION = **27**)

- `SignalHuntHudPacket` (S→C): `active, signalStrength, direction, captureReady,
  captureProgress, captureSeconds, capturedCount, beaconCount`. Factory
  `inactive()` / `searching(...)`.
- `ClientPacketProxy.signalHuntHud()` (default noop) + реализация в
  `ClientPacketHandlersImpl` → `ClientSignalHuntState`.

## Actionbar-HUD

Сервер (`SignalHuntService.onPlayerTick`, вызывается из `PjmServerEvents.onPlayerTick`)
для каждого игрока с детектором в руке считает сигнал ближайшего маяка и шлёт
пакет. Клиент `SignalHuntActionBarHud.tick` (из `ClientEvents.onClientTick`)
формирует actionbar через `mc.gui.setOverlayMessage(component, false)`:

```
◈ Сигнал ▮▮▮▮▯▯▯▯ ▲  [2/3]
```

- Полоса `▮/▯` — сила сигнала (зелёная→жёлтая→красная по мере приближения).
- Стрелка `▲ ◥ ▶ ◢ ▼ ◣ ◀ ◤` — направление к маяку относительно взгляда (octant).
- При `captureReady` (в радиусе захвата): `ПКМ — ПЕРЕХВАТ` + прогресс-бар канала.
- `[N/total]` — перехвачено маяков.

Когда детектор не в руке или нет свежего пакета (>1с) — actionbar не трогается
(не затирает voice-chat HUD и прочее).

## Механика перехвата

1. Игрок входит в `captureRadius` маяка (≤ `signalRadius`, где сигнал = 100%).
2. ПКМ по детектору → `SignalHuntEvent.startCapture(player)` запускает канал:
   запоминается игрок + индекс маяка, прогресс = 0.
3. В `tick()` событие инкрементирует прогресс, пока игрок в `captureRadius` и
   держит детектор. Выход из радиуса / смена предмета — сброс канала.
4. При `captureSeconds` накопленных секунд — маяк перехвачен, XP начисляется
   **именно этому игроку** (`RankService.addXp`, reason `event_signal_hunt`),
   нотификация всем, маяк снимается.
5. Конкуренция: кто последним нажал ПКМ — тот ведёт канал (маяк один, активный
   канал один). Побеждает первый, дошедший до завершения.

## Конфиг `config/pjmbasemod/events/signal_hunt.json`

```json
{
  "schemaVersion": 1,
  "settings": {
    "signalRadius": 20,         // радиус 100% сигнала вокруг маяка (блоки)
    "signalMaxDistance": 400,  // дальность, за которой сигнал = 0
    "captureRadius": 6,         // радиус захвата (<= signalRadius)
    "captureSeconds": 5,        // время удержания для перехвата
    "xpPerBeacon": 30,          // XP за маяк нашедшему
    "maxDurationMinutes": 20    // таймаут события
  },
  "zones": [
    {
      "name": "Полигон Альфа",
      "dimension": "minecraft:overworld",
      "centerX": 250, "centerY": 70, "centerZ": -180,
      "radius": 200,       // радиус зоны для карты/разброса маяков
      "beaconCount": 3,    // маяков в зоне
      "beaconSpread": 150  // разброс маяков от центра (<= radius)
    }
  ]
}
```

## Команды (`/pjm event`)

```
/pjm event start                       — случайный тип события, случайная точка/зона
/pjm event start signal_hunt           — радиоразведка, случайная зона
/pjm event start signal_hunt <зона>    — конкретная зона
/pjm event start drone_raid [точка]    — налёт дронов
/pjm event stop | status | reload      — стандартные
```

Tab-комплит: типы (`drone_raid`, `signal_hunt`) + точки/зоны. Перезагрузка
`/pjm event reload` (секция `events`) перечитывает оба конфига.

## Автозапуск

`Config.events.enabled` включает планировщик. При срабатывании выбирается
случайный доступный тип (равные веса): `signal_hunt` доступен всегда, `drone_raid`
— только если загружен WRBDrones. `requireCaptureInactive` — не запускать, пока
идёт захват фронтлайна.

## Что осталось сделать (placeholder)

- **Текстура детектора** `textures/item/radio_detector.png` — пользователь заменит
  placeholder-геометрию (`geo/item/radio_detector.geo.json`) и текстуру на боевую.
- При необходимости — визуальный маркер маяка (entity/частицы) — сейчас маяк
  невидим по спеке.
