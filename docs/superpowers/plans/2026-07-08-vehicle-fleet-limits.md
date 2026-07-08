# План реализации: лимиты и очистка техники гаража (подсистема `fleet`)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ограничить число одновременно активной техники (лимит команды + игрока + кулдаун спавна) и автоматически удалять брошенную пустую технику после предупреждения.

**Architecture:** Новая подсистема `common/fleet/`: авторитетный реестр активной техники в `SavedData` (`VehicleFleetSavedData`) + persistent-NBT-метка на сущности. `VehicleFleetManager` — статический фасад: проверка лимитов при спавне, регистрация/снятие, реконсиляция и очистка брошенного раз в секунду. Интеграция — точечные врезки в `GarageManager`, `SbwVehicleDestroyMixin`, `PjmServerEvents`.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.172, ванильный `SavedData`, Gson-конфиг (`Config.java`).

## Global Constraints

- **Три source set'а:** новый код — только в `src/main` (common). `main` НИКОГДА не импортирует `client`.
- **Верификация — компиляцией**, а НЕ юнит-тестами (тест-харнеса в проекте нет; `runClient`/`runServer` не работают из-за `!` в пути). Каждая задача завершается `./gradlew compileJava` (и `compileClientJava`, если задача трогает локализацию/клиент). Рантайм-проверку выполняет пользователь.
- **Локализация — во все 5 языков:** `ru_ru.json`, `en_us.json`, `uk_ua.json`, `de_de.json`, `zh_cn.json` в `src/client/resources/assets/pjmbasemod/lang/`.
- **Команда игрока** резолвится через `FrontlineTeams.resolvePlayerTeamId(player)` (может быть `null`/пусто → «без команды»).
- **Игровое время** — `server.overworld().getGameTime()` (монотонно, переживает рестарт). 1 секунда = 20 тиков.
- **Стиль:** статические фасады-менеджеры (как `GarageManager`, `WarehouseManager`), комментарии на русском.
- После завершения всех задач НЕ бампать сетевой `VERSION` — новых пакетов нет.

---

### Task 1: Секция конфига `fleet`

**Files:**
- Modify: `src/main/java/ru/liko/pjmbasemod/Config.java` (класс `ConfigData` ~293-354, блок геттеров ~134, добавить класс `Fleet` рядом с `Garage` ~459-461)

**Interfaces:**
- Produces: `Config.isFleetEnabled()`, `Config.getFleetMaxActivePerTeam()`, `Config.getFleetMaxActivePerPlayer()`, `Config.getFleetAviationMaxActivePerTeam()`, `Config.getFleetAviationMaxActivePerPlayer()`, `Config.getFleetSpawnCooldownSeconds()`, `Config.getFleetAbandonTimeoutSeconds()`, `Config.getFleetAbandonWarningSeconds()` — все `public static`, возвращают `boolean`/`int`.

- [ ] **Step 1: Добавить класс секции `Fleet`**

В `Config.java` сразу после класса `Garage` (после строки `static final class Garage { ... }`, ~461):

```java
    /**
     * Лимиты и очистка активной техники гаража. {@code -1} в любом лимите = без ограничения.
     * Тайминги в секундах; брошенная (пустая) техника удаляется через
     * {@code abandonTimeoutSeconds + abandonWarningSeconds} после того, как её покинул водитель.
     */
    static final class Fleet {
        boolean enabled = true;
        int maxActivePerTeam = 8;
        int maxActivePerPlayer = 1;
        int spawnCooldownSeconds = 120;
        int abandonTimeoutSeconds = 180;
        int abandonWarningSeconds = 30;
        int aviationMaxActivePerTeam = 3;
        int aviationMaxActivePerPlayer = 1;
    }
```

- [ ] **Step 2: Зарегистрировать секцию в `ConfigData`**

В классе `ConfigData` рядом с `Garage garage = new Garage();` (~299) добавить поле:

```java
        Fleet fleet = new Fleet();
```

В методе `normalize()` рядом с `if (garage == null) garage = new Garage();` (~315) добавить дефолт и зажимы:

