# Wipe System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Дать OP-администратору команду `/pjm wipe` для сброса сезонного прогресса игроков — звания одной команды, звания всех, или полный вайп прогресса — не трогая админ-разметку карты.

**Architecture:** Новый пакет `common/wipe/` с классом-оркестратором `WipeService`. Каждому затрагиваемому `SavedData` добавляется метод массовой очистки. Дерево команд `/pjm wipe ...` живёт в `common/command/WipeCommands` (по образцу `WarehouseCommands.build()`), с in-memory подтверждением, и подключается в `PjmCommands`.

**Tech Stack:** Java 21, NeoForge 21.1.172, Minecraft 1.21.1, Brigadier (команды), ванильный `SavedData` (персистентность), `Component.translatable` (локализация).

## Global Constraints

- **Верификация — только компиляцией.** `runClient`/`runServer` не работают из этого пути (символ `!`). После каждой задачи: `./gradlew compileJava` (и `compileClientJava`, если задача трогает клиент/ресурсы). Юнит-тестов в проекте нет.
- **`main` НИКОГДА не импортирует `client`.** Весь код этого плана — в `src/main` (common), клиентских импортов быть не должно.
- **Локализация — во все 5 языков:** `ru_ru.json`, `en_us.json`, `uk_ua.json`, `de_de.json`, `zh_cn.json` в `src/client/resources/assets/pjmbasemod/lang/`.
- **Все `SavedData` в моде получают через `X.get(MinecraftServer server)`** (overworld data storage). Метод очистки должен звать `setDirty()` только при реальном изменении.
- **Не трогается ни при каком вайпе:** `RegionSavedData`, `BaseZoneSavedData`, `WarehouseSettingsSavedData`, `GarageTerminalSavedData`, `SkinSavedData`, `ModerationSavedData`, ключи гаражей, JSON-регистры.
- Русский — язык комментариев и сообщений в чате.

---

## File Structure

**Новые файлы:**
- `src/main/java/ru/liko/pjmbasemod/common/wipe/WipeService.java` — оркестратор вайпа + резолв членов команды + ре-синхронизация онлайн-игроков.
- `src/main/java/ru/liko/pjmbasemod/common/command/WipeCommands.java` — дерево `/pjm wipe`, подтверждение.

**Модифицируемые файлы:**
- 11 `SavedData`-классов — добавить методы массовой очистки.
- `src/main/java/ru/liko/pjmbasemod/common/command/PjmCommands.java` — подключить `WipeCommands.build()`.
- 5 lang-файлов — ключи `pjmbasemod.wipe.*`.

---

## Task 1: Методы массовой очистки в SavedData

**Files:**
- Modify: `src/main/java/ru/liko/pjmbasemod/common/rank/RankSavedData.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/warehouse/WarehouseSavedData.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/warehouse/WarehousePersonalBudgetSavedData.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/garage/GarageSavedData.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/fleet/VehicleFleetSavedData.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/frontline/FrontlineSavedData.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/faction/FactionSelectionSavedData.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/faction/FactionCommanderSavedData.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/faction/FactionDeputySavedData.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/faction/FactionOrderSavedData.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/role/RoleSavedData.java`

**Interfaces:**
- Produces (используется в Task 2):
  - `RankSavedData.clearAll()`, `RankSavedData.clearPlayers(java.util.Set<java.util.UUID> players)`
  - `WarehouseSavedData.clearAll()`
  - `WarehousePersonalBudgetSavedData.clearAll()`
  - `GarageSavedData.clearVehicles()`
  - `VehicleFleetSavedData.clearAll()`
  - `FrontlineSavedData.clearAll()`
  - `FactionSelectionSavedData.clearAll()`
  - `FactionCommanderSavedData.clearAll()`
  - `FactionDeputySavedData.clearAll()`
  - `FactionOrderSavedData.clearAll()`
  - `RoleSavedData.clearAll()`

- [ ] **Step 1: `RankSavedData` — добавить `clearAll()` и `clearPlayers(...)`**

Поле: `private final Map<UUID, Integer> xpByPlayer`. Добавить рядом с существующим `reset(UUID)`:

