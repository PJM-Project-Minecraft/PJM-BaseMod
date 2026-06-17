# Дизайн: Донатные боевые роли (Фича A)

Дата: 2026-06-16
Статус: на ревью
Связанный спек: `2026-06-16-role-locked-equipment-design.md` (Фича B, реализуется после A)

## Проблема

Часть боевых ролей (`CombatRole`) должна быть доступна только за донат (например, `UAV_OPERATOR`, `SSO`). Но один игрок может купить несколько донат-ролей. Требование: **не создавать «киборга»** — игрок не должен получать способности всех купленных ролей одновременно. При этом нужно сохранить «свободу»: купивший волен сам переключаться между своими ролями.

## Ключевое решение: «владение» ≠ «активная роль»

| Понятие | Где хранится | Поведение |
|---|---|---|
| **Владение** ролью | LuckPerms permission node `pjmbasemod.role.unlock.<id>` | Перманентно. Игрок может владеть любым числом ролей |
| **Активная** роль | `RoleSavedData` (`Map<UUID, RoleEntry>`, уже существует) | Всегда **ровно одна**. Переключение = перезапись через `setRole` |

«Киборга» нет по построению: `RoleSavedData` хранит одну активную роль на игрока. Донат расширяет **меню выбора**, а не складывает способности.

Мод уже использует **NeoForge Permission API** (`RolePermissions.can`, `PermissionNode`). LuckPerms автоматически становится его backend'ом — новой зависимости не требуется.

## Компоненты

### 1. Пометка донатности роли — `RoleAccessRegistry`

Новый JSON-реестр в стиле `RoleLimitRegistry`. Файл `config/pjmbasemod/roles/access.json`:

```json
{
  "schemaVersion": 1,
  "roles": {
    "uav_operator": { "paid": true },
    "sso":          { "paid": true },
    "assault":      { "paid": false }
  }
}
```

- Роль, не указанная в файле или `"paid": false` — бесплатная (доступна всем).
- `"paid": true` — требует node `pjmbasemod.role.unlock.<id>`.

Класс: `common/role/RoleAccessRegistry` (синглтон, `get()`, `reload()`, `boolean isPaid(CombatRole)`). Перезагружается на `ServerStartedEvent` рядом с `RoleLimitRegistry.get().reload()` (в `PjmServerEvents.onServerStarted`) и через `/pjm ... reload`.

### 2. Permission nodes — `RolePermissions`

8 ролей фиксированы (enum), поэтому регистрируем 8 статических nodes на `PermissionGatherEvent.Nodes`:

```
pjmbasemod.role.unlock.<id>   // для каждого CombatRole.id()
```

Новый метод проверки владения:

```java
// true, если роль бесплатная ИЛИ у игрока есть node разблокировки
public static boolean canUseRole(ServerPlayer player, CombatRole role)
```

Реализация: `if (!RoleAccessRegistry.get().isPaid(role)) return true;` иначе проверка node через существующий `can(...)`.

### 3. Гибридный поток назначения — `RoleService`

Текущий `assignRole(actor, target, role, checkDistance)` проверяет права `actor` (ADMIN/командир). Добавляем:

- **Проверка владения целью:** после резолва роли — `if (!RolePermissions.canUseRole(target, role))` → отказ с сообщением «роль не разблокирована (доступна за донат)».
- **Самоназначение донат-ролей:** если `actor == target` и `canUseRole(target, role)` — разрешить даже без прав ADMIN/командира. Обычные (не своё назначение) роли — прежняя логика прав командира.
- **Лимит роли** (`RoleLimitRegistry.validateRoleCap`) действует как обычно: донат = право выбрать, не гарантия слота.

Точка входа `assignRoleById(actor, targetId, roleId)` (вызывается из `ServerPacketHandlers.handleSelectRole`) наследует эти проверки.

### 4. Синхронизация разблокированных ролей на клиент

Расширяем синхронизацию роли, чтобы клиент знал, какие роли игроку **доступны для выбора** (для отрисовки замков).

- Расширить `RoleSyncPacket` (добавить `Set<String> unlockedRoles`) **или** новый `RoleAccessSyncPacket(Set<String> unlockedRoles)`. Выбор при реализации; предпочтительно отдельный пакет, чтобы не раздувать частый `RoleSyncPacket`.
- `ClientRoleState` хранит `Set<String> unlockedRoles` + геттер `boolean isUnlocked(CombatRole)`.
- Сервер шлёт набор на логине (`RoleService.sync`/`onPlayerLogin`) и при изменении прав (как минимум на логине; LuckPerms-изменения подхватятся на следующем логине/реконнекте — приемлемо для MVP).
- Бамп `PjmNetworking.VERSION`.

### 5. UI — `RadialMenuScreen`

В подменю выбора роли (`openRoleSubmenu`):

- Для самоназначения: если игрок навёл меню на себя (или открыл «своё» меню), показывать его донат-роли, которыми он владеет, как выбираемые.
- Роли, которыми игрок **не владеет**, показывать заблокированными (иконка замка, тултип «Доступно за донат») — стимул и честность.
- Опираться на `ClientRoleState.isUnlocked(...)`.

### 6. Локализация

Новые ключи во все 5 файлов (`ru_ru`, `en_us`, `uk_ua`, `de_de`, `zh_cn`):
- сообщение «роль не разблокирована»;
- тултип замка «Доступно за донат».

## Затронутые/новые файлы

| Файл | Изменение |
|---|---|
| `common/role/RoleAccessRegistry.java` | **новый** — JSON-реестр донатности |
| `common/role/RolePermissions.java` | +8 unlock-nodes, `canUseRole(...)` |
| `common/role/RoleService.java` | проверка `canUseRole` + гибридное самоназначение |
| `common/event/PjmServerEvents.java` | `RoleAccessRegistry.reload()` на старте + sync доступа на логине |
| `common/network/packet/RoleAccessSyncPacket.java` | **новый** (или расширить `RoleSyncPacket`) |
| `common/network/PjmNetworking.java` | регистрация пакета, бамп `VERSION` |
| `common/network/ClientPacketProxy.java` | +метод для access-sync |
| `client/network/ClientPacketHandlersImpl.java` | реализация access-sync |
| `client/.../client/role/ClientRoleState.java` | хранение `unlockedRoles` |
| `client/gui/RadialMenuScreen.java` | замки на недоступных ролях, самоназначение |
| `common/command/PjmCommands.java` | `reload` подхватывает `RoleAccessRegistry` |
| `lang/*.json` (×5) | новые ключи |

## Verification

- `./gradlew compileJava` + `./gradlew compileClientJava` — компиляция common и client.
- Валидация `access.json` (корректный JSON, известные roleId).
- Внутриигровую проверку (выдача node через LuckPerms, самоназначение, замки в меню) выполняет пользователь из пути без `!`.

## Вне рамок (YAGNI)

- Живое отслеживание изменений прав LuckPerms без релога.
- Отдельные донат-лимиты ролей (решено: лимит как обычно).
- Внутримодовый магазин/выдача нод (это делает донат-плагин/LuckPerms).