```java
            if (fleet == null) fleet = new Fleet();
            fleet.maxActivePerTeam = fleet.maxActivePerTeam < 0 ? -1 : clamp(fleet.maxActivePerTeam, 0, 4096);
            fleet.maxActivePerPlayer = fleet.maxActivePerPlayer < 0 ? -1 : clamp(fleet.maxActivePerPlayer, 0, 4096);
            fleet.aviationMaxActivePerTeam = fleet.aviationMaxActivePerTeam < 0 ? -1 : clamp(fleet.aviationMaxActivePerTeam, 0, 4096);
            fleet.aviationMaxActivePerPlayer = fleet.aviationMaxActivePerPlayer < 0 ? -1 : clamp(fleet.aviationMaxActivePerPlayer, 0, 4096);
            fleet.spawnCooldownSeconds = clamp(fleet.spawnCooldownSeconds, 0, 86_400);
            fleet.abandonTimeoutSeconds = clamp(fleet.abandonTimeoutSeconds, 5, 86_400);
            fleet.abandonWarningSeconds = clamp(fleet.abandonWarningSeconds, 0, 3_600);
```

- [ ] **Step 3: Добавить геттеры**

Рядом с `public static boolean isGarageEnabled() { return data().garage.enabled; }` (~134):

```java
    public static boolean isFleetEnabled()                 { return data().fleet.enabled; }
    public static int getFleetMaxActivePerTeam()           { return data().fleet.maxActivePerTeam; }
    public static int getFleetMaxActivePerPlayer()         { return data().fleet.maxActivePerPlayer; }
    public static int getFleetAviationMaxActivePerTeam()   { return data().fleet.aviationMaxActivePerTeam; }
    public static int getFleetAviationMaxActivePerPlayer() { return data().fleet.aviationMaxActivePerPlayer; }
    public static int getFleetSpawnCooldownSeconds()       { return data().fleet.spawnCooldownSeconds; }
    public static int getFleetAbandonTimeoutSeconds()      { return data().fleet.abandonTimeoutSeconds; }
    public static int getFleetAbandonWarningSeconds()      { return data().fleet.abandonWarningSeconds; }
```

- [ ] **Step 4: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/Config.java
git commit -m "feat(fleet): секция конфига fleet — лимиты и тайминги очистки"
```

---

### Task 2: `FleetRecord` — запись реестра

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/fleet/FleetRecord.java`

**Interfaces:**
- Consumes: `GarageType` (`ru.liko.pjmbasemod.common.garage.GarageType`).
- Produces: класс `FleetRecord` с полями `UUID entityId`, `UUID ownerId`, `String teamId`, `String defId`, `GarageType type`, `ResourceKey<Level> dimension`, `long spawnGameTime`, `long lastOccupiedGameTime` (mutable), `boolean warned` (mutable); методы `CompoundTag save()`, `static FleetRecord load(CompoundTag)`.

- [ ] **Step 1: Создать класс**