```java
/** Полный сброс XP у всех игроков. */
public void clearAll() {
    if (!xpByPlayer.isEmpty()) {
        xpByPlayer.clear();
        setDirty();
    }
}

/** Сброс XP только у указанных игроков (по UUID). */
public void clearPlayers(java.util.Set<UUID> players) {
    boolean changed = false;
    for (UUID id : players) {
        if (xpByPlayer.remove(id) != null) changed = true;
    }
    if (changed) setDirty();
}
```

- [ ] **Step 2: `WarehouseSavedData` — `clearAll()`**

Поле: `private final Map<String, EnumMap<WarehousePoolCategory, Integer>> stock`.

```java
/** Сброс накопленных очков всех складов (админ-разметка зон приёма не трогается). */
public void clearAll() {
    if (!stock.isEmpty()) {
        stock.clear();
        setDirty();
    }
}
```

- [ ] **Step 3: `WarehousePersonalBudgetSavedData` — `clearAll()`**

Поле: `private final Map<UUID, Entry> entries`.

```java
/** Сброс личных бюджетов всех игроков. */
public void clearAll() {
    if (!entries.isEmpty()) {
        entries.clear();
        setDirty();
    }
}
```

- [ ] **Step 4: `GarageSavedData` — `clearVehicles()`**

Поле: `private final Map<String, List<StoredVehicle>> garages`. Ключи (гаражи как админ-разметка) сохраняем, опустошаем только списки машин.

```java
/** Опустошает хранимую технику во всех гаражах, сами гаражи (ключи) сохраняются. */
public void clearVehicles() {
    boolean changed = false;
    for (List<StoredVehicle> list : garages.values()) {
        if (!list.isEmpty()) {
            list.clear();
            changed = true;
        }
    }
    if (changed) setDirty();
}
```

- [ ] **Step 5: `VehicleFleetSavedData` — `clearAll()`**

Поля: `private final Map<UUID, FleetRecord> active`, `private final Map<UUID, Long> lastSpawn`.

```java
/** Сброс реестра активной техники и кулдаунов спавна. */
public void clearAll() {
    if (!active.isEmpty() || !lastSpawn.isEmpty()) {
        active.clear();
        lastSpawn.clear();
        setDirty();
    }
}
```

- [ ] **Step 6: `FrontlineSavedData` — `clearAll()`**

Поля: `private final Map<FrontlineChunkKey, FrontlineChunkState> chunks`, `private final Map<FrontlineSectorKey, FrontlineSectorState> sectors`.

```java
/** Сброс всех захватов: чанки и сектора возвращаются в нейтраль. */
public void clearAll() {
    if (!chunks.isEmpty() || !sectors.isEmpty()) {
        chunks.clear();
        sectors.clear();
        setDirty();
    }
}
```

- [ ] **Step 7: `FactionSelectionSavedData` — `clearAll()`**

Поле: `private final Map<UUID, SelectionEntry> selectionsByPlayer`.

```java
/** Сброс выбора фракции у всех игроков. */
public void clearAll() {
    if (!selectionsByPlayer.isEmpty()) {
        selectionsByPlayer.clear();
        setDirty();
    }
}
```

- [ ] **Step 8: `FactionCommanderSavedData` — `clearAll()`**

Поле: `private final Map<String, CommanderEntry> commandersByTeam`.

```java
/** Снять всех командиров фракций. */
public void clearAll() {
    if (!commandersByTeam.isEmpty()) {
        commandersByTeam.clear();
        setDirty();
    }
}
```

- [ ] **Step 9: `FactionDeputySavedData` — `clearAll()`**

Поле: `private final Map<String, Map<UUID, Integer>> deputiesByTeam`.

```java
/** Снять всех заместителей фракций. */
public void clearAll() {
    if (!deputiesByTeam.isEmpty()) {
        deputiesByTeam.clear();
        setDirty();
    }
}
```

- [ ] **Step 10: `FactionOrderSavedData` — `clearAll()`**

Поле: `private final Map<String, OrderEntry> ordersByTeam`.

```java
/** Убрать приказы у всех фракций. */
public void clearAll() {
    if (!ordersByTeam.isEmpty()) {
        ordersByTeam.clear();
        setDirty();
    }
}
```

