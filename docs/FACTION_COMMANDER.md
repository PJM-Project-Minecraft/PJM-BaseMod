# КМД фракции

Роль `КОМАНДИР ФРАКЦИИ` (`КМД`) хранится отдельно от XP-званий. Фракции берутся из `teams.definitions` в `config/pjmbasemod-common.toml` и сопоставляются с текущими scoreboard-командами игроков.

## Команды

```text
/pjm faction commander set <team> <player>
/pjm faction commander clear <team>
/pjm faction commander info [team]
/pjm faction commander list
/pjm faction manage
```

`set` и `clear` требуют permission `pjmbasemod.faction.commander.admin`; fallback без backend'а прав — OP level 2. В одной фракции может быть только один КМД. Назначаемый игрок должен уже состоять в выбранной scoreboard-фракции.

`/pjm faction manage` открывает отдельный экран управления ролями онлайн-бойцов своей текущей фракции. Доступ есть у активного КМД и у игроков с `pjmbasemod.role.admin`; все назначения проходят через серверную проверку фракции и лимитов ролей.

Если назначенный командир переходит в другую scoreboard-команду или выходит из настроенной фракции, назначение автоматически снимается при login/tick/info/list.

## Отображение

Активный КМД получает префикс `[КМД]` в TAB. Если включены XP-звания, формат становится:

```text
[КМД] [MAJ] PlayerName
```

У самого игрока роль также отображается второй строкой в бейдже звания в инвентаре.