```java
package ru.liko.pjmbasemod.common.fleet;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import ru.liko.pjmbasemod.common.garage.GarageType;

import java.util.UUID;

/**
 * Запись реестра активной техники: сущность в мире + её владелец/команда/тип и тайминги.
 * {@code lastOccupiedGameTime} и {@code warned} мутируются при реконсиляции.
 */
public final class FleetRecord {

    public final UUID entityId;
    public final UUID ownerId;
    public final String teamId;   // "" если игрок был без команды
    public final String defId;
    public final GarageType type;
    public final ResourceKey<Level> dimension;
    public final long spawnGameTime;
    public long lastOccupiedGameTime;
    public boolean warned;

    public FleetRecord(UUID entityId, UUID ownerId, String teamId, String defId, GarageType type,
                       ResourceKey<Level> dimension, long spawnGameTime, long lastOccupiedGameTime, boolean warned) {
        this.entityId = entityId;
        this.ownerId = ownerId;
        this.teamId = teamId == null ? "" : teamId;
        this.defId = defId;
        this.type = type;
        this.dimension = dimension;
        this.spawnGameTime = spawnGameTime;
        this.lastOccupiedGameTime = lastOccupiedGameTime;
        this.warned = warned;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Entity", entityId);
        tag.putUUID("Owner", ownerId);
        tag.putString("Team", teamId);
        tag.putString("Def", defId);
        tag.putString("Type", type.name());
        tag.putString("Dim", dimension.location().toString());
        tag.putLong("Spawn", spawnGameTime);
        tag.putLong("LastOccupied", lastOccupiedGameTime);
        tag.putBoolean("Warned", warned);
        return tag;
    }

    public static FleetRecord load(CompoundTag tag) {
        GarageType type;
        try {
            type = GarageType.valueOf(tag.getString("Type"));
        } catch (IllegalArgumentException e) {
            type = GarageType.GROUND;
        }
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(tag.getString("Dim")));
        return new FleetRecord(
                tag.getUUID("Entity"),
                tag.getUUID("Owner"),
                tag.getString("Team"),
                tag.getString("Def"),
                type,
                dim,
                tag.getLong("Spawn"),
                tag.getLong("LastOccupied"),
                tag.getBoolean("Warned"));
    }
}
```

- [ ] **Step 2: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/fleet/FleetRecord.java
git commit -m "feat(fleet): FleetRecord — запись реестра активной техники"
```

---

### Task 3: `VehicleFleetSavedData` — персистентность

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/fleet/VehicleFleetSavedData.java`

**Interfaces:**
- Consumes: `FleetRecord` (Task 2).
- Produces: `static VehicleFleetSavedData get(MinecraftServer)`; методы `void put(FleetRecord)`, `void remove(UUID)`, `Collection<FleetRecord> all()`, `long lastSpawn(UUID owner)`, `void setLastSpawn(UUID owner, long tick)`.

- [ ] **Step 1: Создать класс**

```java
package ru.liko.pjmbasemod.common.fleet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Персистентный реестр активной техники гаража. Хранится в data/{@value #DATA_NAME}.dat overworld-а.
 * {@code active} — карта entityId → запись; {@code lastSpawn} — время последнего спавна на игрока (кулдаун).
 */
public final class VehicleFleetSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_fleet";
    private static final SavedData.Factory<VehicleFleetSavedData> FACTORY = new SavedData.Factory<>(
            VehicleFleetSavedData::new,
            VehicleFleetSavedData::load);

    private final Map<UUID, FleetRecord> active = new LinkedHashMap<>();
    private final Map<UUID, Long> lastSpawn = new HashMap<>();

    public static VehicleFleetSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static VehicleFleetSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        VehicleFleetSavedData data = new VehicleFleetSavedData();
        ListTag records = tag.getList("Active", Tag.TAG_COMPOUND);
        for (int i = 0; i < records.size(); i++) {
            FleetRecord record = FleetRecord.load(records.getCompound(i));
            data.active.put(record.entityId, record);
        }
        ListTag cooldowns = tag.getList("Cooldowns", Tag.TAG_COMPOUND);
        for (int i = 0; i < cooldowns.size(); i++) {
            CompoundTag c = cooldowns.getCompound(i);
            if (c.hasUUID("Owner")) data.lastSpawn.put(c.getUUID("Owner"), c.getLong("Tick"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag records = new ListTag();
        for (FleetRecord record : active.values()) {
            records.add(record.save());
        }
        tag.put("Active", records);
        ListTag cooldowns = new ListTag();
        for (Map.Entry<UUID, Long> entry : lastSpawn.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Owner", entry.getKey());
            c.putLong("Tick", entry.getValue());
            cooldowns.add(c);
        }
        tag.put("Cooldowns", cooldowns);
        return tag;
    }

    public void put(FleetRecord record) {
        active.put(record.entityId, record);
        setDirty();
    }

    public void remove(UUID entityId) {
        if (active.remove(entityId) != null) setDirty();
    }

    /** Копия для безопасной итерации при реконсиляции. */
    public Collection<FleetRecord> all() {
        return new ArrayList<>(active.values());
    }

    public long lastSpawn(UUID owner) {
        return lastSpawn.getOrDefault(owner, 0L);
    }

    public void setLastSpawn(UUID owner, long tick) {
        lastSpawn.put(owner, tick);
        setDirty();
    }
}
```