- [ ] **Step 11: `RoleSavedData` — `clearAll()`**

Поле: `private final Map<UUID, RoleEntry> rolesByPlayer`.

```java
/** Сброс боевых ролей у всех игроков. */
public void clearAll() {
    if (!rolesByPlayer.isEmpty()) {
        rolesByPlayer.clear();
        setDirty();
    }
}
```

- [ ] **Step 12: Компиляция**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. Если падает — проверь, что имена полей совпадают с реальными (напр. `List`/`Map` уже импортированы в каждом файле, `StoredVehicle` уже используется в `GarageSavedData`).

- [ ] **Step 13: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/rank/RankSavedData.java \
        src/main/java/ru/liko/pjmbasemod/common/warehouse/WarehouseSavedData.java \
        src/main/java/ru/liko/pjmbasemod/common/warehouse/WarehousePersonalBudgetSavedData.java \
        src/main/java/ru/liko/pjmbasemod/common/garage/GarageSavedData.java \
        src/main/java/ru/liko/pjmbasemod/common/fleet/VehicleFleetSavedData.java \
        src/main/java/ru/liko/pjmbasemod/common/frontline/FrontlineSavedData.java \
        src/main/java/ru/liko/pjmbasemod/common/faction/FactionSelectionSavedData.java \
        src/main/java/ru/liko/pjmbasemod/common/faction/FactionCommanderSavedData.java \
        src/main/java/ru/liko/pjmbasemod/common/faction/FactionDeputySavedData.java \
        src/main/java/ru/liko/pjmbasemod/common/faction/FactionOrderSavedData.java \
        src/main/java/ru/liko/pjmbasemod/common/role/RoleSavedData.java
git commit -m "feat(wipe): методы массовой очистки в 11 SavedData"
```

---

## Task 2: WipeService (оркестратор + резолв команды + ре-синхронизация)

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/wipe/WipeService.java`

**Interfaces:**
- Consumes (из Task 1): методы `clearAll()`/`clearPlayers(...)`/`clearVehicles()` перечисленных `SavedData`.
- Consumes (существующие sync-методы):
  - `RankService.syncAll(MinecraftServer)`, `RoleService.syncAll(MinecraftServer)`, `FactionCommanderService.syncAll(MinecraftServer)`
  - `FrontlineManager.sendInitialSync(ServerPlayer)`, `FactionOrderManager.syncTo(ServerPlayer)`, `FactionMenuService.onPlayerLogin(ServerPlayer)`
  - `FrontlineBlueMapService.requestSync(String)`
- Produces (используется в Task 3):
  - `WipeService.wipeRanks(MinecraftServer server)` → void
  - `WipeService.wipeRanksForTeam(MinecraftServer server, java.util.Set<java.util.UUID> members)` → void
  - `WipeService.wipeAll(MinecraftServer server)` → void
  - `WipeService.resolveTeamMemberUuids(MinecraftServer server, String teamId)` → `java.util.Set<java.util.UUID>`

- [ ] **Step 1: Создать `WipeService.java`**

