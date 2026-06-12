# Конфиг склада

Предметы, доступные у кладовщика, настраиваются в серверном файле:

```text
config/pjmbasemod/warehouse/items.json
```

Если файла нет, мод создаст пример при старте сервера. После правок можно перезагрузить склад без рестарта:

```text
/pjm warehouse reload
```

## Формат

```json
{
  "schemaVersion": 1,
  "items": [
    {
      "id": "ak74m",
      "displayName": "АК-74М",
      "itemId": "superbwarfare:ak_47",
      "pool": "weapon",
      "displayCategory": "weapon",
      "allowedRoles": ["assault", "sso"],
      "pointCost": 1,
      "maxPerWithdraw": 8,
      "refundValue": 1
    },
    {
      "id": "medkit",
      "displayName": "Аптечка",
      "itemId": "minecraft:golden_apple",
      "pool": "supply",
      "displayCategory": "medicine",
      "pointCost": 1,
      "maxPerWithdraw": 16,
      "refundValue": 1
    }
  ]
}
```

Поля:

| Поле | Что значит |
|------|------------|
| `id` | Уникальный id записи склада. Можно писать латиницей, цифрами, `_` и `-`; остальное будет нормализовано. |
| `displayName` | Название в GUI склада. |
| `itemId` | Registry id предмета, например `minecraft:iron_ingot` или `superbwarfare:ak_47`; при установленном TACZ можно указать gunpack id, например `tacz:ak47` или `tacz:762x39`. |
| `pool` | Пул очков: `weapon`, `supply`, `equipment`, `raw`, `special`. |
| `displayCategory` | Вкладка GUI: `weapon`, `ammo`, `food`, `medicine`, `equipment`, `raw`, `vehicle`, `special`. |
| `allowedRoles` | Необязательный список ролей, которым разрешено получать предмет. Если поля нет или список пустой — предмет общий. |
| `pointCost` | Стоимость одной штуки при получении. Минимум `1`. |
| `maxPerWithdraw` | Максимум за одно получение. Если поставить `0`, мод заменит на дефолт `16`. |
| `refundValue` | Сколько очков возвращается за сдачу одной штуки. Если поля нет, возврат равен `pointCost`; если `0`, предмет нельзя сдавать. |

Старый формат `config/pjmbasemod/warehouse/items/<id>.json` тоже поддерживается. Если один и тот же `id` есть и в старой папке, и в `items.json`, запись из `items.json` заменит старую.

## Роли

Поле `allowedRoles` принимает canonical id ролей:

```text
assault, machine_gunner, sniper, uav_operator, sso, marksman, ew_specialist, crew
```

Пример снайперского предмета:

```json
{
  "id": "svdm",
  "displayName": "СВДМ",
  "itemId": "superbwarfare:svd",
  "pool": "weapon",
  "displayCategory": "weapon",
  "allowedRoles": ["sniper", "marksman"],
  "pointCost": 3,
  "maxPerWithdraw": 1,
  "refundValue": 1
}
```

Если в `allowedRoles` указаны только неизвестные роли, запись будет пропущена при загрузке, а причина появится в логе сервера.

## TACZ

Для TACZ предметы в конфиге указываются через их gunpack id:

```json
{
  "id": "ak47_tacz",
  "displayName": "AK-47",
  "itemId": "tacz:ak47",
  "pool": "weapon",
  "displayCategory": "weapon",
  "pointCost": 2,
  "maxPerWithdraw": 1,
  "refundValue": 1
}
```

Патроны работают так же:

```json
{
  "id": "ammo_762x39_tacz",
  "displayName": "7.62x39",
  "itemId": "tacz:762x39",
  "pool": "supply",
  "displayCategory": "ammo",
  "pointCost": 1,
  "maxPerWithdraw": 60,
  "refundValue": 1
}
```

Если старый конфиг содержит `superbwarfare:ak_47`, а самого SuperbWarfare-предмета нет, склад попробует выдать TACZ `tacz:ak47`.

## Ящики поставок

Ящики настраиваются отдельно в:

```text
config/pjmbasemod/warehouse/crates/
```

Стандартные типы:

| Ящик | Пул | Очки |
|------|-----|------|
| `weapon_crate` | `weapon` | 3 |
| `supply_crate` | `supply` | 5 |
| `equipment_crate` | `equipment` | 4 |
| `raw_crate` | `raw` | 5 |
| `special_crate` | `special` | 2 |