- [ ] **Step 2: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/fleet/VehicleFleetSavedData.java
git commit -m "feat(fleet): VehicleFleetSavedData — персистентный реестр + кулдауны"
```

---

### Task 4: `VehicleFleetManager` — лимиты, регистрация, снятие

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/fleet/VehicleFleetManager.java`

**Interfaces:**
- Consumes: `VehicleFleetSavedData`, `FleetRecord` (Tasks 2-3); `Config` геттеры (Task 1); `FrontlineTeams.resolvePlayerTeamId`; `GarageType`.
- Produces:
  - `static boolean canSpawn(ServerPlayer player, GarageType type)` — проверяет кулдаун + лимит игрока + лимит команды; при отказе шлёт игроку сообщение и возвращает `false`; при `!Config.isFleetEnabled()` возвращает `true`.
  - `static void register(net.minecraft.world.entity.Entity entity, ServerPlayer player, String defId, GarageType type)` — пишет запись + метку на сущность + обновляет кулдаун.
  - `static void unregister(java.util.UUID entityId)` — снимает запись (используется из `GarageManager` и миксина; сервер берётся из статической ссылки, см. Step 3).

- [ ] **Step 1: Создать класс с подсчётом и canSpawn**

```java
package ru.liko.pjmbasemod.common.fleet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.garage.GarageType;

import java.util.UUID;

/**
 * Фасад подсистемы «парк техники»: лимиты одновременно активной техники (команда/игрок),
 * кулдаун спавна, регистрация/снятие записей и очистка брошенной техники (см. onServerTick).
 */
public final class VehicleFleetManager {

    /** NBT-ключ метки на сущности (в {@code entity.getPersistentData()}). */
    public static final String TAG = "PjmFleet";

    private VehicleFleetManager() {}

    private static int maxPerTeam(GarageType type) {
        return type == GarageType.AVIATION
                ? Config.getFleetAviationMaxActivePerTeam()
                : Config.getFleetMaxActivePerTeam();
    }

    private static int maxPerPlayer(GarageType type) {
        return type == GarageType.AVIATION
                ? Config.getFleetAviationMaxActivePerPlayer()
                : Config.getFleetMaxActivePerPlayer();
    }

    private static int countForOwner(VehicleFleetSavedData data, UUID owner, GarageType type) {
        int n = 0;
        for (FleetRecord r : data.all()) {
            if (r.type == type && r.ownerId.equals(owner)) n++;
        }
        return n;
    }

    private static int countForTeam(VehicleFleetSavedData data, String teamId, GarageType type) {
        int n = 0;
        for (FleetRecord r : data.all()) {
            if (r.type == type && r.teamId.equals(teamId)) n++;
        }
        return n;
    }

    public static boolean canSpawn(ServerPlayer player, GarageType type) {
        if (!Config.isFleetEnabled()) return true;
        VehicleFleetSavedData data = VehicleFleetSavedData.get(player.server);
        long now = player.server.overworld().getGameTime();

        // 1. Кулдаун игрока.
        int cooldownTicks = Config.getFleetSpawnCooldownSeconds() * 20;
        if (cooldownTicks > 0) {
            long elapsed = now - data.lastSpawn(player.getUUID());
            if (elapsed < cooldownTicks) {
                long remainingSec = (cooldownTicks - elapsed + 19) / 20;
                player.sendSystemMessage(Component.translatable("gui.pjmbasemod.fleet.cooldown", remainingSec));
                return false;
            }
        }

        // 2. Лимит игрока.
        int playerLimit = maxPerPlayer(type);
        if (playerLimit >= 0 && countForOwner(data, player.getUUID(), type) >= playerLimit) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.fleet.limit_player"));
            return false;
        }

        // 3. Лимит команды (только если игрок в команде).
        String team = FrontlineTeams.resolvePlayerTeamId(player);
        if (team != null && !team.isBlank()) {
            int teamLimit = maxPerTeam(type);
            if (teamLimit >= 0 && countForTeam(data, team, type) >= teamLimit) {
                player.sendSystemMessage(Component.translatable(
                        "gui.pjmbasemod.fleet.limit_team", countForTeam(data, team, type), teamLimit));
                return false;
            }
        }
        return true;
    }

    public static void register(Entity entity, ServerPlayer player, String defId, GarageType type) {
        if (!Config.isFleetEnabled()) return;
        MinecraftServer server = player.server;
        VehicleFleetSavedData data = VehicleFleetSavedData.get(server);
        long now = server.overworld().getGameTime();
        String team = FrontlineTeams.resolvePlayerTeamId(player);
        if (team == null) team = "";

        FleetRecord record = new FleetRecord(entity.getUUID(), player.getUUID(), team, defId, type,
                entity.level().dimension(), now, now, false);
        data.put(record);
        data.setLastSpawn(player.getUUID(), now);

        CompoundTag fleet = new CompoundTag();
        fleet.putUUID("Owner", player.getUUID());
        fleet.putString("Team", team);
        fleet.putString("Def", defId);
        fleet.putString("Type", type.name());
        fleet.putLong("Spawn", now);
        entity.getPersistentData().put(TAG, fleet);
    }

    public static void unregister(MinecraftServer server, UUID entityId) {
        if (server == null) return;
        VehicleFleetSavedData.get(server).remove(entityId);
    }
}
```