```java
package ru.liko.pjmbasemod.common.wipe;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.scores.PlayerTeam;
import ru.liko.pjmbasemod.common.faction.FactionCommanderSavedData;
import ru.liko.pjmbasemod.common.faction.FactionCommanderService;
import ru.liko.pjmbasemod.common.faction.FactionDeputySavedData;
import ru.liko.pjmbasemod.common.faction.FactionMenuService;
import ru.liko.pjmbasemod.common.faction.FactionOrderManager;
import ru.liko.pjmbasemod.common.faction.FactionOrderSavedData;
import ru.liko.pjmbasemod.common.faction.FactionSelectionSavedData;
import ru.liko.pjmbasemod.common.fleet.VehicleFleetSavedData;
import ru.liko.pjmbasemod.common.frontline.FrontlineManager;
import ru.liko.pjmbasemod.common.frontline.FrontlineSavedData;
import ru.liko.pjmbasemod.common.frontline.bluemap.FrontlineBlueMapService;
import ru.liko.pjmbasemod.common.garage.GarageSavedData;
import ru.liko.pjmbasemod.common.rank.RankSavedData;
import ru.liko.pjmbasemod.common.rank.RankService;
import ru.liko.pjmbasemod.common.role.RoleSavedData;
import ru.liko.pjmbasemod.common.role.RoleService;
import ru.liko.pjmbasemod.common.warehouse.WarehousePersonalBudgetSavedData;
import ru.liko.pjmbasemod.common.warehouse.WarehouseSavedData;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Оркестратор вайпа сезонного прогресса игроков. Не трогает админ-разметку
 * (регионы, зоны базы, настройки склада, терминалы, скины, баны).
 */
public final class WipeService {

    private WipeService() {}

    /** Сброс званий (XP) у всех игроков. */
    public static void wipeRanks(MinecraftServer server) {
        RankSavedData.get(server).clearAll();
        resyncAll(server);
    }

    /** Сброс званий (XP) у указанного набора игроков (членов команды). */
    public static void wipeRanksForTeam(MinecraftServer server, Set<UUID> members) {
        RankSavedData.get(server).clearPlayers(members);
        resyncAll(server);
    }

    /** Полный вайп прогресса игроков. Админ-разметка сохраняется. */
    public static void wipeAll(MinecraftServer server) {
        RankSavedData.get(server).clearAll();
        WarehouseSavedData.get(server).clearAll();
        WarehousePersonalBudgetSavedData.get(server).clearAll();
        GarageSavedData.get(server).clearVehicles();
        VehicleFleetSavedData.get(server).clearAll();
        FrontlineSavedData.get(server).clearAll();
        FactionSelectionSavedData.get(server).clearAll();
        FactionCommanderSavedData.get(server).clearAll();
        FactionDeputySavedData.get(server).clearAll();
        FactionOrderSavedData.get(server).clearAll();
        RoleSavedData.get(server).clearAll();
        resyncAll(server);
    }

    /**
     * Резолв UUID членов scoreboard-команды: онлайн — напрямую, офлайн — через
     * кэш профилей сервера. Имена, которых нет в кэше, молча пропускаются
     * (вызывающий может сравнить размеры и сообщить о пропущенных).
     */
    public static Set<UUID> resolveTeamMemberUuids(MinecraftServer server, String teamId) {
        Set<UUID> result = new LinkedHashSet<>();
        PlayerTeam team = server.getScoreboard().getPlayerTeam(teamId);
        if (team == null) return result;
        GameProfileCache cache = server.getProfileCache();
        for (String name : team.getPlayers()) {
            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
            if (online != null) {
                result.add(online.getUUID());
                continue;
            }
            if (cache != null) {
                cache.get(name).ifPresent(profile -> result.add(profile.getId()));
            }
        }
        return result;
    }

    /** Мгновенно обновляет клиентов онлайн-игроков после вайпа — без релога. */
    private static void resyncAll(MinecraftServer server) {
        RankService.syncAll(server);
        RoleService.syncAll(server);
        FactionCommanderService.syncAll(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            FrontlineManager.sendInitialSync(player);
            FactionOrderManager.syncTo(player);
            FactionMenuService.onPlayerLogin(player);
        }
        FrontlineBlueMapService.requestSync("wipe");
    }
}
```

- [ ] **Step 2: Компиляция**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

Примечания (сверено с `ModerationCommands.java:348`):
- `server.getProfileCache()` возвращает `GameProfileCache` напрямую (nullable), а `.get(name)` → `Optional<GameProfile>`. Именно поэтому в коде null-проверка `cache`, а не `Optional`-обёртка.
- `FactionMenuService.onPlayerLogin` идемпотентен (та же логика, что при входе) — безопасно звать при ре-синхронизации.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/wipe/WipeService.java
git commit -m "feat(wipe): WipeService — оркестратор вайпа и ре-синхронизация"
```

---

## Task 3: WipeCommands (дерево команд + подтверждение) и подключение

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/command/WipeCommands.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/command/PjmCommands.java`

**Interfaces:**
- Consumes (из Task 2): `WipeService.wipeRanks`, `wipeRanksForTeam`, `wipeAll`, `resolveTeamMemberUuids`.
- Consumes (существующие): `FrontlineTeams.all()` → `List<Config.ConfiguredTeam>` (у элемента `.id()`).
- Produces: `WipeCommands.build()` → `LiteralArgumentBuilder<CommandSourceStack>` (узел `wipe`), подключаемый в `PjmCommands`.

