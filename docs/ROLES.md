# Система ролей

Роли назначаются через radial menu: админ или КМД фракции наводится на игрока рядом, открывает `Q -> Роль` и выбирает роль. Обычный игрок видит свою текущую роль в read-only режиме.

При первом успешном входе в систему фракций игрок получает обязательное меню выбора фракции и роли. Выбор сохраняется по UUID; если игрок закрылся до подтверждения, меню будет открываться снова.

Также доступны команды:

```text
/pjm role set <player> <role>
/pjm role clear <player>
/pjm role info [player]
/pjm role list [team]
```

`set` и `clear` доступны OP/permission `pjmbasemod.role.admin`; активный КМД может назначать роли только бойцам своей текущей scoreboard-фракции.

## Лимиты ролей

Лимиты задаются по каждой фракции в JSON:

```json
{
  "schemaVersion": 1,
  "teams": {
    "team1": {
      "assault": -1,
      "machine_gunner": 3,
      "sniper": 2,
      "uav_operator": 1,
      "sso": 1,
      "pilot": 2,
      "ew_specialist": 1,
      "crew": 4
    }
  }
}
```

Файл: `config/pjmbasemod/roles/limits.json`. Если файла нет, мод создаёт пример для всех `teams.definitions`.

- `-1` или отсутствующая роль — без лимита.
- `0` — роль отключена для этой фракции.
- `>0` — максимум игроков этой роли в выбранной scoreboard-фракции.

Лимиты проверяются сервером для первого выбора, radial menu, `/pjm role set` и меню управления КМД.

**Оффлайн-игроки занимают слоты.** Роль персистентна (`RoleSavedData`) и переживает выход,
поэтому счётчик `RoleService.roleCount` считает по сохранённым данным, а не по списку онлайна:
иначе вышедшие игроки освобождали бы слоты, на них выдавались новые роли, и после возвращения
лимит оказывался бы превышен. Слот освобождается только явным снятием роли (`/pjm role set … none`,
меню КМД) или сменой фракции игроком — устаревшую запись чистит `RoleService.cleanupInvalidFor`
при его следующем входе.

## Role ids

| Id | Название |
|----|----------|
| `assault` | Штурмовик |
| `machine_gunner` | Пулеметчик |
| `sniper` | Снайпер |
| `uav_operator` | Оператор БПЛА |
| `sso` | ССО |
| `pilot` | Пилот |
| `ew_specialist` | Специалист РЭБ |
| `crew` | Экипаж |

Роль сохраняется по UUID игрока вместе с текущей фракцией. Если игрок сменил scoreboard-команду или вышел из настроенной фракции, роль автоматически снимается.

## Ограничение склада

```json
{
  "id": "svdm",
  "displayName": "СВДМ",
  "itemId": "superbwarfare:svd",
  "pool": "weapon",
  "displayCategory": "weapon",
  "allowedRoles": ["sniper"],
  "pointCost": 3,
  "maxPerWithdraw": 1
}
```

Предметы без `allowedRoles` или с пустым списком доступны всем. Сдача предметов на склад не блокируется ролью.

## Ограничение техники

```json
{
  "id": "m1a2",
  "displayName": "M1A2 Abrams",
  "entityType": "superbwarfare:m_1a_2",
  "icon": "minecraft:iron_block",
  "category": "tank",
  "assemblyTime": 0,
  "allowedRoles": ["crew"],
  "cost": [
    { "item": "minecraft:iron_block", "count": 16 },
    { "item": "minecraft:redstone_block", "count": 8 }
  ]
}
```

Для техники роль проверяется на сборке, спавне, загрузке обратно в гараж и переработке.