Примечание: `unregister` принимает `MinecraftServer` явно — так его зовут и `GarageManager` (есть `player.server`), и миксин (`self.getServer()` через `entity.getServer()`; в `onServerTick` реконсиляция вызывает `remove` напрямую).

- [ ] **Step 2: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/fleet/VehicleFleetManager.java
git commit -m "feat(fleet): VehicleFleetManager — лимиты, кулдаун, регистрация техники"
```

---

### Task 5: Реконсиляция и очистка брошенного (`onServerTick`)

**Files:**
- Modify: `src/main/java/ru/liko/pjmbasemod/common/fleet/VehicleFleetManager.java`

**Interfaces:**
- Consumes: `Config` тайминги (Task 1), `VehicleFleetSavedData` (Task 3), `PjmActionLogger`, `LogCategory`, `VehicleRegistry`.
- Produces: `static void onServerTick(MinecraftServer server)` — раз в 20 тиков реконсилирует реестр: снимает мёртвые записи, продлевает занятые, предупреждает и удаляет брошенные.

- [ ] **Step 1: Добавить импорты**

В шапку `VehicleFleetManager.java` к существующим импортам добавить:

```java
import net.minecraft.server.level.ServerLevel;
import ru.liko.pjmbasemod.common.garage.VehicleDefinition;
import ru.liko.pjmbasemod.common.garage.VehicleRegistry;
import ru.liko.pjmbasemod.common.logging.LogCategory;
import ru.liko.pjmbasemod.common.logging.PjmActionLogger;