Поведение подтверждения: первый вызов `/pjm wipe <scope> [team]` печатает ⚠-предупреждение и кладёт `Pending` с TTL 15 сек; повтор той же команды с суффиксом `confirm` в окне — выполняет. Просрочка/несовпадение/отсутствие — сообщение «запустите заново». `sourceKey` = UUID игрока или `"__console__"`.

- [ ] **Step 1: Создать `WipeCommands.java`**

```java
package ru.liko.pjmbasemod.common.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.wipe.WipeService;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * {@code /pjm wipe ...} — сброс сезонного прогресса игроков. Только OP (level 4),
 * с двухшаговым подтверждением (повтор команды с {@code confirm} в течение 15 сек).
 */
public final class WipeCommands {

    private static final long WINDOW_MS = 15_000L;
    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(String scope, @Nullable String team, long expiresAt) {}

    private static final SuggestionProvider<CommandSourceStack> TEAM_SUGGESTIONS = (ctx, builder) -> {
        for (Config.ConfiguredTeam team : FrontlineTeams.all()) {
            builder.suggest(team.id());
        }
        return builder.buildFuture();
    };

    private WipeCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("wipe")
                .requires(src -> src.hasPermission(4))
                .then(Commands.literal("ranks")
                        .executes(ctx -> handleRanks(ctx.getSource(), null, false))
                        .then(Commands.literal("confirm")
                                .executes(ctx -> handleRanks(ctx.getSource(), null, true)))
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests(TEAM_SUGGESTIONS)
                                .executes(ctx -> handleRanks(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "team"), false))
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> handleRanks(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team"), true)))))
                .then(Commands.literal("all")
                        .executes(ctx -> handleAll(ctx.getSource(), false))
                        .then(Commands.literal("confirm")
                                .executes(ctx -> handleAll(ctx.getSource(), true))));
    }

    // ---------------------------------------------------------------- ranks

    private static int handleRanks(CommandSourceStack src, @Nullable String team, boolean confirm) {
        MinecraftServer server = src.getServer();
        if (team != null && !isKnownTeam(team)) {
            src.sendFailure(Component.translatable("pjmbasemod.wipe.unknown_team", team));
            return 0;
        }
        String scope = team == null ? "ranks" : "ranks_team";

        if (!confirm) {
            arm(src, scope, team);
            Component warn = team == null
                    ? Component.translatable("pjmbasemod.wipe.warn.ranks")
                    : Component.translatable("pjmbasemod.wipe.warn.ranks_team", team);
            src.sendSuccess(() -> warn.copy().withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        if (!disarm(src, scope, team)) {
            src.sendFailure(Component.translatable("pjmbasemod.wipe.expired"));
            return 0;
        }

        if (team == null) {
            WipeService.wipeRanks(server);
            src.sendSuccess(() -> Component.translatable("pjmbasemod.wipe.success.ranks"), true);
        } else {
            Set<UUID> members = WipeService.resolveTeamMemberUuids(server, team);
            int total = teamMemberCount(server, team);
            int skipped = Math.max(0, total - members.size());
            WipeService.wipeRanksForTeam(server, members);
            String teamFinal = team;
            src.sendSuccess(() -> Component.translatable(
                    "pjmbasemod.wipe.success.ranks_team", teamFinal, members.size()), true);
            if (skipped > 0) {
                src.sendSuccess(() -> Component.translatable(
                        "pjmbasemod.wipe.team_offline_skipped", skipped), false);
            }
        }
        return 1;
    }

    // ---------------------------------------------------------------- all

    private static int handleAll(CommandSourceStack src, boolean confirm) {
        MinecraftServer server = src.getServer();
        if (!confirm) {
            arm(src, "all", null);
            src.sendSuccess(() -> Component.translatable("pjmbasemod.wipe.warn.all")
                    .copy().withStyle(ChatFormatting.RED), false);
            return 1;
        }
        if (!disarm(src, "all", null)) {
            src.sendFailure(Component.translatable("pjmbasemod.wipe.expired"));
            return 0;
        }
        WipeService.wipeAll(server);
        src.sendSuccess(() -> Component.translatable("pjmbasemod.wipe.success.all"), true);
        return 1;
    }

    // ---------------------------------------------------------------- helpers

    private static void arm(CommandSourceStack src, String scope, @Nullable String team) {
        PENDING.put(sourceKey(src), new Pending(scope, team, nowMs() + WINDOW_MS));
    }

    /** true, если для источника есть валидная (не просроченная, совпадающая) заявка; удаляет её. */
    private static boolean disarm(CommandSourceStack src, String scope, @Nullable String team) {
        Pending pending = PENDING.remove(sourceKey(src));
        return pending != null
                && pending.scope().equals(scope)
                && Objects.equals(pending.team(), team)
                && nowMs() < pending.expiresAt();
    }

    private static String sourceKey(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        return player != null ? player.getUUID().toString() : "__console__";
    }

    private static boolean isKnownTeam(String teamId) {
        for (Config.ConfiguredTeam team : FrontlineTeams.all()) {
            if (team.id().equals(teamId)) return true;
        }
        return false;
    }

    private static int teamMemberCount(MinecraftServer server, String teamId) {
        var team = server.getScoreboard().getPlayerTeam(teamId);
        return team == null ? 0 : team.getPlayers().size();
    }

    private static long nowMs() {
        return System.currentTimeMillis();
    }
}
```

