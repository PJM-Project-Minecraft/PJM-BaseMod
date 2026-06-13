# Debug-команды открытия меню

## Цель
Дать администратору (OP 2) принудительно открывать GUI-меню у произвольного игрока для отладки.

## Объём (одобрено)
Два меню: **выбор фракции** (FactionSelectionScreen) и **управление фракцией**
(FactionManagementScreen). Цель — селектор игрока с дефолтом на исполнителя.

## Команды
Новая ветка под `/pjm`, требует `source.hasPermission(2)`:

- `/pjm debug open faction_selection [target]`
- `/pjm debug open faction_management [target]`

`target` — `EntityArgument.player()`, опционален; по умолчанию исполнитель (через
`requirePlayer`). Если исполнитель не игрок и цель не указана — ошибка.

## Изменения в коде

### FactionMenuService (common/faction)
Два новых публичных метода (команда не лезет в приватные снапшоты):

- `debugOpenSelection(ServerPlayer target)` — шлёт `OpenFactionSelectionPacket`
  со снапшотом `required=false` (экран закрываемый, в отличие от принудительного
  первого выбора).
- `debugOpenManagement(ServerPlayer target) -> boolean` — резолвит команду через
  `FrontlineTeams.resolvePlayerTeamId(target)` напрямую, **минуя** проверку прав
  `managementTeam()`. Шлёт `OpenFactionManagementPacket` с editable-снапшотом
  (полный доступ — debug). Возвращает `false`, если у цели нет команды.

Для `debugOpenSelection` приватный `selectionSnapshot(player, required)` уже
параметризован — переиспользуется. Для management — переиспользуется приватный
`managementSnapshot(actor, team)`.

### PjmCommands (common/command)
- `debugCommand()` — литерал `debug` → `open` → два под-литерала с опциональным
  аргументом `target`.
- `.then(debugCommand())` в `register()` рядом с админскими ветками.

Feedback — исполнителю через `sendSuccess`/`sendFailure`, литералами на русском
(debug-ветка, ключи локализации не плодим). Целевому игроку — только сам экран.

## Сеть
Новых пакетов нет. Переиспользуются `OpenFactionSelectionPacket`,
`OpenFactionManagementPacket`. `PjmNetworking.VERSION` не бампается.

## Проверка
`./gradlew compileJava` + `./gradlew compileClientJava`. Внутриигровую проверку
делает пользователь из пути без `!`.