import java.util.ArrayList;
import java.util.List;
```

- [ ] **Step 2: Добавить счётчик тика и метод `onServerTick`**

Внутрь класса `VehicleFleetManager` (после `unregister`):

```java
    private static int tickCounter = 0;

    public static void onServerTick(MinecraftServer server) {
        if (!Config.isFleetEnabled()) return;
        if (++tickCounter < 20) return;
        tickCounter = 0;

        VehicleFleetSavedData data = VehicleFleetSavedData.get(server);
        long now = server.overworld().getGameTime();
        int timeoutTicks = Config.getFleetAbandonTimeoutSeconds() * 20;
        int warnTicks = Config.getFleetAbandonWarningSeconds() * 20;

        List<UUID> toRemove = new ArrayList<>();
        for (FleetRecord record : data.all()) {
            ServerLevel level = server.getLevel(record.dimension);
            Entity entity = level == null ? null : level.getEntity(record.entityId);

            // Сущности нет / удалена (в т.ч. уничтожена и снята миксином) → снять запись тихо.
            if (entity == null || entity.isRemoved()) {
                toRemove.add(record.entityId);
                continue;
            }
            // Есть водитель/пассажир → техника «живая», сбросить отсчёт и предупреждение.
            if (!entity.getPassengers().isEmpty()) {
                if (record.lastOccupiedGameTime != now || record.warned) {
                    record.lastOccupiedGameTime = now;
                    record.warned = false;
                    data.setDirty();
                }
                continue;
            }
            long idle = now - record.lastOccupiedGameTime;
            if (idle > timeoutTicks + warnTicks) {
                entity.discard();
                toRemove.add(record.entityId);
                PjmActionLogger.instance().logSubsystem(LogCategory.GARAGE,
                        "Брошенная техника удалена: " + displayName(record)
                                + " @ " + record.dimension.location());
            } else if (idle > timeoutTicks && !record.warned) {
                record.warned = true;
                data.setDirty();
                warnAbandon(server, entity, record, warnTicks);
            }
        }
        for (UUID id : toRemove) {
            data.remove(id);
        }
    }

    private static String displayName(FleetRecord record) {
        VehicleDefinition def = VehicleRegistry.get().get(record.defId);
        return def != null ? def.displayName() : record.defId;
    }

    private static void warnAbandon(MinecraftServer server, Entity entity, FleetRecord record, int warnTicks) {
        int seconds = Math.max(1, warnTicks / 20);
        String name = displayName(record);
        // Владельцу — системное сообщение, если онлайн.
        ServerPlayer owner = server.getPlayerList().getPlayer(record.ownerId);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("gui.pjmbasemod.fleet.abandon_warning", name, seconds));
        }
        // Игрокам рядом — actionbar.
        if (entity.level() instanceof ServerLevel level) {
            for (ServerPlayer near : level.getPlayers(p -> p.distanceToSqr(entity) <= 32.0 * 32.0)) {
                near.displayClientMessage(
                        Component.translatable("gui.pjmbasemod.fleet.abandon_warning", name, seconds), true);
            }
        }
    }
```

- [ ] **Step 3: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/fleet/VehicleFleetManager.java
git commit -m "feat(fleet): реконсиляция и очистка брошенной техники с предупреждением"
```

---

### Task 6: Врезки в существующий код (спавн/сдача/уничтожение/тик)

**Files:**
- Modify: `src/main/java/ru/liko/pjmbasemod/common/garage/GarageManager.java` (метод `performSpawn` ~448-507; метод `storeResolvedVehicle` ~684-702)
- Modify: `src/main/java/ru/liko/pjmbasemod/mixin/SbwVehicleDestroyMixin.java` (~31-36)
- Modify: `src/main/java/ru/liko/pjmbasemod/common/event/PjmServerEvents.java` (метод `onServerTick` ~257-271)

**Interfaces:**
- Consumes: `VehicleFleetManager.canSpawn/register/unregister/onServerTick` (Tasks 4-5); `SbwVehicleClassifier.classify` (`common/compat`).

- [ ] **Step 1: Врезать проверку лимита и регистрацию в `performSpawn`**

В `GarageManager.performSpawn`, сразу ПОСЛЕ блока проверки занятости точки (после закрывающей `}` блока `if (!isSpawnPointFree(level, point)) { ... }`, ~строка 475, перед `Entity entity = type.create(level);`) вставить:

```java
        GarageType fleetType = ru.liko.pjmbasemod.common.compat.SbwVehicleClassifier.classify(typeId);
        if (!ru.liko.pjmbasemod.common.fleet.VehicleFleetManager.canSpawn(player, fleetType)) {
            resync(player);
            return;
        }
```

Важно: этот блок выше `data.remove(garageKey(player), instanceId);` (~498), поэтому при отказе техника со склада НЕ снимается.

В том же методе сразу ПОСЛЕ `level.addFreshEntity(entity);` (~496) вставить:

```java
        ru.liko.pjmbasemod.common.fleet.VehicleFleetManager.register(entity, player, stored.defId(), fleetType);
```