- [ ] **Step 2: Подключить узел в `PjmCommands`**

В `src/main/java/ru/liko/pjmbasemod/common/command/PjmCommands.java`, в методе `register(...)`, в блоке `// --- админ / управление миром ---` добавить строку сразу после `.then(ModerationCommands.build())`:

```java
                .then(ModerationCommands.build())
                .then(WipeCommands.build())
                .then(configCommand())
```

Импорт не нужен — `WipeCommands` в том же пакете `ru.liko.pjmbasemod.common.command`.

- [ ] **Step 3: Компиляция**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

Примечания (сверено):
- `FrontlineTeams.all()` → `List<Config.ConfiguredTeam>`, `ConfiguredTeam` — `record ConfiguredTeam(String id)` (`Config.java:285`), значит `.id()` корректен.
- `Component.translatable(key, Object...)` принимает `int` через автобоксинг — `members.size()`/`skipped` передаются как есть.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/command/WipeCommands.java \
        src/main/java/ru/liko/pjmbasemod/common/command/PjmCommands.java
git commit -m "feat(wipe): команда /pjm wipe с подтверждением + подключение в PjmCommands"
```

---

## Task 4: Локализация (5 языков)

**Files:**
- Modify: `src/client/resources/assets/pjmbasemod/lang/ru_ru.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/en_us.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/uk_ua.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/de_de.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/zh_cn.json`

**Interfaces:**
- Consumes: ключи, использованные в Task 3 (`pjmbasemod.wipe.warn.ranks`, `.warn.ranks_team`, `.warn.all`, `.success.ranks`, `.success.ranks_team`, `.success.all`, `.expired`, `.unknown_team`, `.team_offline_skipped`).

- [ ] **Step 1: `ru_ru.json` — добавить блок ключей**

Добавить перед закрывающей `}` (не забыть запятую после предыдущей записи):

```json
  "pjmbasemod.wipe.warn.ranks": "⚠ Это сбросит звания (XP) У ВСЕХ игроков. Для подтверждения повторите ту же команду с 'confirm' в течение 15 секунд.",
  "pjmbasemod.wipe.warn.ranks_team": "⚠ Это сбросит звания (XP) команды '%s'. Для подтверждения повторите ту же команду с 'confirm' в течение 15 секунд.",
  "pjmbasemod.wipe.warn.all": "⚠ ПОЛНЫЙ ВАЙП: будет стёрт весь прогресс игроков (звания, склад, гаражи, флот, фронтлайн, фракции, роли). Админ-разметка сохранится. Повторите с 'confirm' в течение 15 секунд.",
  "pjmbasemod.wipe.success.ranks": "Звания сброшены у всех игроков.",
  "pjmbasemod.wipe.success.ranks_team": "Звания команды '%s' сброшены (%s игроков).",
  "pjmbasemod.wipe.success.all": "Полный вайп прогресса игроков выполнен.",
  "pjmbasemod.wipe.expired": "Подтверждение не найдено или истекло. Запустите команду заново.",
  "pjmbasemod.wipe.unknown_team": "Команда '%s' не найдена.",
  "pjmbasemod.wipe.team_offline_skipped": "Пропущено (нет в кэше профилей): %s."