- [ ] **Step 2: Врезать снятие записи в `storeResolvedVehicle`**

В `GarageManager.storeResolvedVehicle`, непосредственно ПЕРЕД `entity.discard();` (~699) вставить:

```java
        ru.liko.pjmbasemod.common.fleet.VehicleFleetManager.unregister(player.server, entity.getUUID());
```

- [ ] **Step 3: Врезать снятие записи в миксин уничтожения**

В `SbwVehicleDestroyMixin.pjm_onVehicleDestroy` (после существующей строки логирования, ~35) добавить:

```java
        if (self.getServer() != null) {
            ru.liko.pjmbasemod.common.fleet.VehicleFleetManager.unregister(self.getServer(), self.getUUID());
        }
```

- [ ] **Step 4: Вызвать реконсиляцию из `onServerTick`**

В `PjmServerEvents.onServerTick`, среди существующих вызовов (после `ModerationService.tick(event.getServer());`, ~262) добавить:

```java
        ru.liko.pjmbasemod.common.fleet.VehicleFleetManager.onServerTick(event.getServer());
```

- [ ] **Step 5: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/garage/GarageManager.java \
        src/main/java/ru/liko/pjmbasemod/mixin/SbwVehicleDestroyMixin.java \
        src/main/java/ru/liko/pjmbasemod/common/event/PjmServerEvents.java
git commit -m "feat(fleet): интеграция лимитов/учёта в спавн, сдачу, уничтожение и тик"
```

---

### Task 7: Локализация (5 языков)

**Files:**
- Modify: `src/client/resources/assets/pjmbasemod/lang/ru_ru.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/en_us.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/uk_ua.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/de_de.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/zh_cn.json`

**Interfaces:**
- Consumes: ключи, использованные в Tasks 4-5: `gui.pjmbasemod.fleet.cooldown`, `gui.pjmbasemod.fleet.limit_player`, `gui.pjmbasemod.fleet.limit_team`, `gui.pjmbasemod.fleet.abandon_warning`.

- [ ] **Step 1: Добавить ключи в каждый lang-файл**

Вставить в существующий JSON-объект каждого файла (рядом с блоком `gui.pjmbasemod.garage.*`, соблюдая запятые). `%s` — плейсхолдеры: `cooldown` — секунды; `limit_team` — текущее/лимит; `abandon_warning` — имя техники, секунды.

`ru_ru.json`:
```json
  "gui.pjmbasemod.fleet.cooldown": "§cПерезарядка спавна техники: ещё %s сек.",
  "gui.pjmbasemod.fleet.limit_player": "§cУ вас уже есть активная техника. Сдайте её в гараж, прежде чем брать новую.",
  "gui.pjmbasemod.fleet.limit_team": "§cЛимит техники команды исчерпан (%s/%s). Дождитесь, пока освободится слот.",
  "gui.pjmbasemod.fleet.abandon_warning": "§cТехника «%s» брошена и будет удалена через %s сек. Сядьте в неё, чтобы отменить.",
```

`en_us.json`:
```json
  "gui.pjmbasemod.fleet.cooldown": "§cVehicle spawn cooldown: %s s left.",
  "gui.pjmbasemod.fleet.limit_player": "§cYou already have an active vehicle. Return it to the garage before taking a new one.",
  "gui.pjmbasemod.fleet.limit_team": "§cTeam vehicle limit reached (%s/%s). Wait for a slot to free up.",
  "gui.pjmbasemod.fleet.abandon_warning": "§cVehicle \"%s\" is abandoned and will be removed in %s s. Enter it to cancel.",
```

`uk_ua.json`:
```json
  "gui.pjmbasemod.fleet.cooldown": "§cПерезарядка спавну техніки: ще %s с.",
  "gui.pjmbasemod.fleet.limit_player": "§cУ вас уже є активна техніка. Здайте її в гараж, перш ніж брати нову.",
  "gui.pjmbasemod.fleet.limit_team": "§cЛіміт техніки команди вичерпано (%s/%s). Зачекайте, поки звільниться слот.",
  "gui.pjmbasemod.fleet.abandon_warning": "§cТехніка «%s» покинута і буде видалена через %s с. Сядьте в неї, щоб скасувати.",
```

`de_de.json`:
```json
  "gui.pjmbasemod.fleet.cooldown": "§cFahrzeug-Spawn-Abklingzeit: noch %s s.",
  "gui.pjmbasemod.fleet.limit_player": "§cDu hast bereits ein aktives Fahrzeug. Bring es zur Garage zurück, bevor du ein neues nimmst.",
  "gui.pjmbasemod.fleet.limit_team": "§cFahrzeuglimit des Teams erreicht (%s/%s). Warte auf einen freien Platz.",
  "gui.pjmbasemod.fleet.abandon_warning": "§cFahrzeug \"%s\" wurde verlassen und wird in %s s entfernt. Steig ein, um abzubrechen.",
```

`zh_cn.json`:
```json
  "gui.pjmbasemod.fleet.cooldown": "§c载具生成冷却中：还剩 %s 秒。",
  "gui.pjmbasemod.fleet.limit_player": "§c你已有一辆活动载具。请先将其收回车库再取新载具。",
  "gui.pjmbasemod.fleet.limit_team": "§c队伍载具数量已达上限（%s/%s）。请等待空位释放。",
  "gui.pjmbasemod.fleet.abandon_warning": "§c载具\"%s\"已被遗弃，将在 %s 秒后移除。进入载具即可取消。",
```

- [ ] **Step 2: Валидация JSON + компиляция клиента**

Run: `for f in src/client/resources/assets/pjmbasemod/lang/*.json; do python3 -m json.tool "$f" > /dev/null && echo "OK $f" || echo "BAD $f"; done`
Expected: `OK` для всех пяти файлов.

Run: `./gradlew compileClientJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/client/resources/assets/pjmbasemod/lang/
git commit -m "feat(fleet): локализация сообщений лимитов и очистки (5 языков)"
```

---

## Self-Review

**1. Spec coverage:**
- Реестр `SavedData` + метка на сущности → Tasks 2, 3, 4 (`register` пишет и запись, и `PjmFleet`-метку). ✅
- Лимит команды + игрока + кулдаун → Task 4 `canSpawn`. ✅
- Раздельные лимиты GROUND/AVIATION → Task 1 (поля aviation*) + Task 4 (`maxPerTeam/maxPerPlayer` по типу, тип из `SbwVehicleClassifier.classify`). ✅
- Очистка брошенного: warning → delete → Task 5. ✅
- Врезки в `performSpawn` (до снятия со склада), `storeResolvedVehicle`, миксин, `onServerTick` → Task 6. ✅
- Конфиг-секция `fleet` → Task 1. ✅
- Локализация 5 языков → Task 7. ✅
- Глобальный подсчёт команды (поле `dimension` заложено на будущее) → Task 4 `countForTeam` не фильтрует по измерению; `dimension` сохраняется в `FleetRecord`. ✅

**2. Placeholder scan:** плейсхолдеров-заглушек нет; весь код приведён целиком.

**3. Type consistency:**
- `unregister(MinecraftServer, UUID)` — единая сигнатура, так зовётся в Tasks 4/5/6. ✅
- `register(Entity, ServerPlayer, String defId, GarageType)` — `stored.defId()` (String) в Task 6 совпадает. ✅
- `canSpawn(ServerPlayer, GarageType)` — тип из `classify(typeId)` в Task 6. ✅
- `GarageType.valueOf/name()` — enum есть в `common/garage`. ✅
- `LogCategory.GARAGE` / `PjmActionLogger.logSubsystem(LogCategory, String)` — использованы как в существующем `GarageManager` (см. `performSpawn`). ✅

**Отклонение от спеки:** класс `FleetLimits` не создаётся — резолв лимита по `GarageType` свёрнут в геттеры `Config` + приватные методы `VehicleFleetManager.maxPerTeam/maxPerPlayer`. Меньше классов, та же логика.