```

- [ ] **Step 2: `en_us.json` — те же ключи**

```json
  "pjmbasemod.wipe.warn.ranks": "⚠ This will reset ranks (XP) for ALL players. To confirm, repeat the same command with 'confirm' within 15 seconds.",
  "pjmbasemod.wipe.warn.ranks_team": "⚠ This will reset ranks (XP) for team '%s'. To confirm, repeat the same command with 'confirm' within 15 seconds.",
  "pjmbasemod.wipe.warn.all": "⚠ FULL WIPE: all player progress will be erased (ranks, warehouse, garages, fleet, frontline, factions, roles). Admin setup is preserved. Repeat with 'confirm' within 15 seconds.",
  "pjmbasemod.wipe.success.ranks": "Ranks reset for all players.",
  "pjmbasemod.wipe.success.ranks_team": "Ranks for team '%s' reset (%s players).",
  "pjmbasemod.wipe.success.all": "Full player-progress wipe complete.",
  "pjmbasemod.wipe.expired": "No pending confirmation, or it expired. Run the command again.",
  "pjmbasemod.wipe.unknown_team": "Team '%s' not found.",
  "pjmbasemod.wipe.team_offline_skipped": "Skipped (not in profile cache): %s."
```

- [ ] **Step 3: `uk_ua.json` — те же ключи**

```json
  "pjmbasemod.wipe.warn.ranks": "⚠ Це скине звання (XP) У ВСІХ гравців. Для підтвердження повторіть ту саму команду з 'confirm' протягом 15 секунд.",
  "pjmbasemod.wipe.warn.ranks_team": "⚠ Це скине звання (XP) команди '%s'. Для підтвердження повторіть ту саму команду з 'confirm' протягом 15 секунд.",
  "pjmbasemod.wipe.warn.all": "⚠ ПОВНИЙ ВАЙП: буде стерто весь прогрес гравців (звання, склад, гаражі, флот, фронтлайн, фракції, ролі). Адмін-налаштування збережуться. Повторіть з 'confirm' протягом 15 секунд.",
  "pjmbasemod.wipe.success.ranks": "Звання скинуто в усіх гравців.",
  "pjmbasemod.wipe.success.ranks_team": "Звання команди '%s' скинуто (%s гравців).",
  "pjmbasemod.wipe.success.all": "Повний вайп прогресу гравців виконано.",
  "pjmbasemod.wipe.expired": "Підтвердження не знайдено або воно застаріло. Запустіть команду знову.",
  "pjmbasemod.wipe.unknown_team": "Команду '%s' не знайдено.",
  "pjmbasemod.wipe.team_offline_skipped": "Пропущено (немає в кеші профілів): %s."
```

- [ ] **Step 4: `de_de.json` — те же ключи**

```json
  "pjmbasemod.wipe.warn.ranks": "⚠ Dies setzt die Ränge (XP) ALLER Spieler zurück. Zum Bestätigen denselben Befehl innerhalb von 15 Sekunden mit 'confirm' wiederholen.",
  "pjmbasemod.wipe.warn.ranks_team": "⚠ Dies setzt die Ränge (XP) des Teams '%s' zurück. Zum Bestätigen denselben Befehl innerhalb von 15 Sekunden mit 'confirm' wiederholen.",
  "pjmbasemod.wipe.warn.all": "⚠ VOLLSTÄNDIGER WIPE: Der gesamte Spielerfortschritt wird gelöscht (Ränge, Lager, Garagen, Flotte, Frontlinie, Fraktionen, Rollen). Admin-Einrichtung bleibt erhalten. Innerhalb von 15 Sekunden mit 'confirm' wiederholen.",
  "pjmbasemod.wipe.success.ranks": "Ränge aller Spieler zurückgesetzt.",
  "pjmbasemod.wipe.success.ranks_team": "Ränge des Teams '%s' zurückgesetzt (%s Spieler).",
  "pjmbasemod.wipe.success.all": "Vollständiger Wipe des Spielerfortschritts abgeschlossen.",
  "pjmbasemod.wipe.expired": "Keine ausstehende Bestätigung oder abgelaufen. Befehl erneut ausführen.",
  "pjmbasemod.wipe.unknown_team": "Team '%s' nicht gefunden.",
  "pjmbasemod.wipe.team_offline_skipped": "Übersprungen (nicht im Profil-Cache): %s."
```

- [ ] **Step 5: `zh_cn.json` — те же ключи**

```json
  "pjmbasemod.wipe.warn.ranks": "⚠ 这将重置所有玩家的军衔（经验）。如需确认，请在 15 秒内以 'confirm' 重复相同命令。",
  "pjmbasemod.wipe.warn.ranks_team": "⚠ 这将重置队伍 '%s' 的军衔（经验）。如需确认，请在 15 秒内以 'confirm' 重复相同命令。",
  "pjmbasemod.wipe.warn.all": "⚠ 完全清档：将清除所有玩家进度（军衔、仓库、车库、载具、前线、阵营、角色）。管理员配置将保留。请在 15 秒内以 'confirm' 重复命令。",
  "pjmbasemod.wipe.success.ranks": "已重置所有玩家的军衔。",
  "pjmbasemod.wipe.success.ranks_team": "已重置队伍 '%s' 的军衔（%s 名玩家）。",
  "pjmbasemod.wipe.success.all": "玩家进度完全清档已完成。",
  "pjmbasemod.wipe.expired": "没有待确认操作或已过期。请重新执行命令。",
  "pjmbasemod.wipe.unknown_team": "未找到队伍 '%s'。",
  "pjmbasemod.wipe.team_offline_skipped": "已跳过（不在档案缓存中）：%s。"
```

- [ ] **Step 6: Валидация JSON + компиляция клиента**

Run:
```bash
for f in ru_ru en_us uk_ua de_de zh_cn; do python3 -m json.tool "src/client/resources/assets/pjmbasemod/lang/$f.json" > /dev/null && echo "$f OK"; done
./gradlew compileClientJava
```
Expected: пять строк `... OK` и `BUILD SUCCESSFUL`. Если `json.tool` падает — чаще всего пропущена/лишняя запятая перед вставленным блоком.

- [ ] **Step 7: Commit**

```bash
git add src/client/resources/assets/pjmbasemod/lang/ru_ru.json \
        src/client/resources/assets/pjmbasemod/lang/en_us.json \
        src/client/resources/assets/pjmbasemod/lang/uk_ua.json \
        src/client/resources/assets/pjmbasemod/lang/de_de.json \
        src/client/resources/assets/pjmbasemod/lang/zh_cn.json
git commit -m "feat(wipe): локализация сообщений вайпа (5 языков)"
```

---

## Self-Review

- **Spec coverage:** Команды `ranks`/`ranks <team>`/`all` — Task 3. Методы очистки 11 SavedData — Task 1. Резолв офлайн через profile cache — Task 2 (`resolveTeamMemberUuids`). Подтверждение 15 сек — Task 3. Ре-синхронизация без релога — Task 2 (`resyncAll`). Локализация 5 языков — Task 4. Админ-разметка не трогается — обеспечено выбором SavedData в Task 1/2. ✔
- **Placeholder scan:** заглушек нет, весь код приведён.
- **Type consistency:** `clearAll()`/`clearPlayers(Set<UUID>)`/`clearVehicles()` из Task 1 совпадают с вызовами в Task 2; `WipeService.wipeRanks/wipeRanksForTeam/wipeAll/resolveTeamMemberUuids` из Task 2 совпадают с вызовами в Task 3; ключи `pjmbasemod.wipe.*` в Task 3 совпадают с Task 4.
- **Известные точки сверки при компиляции** (помечены в шагах): тип `server.getProfileCache()` (Optional vs прямой) в Task 2; тип элемента `FrontlineTeams.all()` в Task 3.
