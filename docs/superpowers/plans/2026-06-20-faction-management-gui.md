# Faction Management GUI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Доработать экран управления фракцией (`FactionManagementScreen`): добавить назначаемых командиром заместителей с настраиваемыми правами и систему приказа фракции (постоянное уведомление с TTL на HUD у всех членов команды).

**Architecture:** Бэкенд в `common/faction` — два новых `SavedData` (замы, приказ), enum прав-битмаски, сервис приказа, расширение `FactionManagementSnapshot` и `FactionMenuService` (объект прав зрителя `Authority`). Сеть — 2 новых C→S пакета и 1 S→C (бамп `VERSION`). Клиент — зеркало приказа + HUD-overlay + переписанный экран с табами «Роль / Зам / Приказ».

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.172, Gson-конфиг, ванильный `SavedData`, NeoForge `PayloadRegistrar`.

## Global Constraints

- Three source sets: `main` (common), `client`, `server`. **`main` НИКОГДА не импортирует `client`.** S→C — только через `ClientPacketProxy`.
- `runClient`/`runServer` не работают (символ `!` в пути). Верификация: `./gradlew compileJava` (common) + `./gradlew compileClientJava` (client) + валидация JSON всех 5 lang-файлов.
- Локализация — ключи во **все 5** языков: `ru_ru`, `en_us`, `uk_ua`, `de_de`, `zh_cn`.
- При изменении состава пакетов — бампать `PjmNetworking.VERSION` (сейчас `"18"` → `"19"`).
- Команды/состояние игроков — через ванильный scoreboard (`FrontlineTeams.resolvePlayerTeamId`). Лимиты/числа конфига зажимать в `normalize()`.
- Сборка медленная (NeoForge) — компиляция одного source set может занять минуты, это нормально.

---

### Task 1: DeputyPermission enum + конфиг-секция faction

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/faction/DeputyPermission.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/Config.java`

**Interfaces:**
- Produces: `DeputyPermission` (enum `ASSIGN_ROLES, SET_ORDER, OPEN_GUI`), методы `int bit()`, `static int pack(Set<DeputyPermission>)`, `static boolean has(int mask, DeputyPermission)`, `static int sanitize(int mask)`, `static int all()`.
- Produces: `Config.getFactionMaxDeputies()`, `Config.getFactionOrderMaxLength()`, `Config.getFactionOrderDefaultTtlMinutes()`, `Config.getFactionOrderMaxTtlMinutes()` — все `int`.

- [ ] **Step 1: Создать enum DeputyPermission**

```java
package ru.liko.pjmbasemod.common.faction;

import java.util.Set;

/** Права заместителя фракции, упакованные в int-битмаску. */
public enum DeputyPermission {
    ASSIGN_ROLES(1),
    SET_ORDER(2),
    OPEN_GUI(4);

    private final int bit;

    DeputyPermission(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public static int pack(Set<DeputyPermission> perms) {
        int mask = 0;
        for (DeputyPermission p : perms) mask |= p.bit;
        return mask;
    }

    public static boolean has(int mask, DeputyPermission perm) {
        return (mask & perm.bit) != 0;
    }

    public static int all() {
        int mask = 0;
        for (DeputyPermission p : values()) mask |= p.bit;
        return mask;
    }

    /** Отбрасывает биты, не соответствующие ни одному праву. */
    public static int sanitize(int mask) {
        return mask & all();
    }
}
```

- [ ] **Step 2: Добавить секцию Faction в Config.ConfigData**

В `Config.java` в классе `ConfigData` добавить поле рядом с `Garage garage = new Garage();`:

```java
        Garage garage = new Garage();
        Faction faction = new Faction();
        Commands commands = new Commands();
```

В методе `normalize()` после `if (garage == null) garage = new Garage();` добавить:

```java
            if (faction == null) faction = new Faction();
            faction.maxDeputies = clamp(faction.maxDeputies, 0, 64);
            faction.orderMaxLength = clamp(faction.orderMaxLength, 1, 256);
            faction.orderMaxTtlMinutes = clamp(faction.orderMaxTtlMinutes, 0, 10_080);
            faction.orderDefaultTtlMinutes = clamp(faction.orderDefaultTtlMinutes, 0, faction.orderMaxTtlMinutes);
```

Добавить вложенный класс рядом с `static final class Garage`:

```java
    static final class Faction {
        int maxDeputies = 3;
        int orderMaxLength = 120;
        int orderDefaultTtlMinutes = 30;
        int orderMaxTtlMinutes = 240;
    }
```

- [ ] **Step 3: Добавить геттеры в Config (публичный API)**

После строки `public static boolean isGarageEnabled() { return data().garage.enabled; }` добавить:

```java
    public static int getFactionMaxDeputies()            { return data().faction.maxDeputies; }
    public static int getFactionOrderMaxLength()         { return data().faction.orderMaxLength; }
    public static int getFactionOrderDefaultTtlMinutes() { return data().faction.orderDefaultTtlMinutes; }
    public static int getFactionOrderMaxTtlMinutes()     { return data().faction.orderMaxTtlMinutes; }
```

- [ ] **Step 4: Компиляция common**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/faction/DeputyPermission.java src/main/java/ru/liko/pjmbasemod/Config.java
git commit -m "feat(faction): права заместителя (DeputyPermission) и конфиг-секция faction"
```

---

### Task 2: FactionDeputySavedData

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/faction/FactionDeputySavedData.java`

**Interfaces:**
- Consumes: `FrontlineTeams.normalize(String)` (есть), `DeputyPermission.sanitize(int)` (Task 1).
- Produces: `FactionDeputySavedData.get(MinecraftServer)`, `isDeputy(String teamId, UUID)`, `int permissions(String teamId, UUID)`, `int deputyCount(String teamId)`, `Map<UUID,Integer> deputies(String teamId)`, `void setDeputy(String teamId, UUID, int perms)`, `boolean removeDeputy(String teamId, UUID)`, `String deputyTeamOf(UUID)`, `void clearPlayer(UUID)`.

- [ ] **Step 1: Создать FactionDeputySavedData**

```java
package ru.liko.pjmbasemod.common.faction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Заместители фракций: teamId → (playerId → битмаска прав DeputyPermission). */
public final class FactionDeputySavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_faction_deputies";
    private static final SavedData.Factory<FactionDeputySavedData> FACTORY = new SavedData.Factory<>(
            FactionDeputySavedData::new,
            FactionDeputySavedData::load
    );

    private final Map<String, Map<UUID, Integer>> deputiesByTeam = new LinkedHashMap<>();

    public static FactionDeputySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static FactionDeputySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionDeputySavedData data = new FactionDeputySavedData();
        ListTag list = tag.getList("deputies", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String team = normalize(entry.getString("team"));
            if (team.isBlank()) continue;
            try {
                UUID uuid = UUID.fromString(entry.getString("uuid"));
                int perms = DeputyPermission.sanitize(entry.getInt("perms"));
                data.deputiesByTeam.computeIfAbsent(team, k -> new LinkedHashMap<>()).put(uuid, perms);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Map<UUID, Integer>> team : deputiesByTeam.entrySet()) {
            for (Map.Entry<UUID, Integer> member : team.getValue().entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("team", team.getKey());
                entry.putString("uuid", member.getKey().toString());
                entry.putInt("perms", member.getValue());
                list.add(entry);
            }
        }
        tag.put("deputies", list);
        return tag;
    }

    public boolean isDeputy(String teamId, UUID playerId) {
        Map<UUID, Integer> team = deputiesByTeam.get(normalize(teamId));
        return team != null && playerId != null && team.containsKey(playerId);
    }

    public int permissions(String teamId, UUID playerId) {
        Map<UUID, Integer> team = deputiesByTeam.get(normalize(teamId));
        if (team == null || playerId == null) return 0;
        return team.getOrDefault(playerId, 0);
    }

    public int deputyCount(String teamId) {
        Map<UUID, Integer> team = deputiesByTeam.get(normalize(teamId));
        return team == null ? 0 : team.size();
    }

    public Map<UUID, Integer> deputies(String teamId) {
        Map<UUID, Integer> team = deputiesByTeam.get(normalize(teamId));
        return team == null ? Map.of() : Map.copyOf(team);
    }

    public void setDeputy(String teamId, UUID playerId, int perms) {
        String team = normalize(teamId);
        if (team.isBlank() || playerId == null) return;
        int sanitized = DeputyPermission.sanitize(perms);
        Integer previous = deputiesByTeam.computeIfAbsent(team, k -> new LinkedHashMap<>()).put(playerId, sanitized);
        if (previous == null || previous != sanitized) setDirty();
    }

    public boolean removeDeputy(String teamId, UUID playerId) {
        Map<UUID, Integer> team = deputiesByTeam.get(normalize(teamId));
        if (team == null || playerId == null) return false;
        boolean removed = team.remove(playerId) != null;
        if (team.isEmpty()) deputiesByTeam.remove(normalize(teamId));
        if (removed) setDirty();
        return removed;
    }

    @Nullable
    public String deputyTeamOf(UUID playerId) {
        if (playerId == null) return null;
        for (Map.Entry<String, Map<UUID, Integer>> team : deputiesByTeam.entrySet()) {
            if (team.getValue().containsKey(playerId)) return team.getKey();
        }
        return null;
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) return;
        boolean changed = false;
        for (Map<UUID, Integer> team : deputiesByTeam.values()) {
            if (team.remove(playerId) != null) changed = true;
        }
        deputiesByTeam.values().removeIf(Map::isEmpty);
        if (changed) setDirty();
    }

    private static String normalize(String teamId) {
        return FrontlineTeams.normalize(teamId);
    }
}
```

- [ ] **Step 2: Компиляция common**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/faction/FactionDeputySavedData.java
git commit -m "feat(faction): персистентность заместителей (FactionDeputySavedData)"
```

---

### Task 3: FactionOrderSavedData

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/faction/FactionOrderSavedData.java`

**Interfaces:**
- Consumes: `FrontlineTeams.normalize(String)`.
- Produces: `FactionOrderSavedData.get(MinecraftServer)`, `OrderEntry order(String teamId)`, `void setOrder(String teamId, OrderEntry)`, `void clearOrder(String teamId)`, `Map<String,OrderEntry> orders()`; record `OrderEntry(String text, String author, long setAtGameTime, long expiresAtGameTime)` (`expiresAtGameTime == -1` → бессрочно).

- [ ] **Step 1: Создать FactionOrderSavedData**

```java
package ru.liko.pjmbasemod.common.faction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Текущий приказ каждой фракции. expiresAtGameTime == -1 → бессрочно. */
public final class FactionOrderSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_faction_orders";
    private static final SavedData.Factory<FactionOrderSavedData> FACTORY = new SavedData.Factory<>(
            FactionOrderSavedData::new,
            FactionOrderSavedData::load
    );

    private final Map<String, OrderEntry> ordersByTeam = new LinkedHashMap<>();

    public static FactionOrderSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static FactionOrderSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionOrderSavedData data = new FactionOrderSavedData();
        ListTag list = tag.getList("orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String team = FrontlineTeams.normalize(entry.getString("team"));
            if (team.isBlank()) continue;
            String text = entry.getString("text");
            if (text.isBlank()) continue;
            data.ordersByTeam.put(team, new OrderEntry(
                    text,
                    entry.getString("author"),
                    entry.getLong("setAt"),
                    entry.getLong("expiresAt")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, OrderEntry> entry : ordersByTeam.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("team", entry.getKey());
            t.putString("text", entry.getValue().text());
            t.putString("author", entry.getValue().author());
            t.putLong("setAt", entry.getValue().setAtGameTime());
            t.putLong("expiresAt", entry.getValue().expiresAtGameTime());
            list.add(t);
        }
        tag.put("orders", list);
        return tag;
    }

    @Nullable
    public OrderEntry order(String teamId) {
        return ordersByTeam.get(FrontlineTeams.normalize(teamId));
    }

    public void setOrder(String teamId, OrderEntry entry) {
        String team = FrontlineTeams.normalize(teamId);
        if (team.isBlank() || entry == null) return;
        ordersByTeam.put(team, entry);
        setDirty();
    }

    public void clearOrder(String teamId) {
        if (ordersByTeam.remove(FrontlineTeams.normalize(teamId)) != null) setDirty();
    }

    public Map<String, OrderEntry> orders() {
        return Map.copyOf(ordersByTeam);
    }

    public record OrderEntry(String text, String author, long setAtGameTime, long expiresAtGameTime) {
    }
}
```

- [ ] **Step 2: Компиляция common**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/faction/FactionOrderSavedData.java
git commit -m "feat(faction): персистентность приказа фракции (FactionOrderSavedData)"
```

---

### Task 4: Расширить FactionManagementSnapshot

**Files:**
- Modify: `src/main/java/ru/liko/pjmbasemod/common/faction/FactionManagementSnapshot.java`

**Interfaces:**
- Produces: новый конструктор `FactionManagementSnapshot(teamId, teamName, teamColor, canManage, members, roles, viewerCanAssignRoles, viewerCanManageDeputies, viewerCanSetOrder, maxDeputies, deputyCount, orderText, orderAuthor, orderSecondsRemaining)`; `MemberEntry(UUID playerId, String name, String roleId, boolean commander, boolean deputy, int deputyPerms)`.

- [ ] **Step 1: Полностью заменить файл**

```java
package ru.liko.pjmbasemod.common.faction;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record FactionManagementSnapshot(
        String teamId,
        String teamName,
        int teamColor,
        boolean canManage,
        List<MemberEntry> members,
        List<FactionSelectionSnapshot.RoleEntry> roles,
        boolean viewerCanAssignRoles,
        boolean viewerCanManageDeputies,
        boolean viewerCanSetOrder,
        int maxDeputies,
        int deputyCount,
        String orderText,
        String orderAuthor,
        int orderSecondsRemaining
) {

    public record MemberEntry(UUID playerId, String name, String roleId, boolean commander,
                              boolean deputy, int deputyPerms) {
    }

    public static void write(FriendlyByteBuf buf, FactionManagementSnapshot snapshot) {
        buf.writeUtf(snapshot.teamId());
        buf.writeUtf(snapshot.teamName());
        buf.writeVarInt(snapshot.teamColor());
        buf.writeBoolean(snapshot.canManage());

        buf.writeVarInt(snapshot.members().size());
        for (MemberEntry member : snapshot.members()) {
            buf.writeUUID(member.playerId());
            buf.writeUtf(member.name());
            buf.writeUtf(member.roleId());
            buf.writeBoolean(member.commander());
            buf.writeBoolean(member.deputy());
            buf.writeVarInt(member.deputyPerms());
        }

        buf.writeVarInt(snapshot.roles().size());
        for (FactionSelectionSnapshot.RoleEntry role : snapshot.roles()) {
            buf.writeUtf(role.id());
            buf.writeUtf(role.displayName());
            buf.writeVarInt(role.color());
            buf.writeInt(role.limit());
            buf.writeVarInt(role.current());
        }

        buf.writeBoolean(snapshot.viewerCanAssignRoles());
        buf.writeBoolean(snapshot.viewerCanManageDeputies());
        buf.writeBoolean(snapshot.viewerCanSetOrder());
        buf.writeVarInt(snapshot.maxDeputies());
        buf.writeVarInt(snapshot.deputyCount());
        buf.writeUtf(snapshot.orderText());
        buf.writeUtf(snapshot.orderAuthor());
        buf.writeInt(snapshot.orderSecondsRemaining());
    }

    public static FactionManagementSnapshot read(FriendlyByteBuf buf) {
        String teamId = buf.readUtf();
        String teamName = buf.readUtf();
        int teamColor = buf.readVarInt();
        boolean canManage = buf.readBoolean();

        int memberCount = buf.readVarInt();
        List<MemberEntry> members = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            members.add(new MemberEntry(buf.readUUID(), buf.readUtf(), buf.readUtf(),
                    buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));
        }

        int roleCount = buf.readVarInt();
        List<FactionSelectionSnapshot.RoleEntry> roles = new ArrayList<>(roleCount);
        for (int i = 0; i < roleCount; i++) {
            roles.add(new FactionSelectionSnapshot.RoleEntry(
                    buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readInt(), buf.readVarInt()));
        }

        boolean viewerCanAssignRoles = buf.readBoolean();
        boolean viewerCanManageDeputies = buf.readBoolean();
        boolean viewerCanSetOrder = buf.readBoolean();
        int maxDeputies = buf.readVarInt();
        int deputyCount = buf.readVarInt();
        String orderText = buf.readUtf();
        String orderAuthor = buf.readUtf();
        int orderSecondsRemaining = buf.readInt();

        return new FactionManagementSnapshot(teamId, teamName, teamColor, canManage,
                List.copyOf(members), List.copyOf(roles),
                viewerCanAssignRoles, viewerCanManageDeputies, viewerCanSetOrder,
                maxDeputies, deputyCount, orderText, orderAuthor, orderSecondsRemaining);
    }
}
```

- [ ] **Step 2: Компиляция common — ожидается ошибка**

Run: `./gradlew compileJava`
Expected: FAIL — `FactionMenuService.managementSnapshot` использует старый конструктор. Это нормально, чинится в Task 5. Зафиксировать, что ошибка именно в `FactionMenuService` (вызовы `new FactionManagementSnapshot(...)` и `new FactionManagementSnapshot.MemberEntry(...)`).

- [ ] **Step 3: НЕ коммитить отдельно** — этот файл коммитится вместе с Task 5 (взаимозависимы). Перейти к Task 5.

---

### Task 5: FactionMenuService — Authority, права, snapshot, обработчики

**Files:**
- Modify: `src/main/java/ru/liko/pjmbasemod/common/faction/FactionMenuService.java`

**Interfaces:**
- Consumes: `FactionDeputySavedData` (Task 2), `DeputyPermission` (Task 1), `FactionOrderSavedData` (Task 3), новый конструктор снапшота (Task 4).
- Produces: `FactionMenuService.Authority authority(ServerPlayer)`; record `Authority(String teamId, boolean canOpen, boolean canAssignRoles, boolean canSetOrder, boolean canManageDeputies)` с методом `boolean valid()`; `void handleManageDeputy(ServerPlayer actor, UUID targetId, boolean deputy, int perms)`; `void resync(ServerPlayer actor)`.

- [ ] **Step 1: Добавить импорты**

В шапку `FactionMenuService.java` добавить к существующим импортам:

```java
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;
```

(Импорты `FactionDeputySavedData`, `FactionOrderSavedData`, `DeputyPermission` не нужны — они в том же пакете `common.faction`.)

- [ ] **Step 2: Добавить метод authority(...) и record Authority**

Вставить перед методом `private static String managementTeam(ServerPlayer actor)`:

```java
    /** Что текущий игрок вправе делать в управлении своей фракцией. */
    public static Authority authority(ServerPlayer actor) {
        if (actor == null || actor.getServer() == null) return Authority.NONE;
        String team = FrontlineTeams.resolvePlayerTeamId(actor);
        if (team == null || team.isBlank()) return Authority.NONE;

        boolean admin = RolePermissions.can(actor, RolePermissions.ADMIN);
        boolean commander = team.equals(FactionCommanderService.activeCommanderTeam(actor));
        boolean full = admin || commander;
        int perms = FactionDeputySavedData.get(actor.getServer()).permissions(team, actor.getUUID());

        boolean open = full || DeputyPermission.has(perms, DeputyPermission.OPEN_GUI);
        boolean roles = full || DeputyPermission.has(perms, DeputyPermission.ASSIGN_ROLES);
        boolean order = full || DeputyPermission.has(perms, DeputyPermission.SET_ORDER);
        return new Authority(team, open, roles, order, full);
    }

    public record Authority(String teamId, boolean canOpen, boolean canAssignRoles,
                            boolean canSetOrder, boolean canManageDeputies) {
        public static final Authority NONE = new Authority("", false, false, false, false);

        public boolean valid() {
            return teamId != null && !teamId.isBlank();
        }
    }
```

- [ ] **Step 3: Переписать openManagement через authority**

Заменить метод `openManagement`:

```java
    public static boolean openManagement(ServerPlayer actor) {
        if (actor == null || actor.getServer() == null) return false;
        Authority authority = authority(actor);
        if (!authority.valid() || !authority.canOpen()) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.no_access"), true);
            return false;
        }
        PjmNetworking.sendToPlayer(actor, new OpenFactionManagementPacket(managementSnapshot(actor, authority)));
        return true;
    }
```

- [ ] **Step 4: Переписать handleManageRole через authority**

Заменить метод `handleManageRole`:

```java
    public static void handleManageRole(ServerPlayer actor, UUID targetId, String roleId) {
        if (actor == null || actor.getServer() == null || targetId == null) return;
        Authority authority = authority(actor);
        if (!authority.valid() || !authority.canAssignRoles()) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.no_access"), true);
            return;
        }
        String team = authority.teamId();

        ServerPlayer target = actor.getServer().getPlayerList().getPlayer(targetId);
        if (target == null) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.target_missing"), true);
            resync(actor);
            return;
        }

        String targetTeam = FrontlineTeams.resolvePlayerTeamId(target);
        if (!team.equals(targetTeam)) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.target_wrong_faction"), true);
            resync(actor);
            return;
        }

        CombatRole role = roleId == null || roleId.isBlank() ? null : CombatRole.byIdOrAlias(roleId);
        if (role == null && roleId != null && !roleId.isBlank()) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.unknown", roleId), true);
            resync(actor);
            return;
        }

        RoleService.AssignmentResult result = RoleService.assignRole(actor, target, role, false);
        actor.displayClientMessage(result.message(), true);
        resync(actor);
    }
```

- [ ] **Step 5: Добавить handleManageDeputy**

Вставить после `handleManageRole`:

```java
    public static void handleManageDeputy(ServerPlayer actor, UUID targetId, boolean deputy, int perms) {
        if (actor == null || actor.getServer() == null || targetId == null) return;
        Authority authority = authority(actor);
        if (!authority.valid() || !authority.canManageDeputies()) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.no_access"), true);
            return;
        }
        String team = authority.teamId();
        MinecraftServer server = actor.getServer();

        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.target_missing"), true);
            resync(actor);
            return;
        }
        if (!team.equals(FrontlineTeams.resolvePlayerTeamId(target))) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.role.target_wrong_faction"), true);
            resync(actor);
            return;
        }

        FactionDeputySavedData data = FactionDeputySavedData.get(server);
        if (deputy) {
            boolean already = data.isDeputy(team, targetId);
            if (!already && data.deputyCount(team) >= Config.getFactionMaxDeputies()) {
                actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.deputy.limit_reached"), true);
                resync(actor);
                return;
            }
            data.setDeputy(team, targetId, DeputyPermission.sanitize(perms));
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.deputy.added",
                    target.getName().getString()), true);
        } else {
            data.removeDeputy(team, targetId);
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.deputy.removed",
                    target.getName().getString()), true);
        }
        resync(actor);
    }
```

- [ ] **Step 6: Сделать ресинк публичным, удалить старый syncManagement**

Заменить приватный `syncManagement`:

```java
    public static void resync(ServerPlayer actor) {
        if (actor == null || actor.getServer() == null) return;
        Authority authority = authority(actor);
        if (!authority.valid() || !authority.canOpen()) return;
        PjmNetworking.sendToPlayer(actor, new FactionManagementSyncPacket(managementSnapshot(actor, authority)));
    }
```

- [ ] **Step 7: Переписать managementSnapshot под authority и новые поля**

Заменить метод `managementSnapshot(ServerPlayer actor, String team)` на версию с `Authority`:

```java
    private static FactionManagementSnapshot managementSnapshot(ServerPlayer actor, Authority authority) {
        MinecraftServer server = actor.getServer();
        String team = authority.teamId();
        FactionDeputySavedData deputies = FactionDeputySavedData.get(server);

        List<FactionManagementSnapshot.MemberEntry> members = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!team.equals(FrontlineTeams.resolvePlayerTeamId(player))) continue;
            String commanderTeam = FactionCommanderService.activeCommanderTeam(player);
            int perms = deputies.permissions(team, player.getUUID());
            members.add(new FactionManagementSnapshot.MemberEntry(player.getUUID(),
                    player.getName().getString(), RoleService.currentRoleId(player),
                    team.equals(commanderTeam),
                    deputies.isDeputy(team, player.getUUID()), perms));
        }
        members.sort(Comparator.comparing(FactionManagementSnapshot.MemberEntry::commander).reversed()
                .thenComparing(FactionManagementSnapshot.MemberEntry::deputy, Comparator.reverseOrder())
                .thenComparing(FactionManagementSnapshot.MemberEntry::name, String.CASE_INSENSITIVE_ORDER));

        FactionOrderSavedData.OrderEntry order = FactionOrderSavedData.get(server).order(team);
        long now = server.overworld().getGameTime();
        String orderText = "";
        String orderAuthor = "";
        int orderSeconds = 0;
        if (order != null && !(order.expiresAtGameTime() >= 0 && now >= order.expiresAtGameTime())) {
            orderText = order.text();
            orderAuthor = order.author();
            orderSeconds = order.expiresAtGameTime() < 0 ? -1
                    : (int) Math.max(1, (order.expiresAtGameTime() - now) / 20);
        }

        return new FactionManagementSnapshot(team,
                FrontlineTeams.displayName(server, team),
                FrontlineTeams.color(server, team),
                true,
                List.copyOf(members),
                roleEntries(server, team),
                authority.canAssignRoles(),
                authority.canManageDeputies(),
                authority.canSetOrder(),
                Config.getFactionMaxDeputies(),
                deputies.deputyCount(team),
                orderText, orderAuthor, orderSeconds);
    }
```

- [ ] **Step 8: Поправить debugOpenManagement (он зовёт старый managementSnapshot)**

Заменить тело `debugOpenManagement`:

```java
    public static boolean debugOpenManagement(ServerPlayer target) {
        if (target == null || target.getServer() == null) return false;
        Authority authority = authority(target);
        if (!authority.valid()) return false;
        // Debug всегда открывает с полными правами, минуя проверки прав.
        Authority full = new Authority(authority.teamId(), true, true, true, true);
        PjmNetworking.sendToPlayer(target, new OpenFactionManagementPacket(managementSnapshot(target, full)));
        return true;
    }
```

- [ ] **Step 9: Добавить очистку зама при смене команды в onPlayerTick**

Заменить метод `onPlayerTick`:

```java
    public static void onPlayerTick(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        if (player.serverLevel().getGameTime() % 40L == 0L) {
            FactionDeputySavedData data = FactionDeputySavedData.get(player.getServer());
            String deputyTeam = data.deputyTeamOf(player.getUUID());
            if (deputyTeam != null && !deputyTeam.equals(FrontlineTeams.resolvePlayerTeamId(player))) {
                data.removeDeputy(deputyTeam, player.getUUID());
            }
        }
        if (!needsFirstJoinSelection(player)) return;
        if (player.serverLevel().getGameTime() % REOPEN_INTERVAL_TICKS == 20L) {
            openSelection(player);
        }
    }
```

- [ ] **Step 10: Удалить ставший ненужным managementTeam (если не используется)**

Метод `private static String managementTeam(ServerPlayer actor)` больше нигде не вызывается после правок. Удалить его и неиспользуемый импорт `javax.annotation.Nullable`, **только если** компилятор/IDE подтверждает отсутствие других ссылок. Проверить grep:

Run: `grep -rn "managementTeam" src/main/java`
Expected: пусто (после удаления). Если есть другие ссылки — оставить метод.

- [ ] **Step 11: Компиляция common (Task 4 + Task 5 вместе)**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 12: Commit (Task 4 + Task 5)**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/faction/FactionManagementSnapshot.java src/main/java/ru/liko/pjmbasemod/common/faction/FactionMenuService.java
git commit -m "feat(faction): права зрителя (Authority), снапшот с замами и приказом, управление замами"
```

---

### Task 6: Пакеты + регистрация сети

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/network/packet/ManageFactionDeputyPacket.java`
- Create: `src/main/java/ru/liko/pjmbasemod/common/network/packet/SetFactionOrderPacket.java`
- Create: `src/main/java/ru/liko/pjmbasemod/common/network/packet/FactionOrderSyncPacket.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/network/PjmNetworking.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/network/ClientPacketProxy.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/network/handler/ServerPacketHandlers.java`

**Interfaces:**
- Produces: `ManageFactionDeputyPacket(UUID targetId, boolean deputy, int perms)`; `SetFactionOrderPacket(String text, int ttlMinutes)`; `FactionOrderSyncPacket(boolean active, String text, String author, int teamColor, int secondsRemaining)`.
- Produces: `PjmNetworking.sendToTeam(MinecraftServer, String teamId, CustomPacketPayload)`.
- Produces: `ClientPacketProxy.factionOrderSync(FactionOrderSyncPacket)` (default noop).
- Produces: `ServerPacketHandlers.handleManageFactionDeputy(...)`, `handleSetFactionOrder(...)`.
- Consumes: `FactionMenuService.handleManageDeputy` (Task 5), `FactionOrderManager.setOrder` (Task 7 — будет добавлен; до Task 7 этот вызов не компилируется, поэтому handleSetFactionOrder добавляется в Task 7, см. ниже).

- [ ] **Step 1: ManageFactionDeputyPacket**

```java
package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

public record ManageFactionDeputyPacket(UUID targetId, boolean deputy, int perms) implements CustomPacketPayload {

    public static final Type<ManageFactionDeputyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "manage_faction_deputy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ManageFactionDeputyPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, packet -> packet.targetId().toString(),
                    ByteBufCodecs.BOOL, ManageFactionDeputyPacket::deputy,
                    ByteBufCodecs.VAR_INT, ManageFactionDeputyPacket::perms,
                    (targetId, deputy, perms) -> new ManageFactionDeputyPacket(UUID.fromString(targetId), deputy, perms));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 2: SetFactionOrderPacket**

```java
package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

public record SetFactionOrderPacket(String text, int ttlMinutes) implements CustomPacketPayload {

    public static final Type<SetFactionOrderPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "set_faction_order"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFactionOrderPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SetFactionOrderPacket::text,
                    ByteBufCodecs.VAR_INT, SetFactionOrderPacket::ttlMinutes,
                    SetFactionOrderPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 3: FactionOrderSyncPacket**

```java
package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

public record FactionOrderSyncPacket(
        boolean active,
        String text,
        String author,
        int teamColor,
        int secondsRemaining
) implements CustomPacketPayload {

    public static final Type<FactionOrderSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "faction_order_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FactionOrderSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBoolean(packet.active());
                buf.writeUtf(packet.text());
                buf.writeUtf(packet.author());
                buf.writeVarInt(packet.teamColor());
                buf.writeInt(packet.secondsRemaining());
            },
            buf -> new FactionOrderSyncPacket(
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 4: ClientPacketProxy — добавить метод и импорт**

В `ClientPacketProxy.java` добавить импорт рядом с другими faction-импортами:

```java
import ru.liko.pjmbasemod.common.network.packet.FactionOrderSyncPacket;
```

И добавить default-метод рядом с `factionManagementSync`:

```java
    default void factionOrderSync(FactionOrderSyncPacket payload) {}
```

- [ ] **Step 5: PjmNetworking — VERSION, регистрация, sendToTeam**

В `PjmNetworking.java`:

(a) Бампнуть версию:

```java
    public static final String VERSION = "19";
```

(b) В блоке `// ===== Client → Server =====` после строки регистрации `ManageFactionRolePacket` добавить:

```java
        r.playToServer(ManageFactionDeputyPacket.TYPE, ManageFactionDeputyPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ServerPacketHandlers.handleManageFactionDeputy(p, (ServerPlayer) ctx.player())));
        r.playToServer(SetFactionOrderPacket.TYPE,     SetFactionOrderPacket.STREAM_CODEC,     (p, ctx) -> ctx.enqueueWork(() -> ServerPacketHandlers.handleSetFactionOrder(p, (ServerPlayer) ctx.player())));
```

(c) В блоке `// ===== Server → Client =====` после строки `FactionManagementSyncPacket` добавить:

```java
        r.playToClient(FactionOrderSyncPacket.TYPE, FactionOrderSyncPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.factionOrderSync(p)));
```

(d) Обновить число в логе (было 34, добавили 3 пакета → 37):

```java
        Pjmbasemod.LOGGER.info("PJM-BaseMod: registered {} network payloads.", 37);
```

(e) Добавить импорт `FrontlineTeams` в шапку:

```java
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
```

(Импорт пакетов покрыт `import ...packet.*;` — он уже есть.)

(f) Добавить метод `sendToTeam` после `sendToAll`:

```java
    public static void sendToTeam(net.minecraft.server.MinecraftServer server, String teamId, CustomPacketPayload payload) {
        if (server == null || teamId == null || teamId.isBlank()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (teamId.equals(FrontlineTeams.resolvePlayerTeamId(p))) {
                PacketDistributor.sendToPlayer(p, payload);
            }
        }
    }
```

- [ ] **Step 6: ServerPacketHandlers — handleManageFactionDeputy**

В `ServerPacketHandlers.java` добавить импорт:

```java
import ru.liko.pjmbasemod.common.network.packet.ManageFactionDeputyPacket;
```

И метод после `handleManageFactionRole`:

```java
    public static void handleManageFactionDeputy(ManageFactionDeputyPacket p, ServerPlayer player) {
        if (player == null) return;
        FactionMenuService.handleManageDeputy(player, p.targetId(), p.deputy(), p.perms());
    }
```

(Метод `handleSetFactionOrder` добавляется в Task 7, вместе с `FactionOrderManager`. До Task 7 строка регистрации `SetFactionOrderPacket` в `PjmNetworking` ссылается на ещё не существующий метод → common НЕ компилируется. Поэтому Task 6 и Task 7 коммитятся вместе. Перейти к Task 7 без коммита.)

- [ ] **Step 7: НЕ компилировать/коммитить отдельно** — перейти к Task 7.

---

### Task 7: FactionOrderManager + wiring событий

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/faction/FactionOrderManager.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/network/handler/ServerPacketHandlers.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/event/PjmServerEvents.java`

**Interfaces:**
- Consumes: `FactionMenuService.authority`, `FactionMenuService.resync` (Task 5), `FactionOrderSavedData` (Task 3), `FactionOrderSyncPacket`, `NotificationPacket`, `PjmNetworking.sendToTeam`/`sendToPlayer` (Task 6), `Config.getFactionOrderMaxLength`/`getFactionOrderMaxTtlMinutes` (Task 1).
- Produces: `FactionOrderManager.setOrder(ServerPlayer, String text, int ttlMinutes)`, `clearOrder(ServerPlayer)`, `syncTo(ServerPlayer)`, `onServerTick(MinecraftServer)`.

- [ ] **Step 1: Создать FactionOrderManager**

```java
package ru.liko.pjmbasemod.common.faction;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.FactionOrderSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;

import java.util.Map;

/** Установка/снятие/рассылка приказа фракции и проверка истечения по gameTime. */
public final class FactionOrderManager {

    private static final long NOTIFY_DURATION_MS = 5000L;
    private static int tickCounter;

    private FactionOrderManager() {
    }

    public static void setOrder(ServerPlayer actor, String rawText, int ttlMinutes) {
        if (actor == null || actor.getServer() == null) return;
        MinecraftServer server = actor.getServer();
        FactionMenuService.Authority authority = FactionMenuService.authority(actor);
        if (!authority.valid() || !authority.canSetOrder()) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.no_access"), true);
            return;
        }
        String team = authority.teamId();

        String text = rawText == null ? "" : rawText.trim();
        if (text.isBlank()) {
            clearOrder(actor);
            return;
        }
        int maxLen = Config.getFactionOrderMaxLength();
        if (text.length() > maxLen) text = text.substring(0, maxLen);

        int ttl = Mth.clamp(ttlMinutes, 0, Config.getFactionOrderMaxTtlMinutes());
        long now = server.overworld().getGameTime();
        long expires = ttl <= 0 ? -1L : now + (long) ttl * 60L * 20L;

        FactionOrderSavedData.get(server).setOrder(team,
                new FactionOrderSavedData.OrderEntry(text, actor.getName().getString(), now, expires));

        broadcast(server, team);

        Component title = Component.translatable("gui.pjmbasemod.faction.order.notify_title");
        Component subtitle = Component.literal(text);
        int color = FrontlineTeams.color(server, team);
        for (ServerPlayer member : server.getPlayerList().getPlayers()) {
            if (team.equals(FrontlineTeams.resolvePlayerTeamId(member))) {
                PjmNetworking.sendToPlayer(member, new NotificationPacket(title, subtitle, color, NOTIFY_DURATION_MS));
            }
        }
        FactionMenuService.resync(actor);
    }

    public static void clearOrder(ServerPlayer actor) {
        if (actor == null || actor.getServer() == null) return;
        FactionMenuService.Authority authority = FactionMenuService.authority(actor);
        if (!authority.valid() || !authority.canSetOrder()) {
            actor.displayClientMessage(Component.translatable("gui.pjmbasemod.faction.manage.no_access"), true);
            return;
        }
        MinecraftServer server = actor.getServer();
        FactionOrderSavedData.get(server).clearOrder(authority.teamId());
        broadcast(server, authority.teamId());
        FactionMenuService.resync(actor);
    }

    /** Отправляет игроку актуальный приказ его команды (или «пусто»). Вызывается при логине. */
    public static void syncTo(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        String team = FrontlineTeams.resolvePlayerTeamId(player);
        if (team == null || team.isBlank()) {
            PjmNetworking.sendToPlayer(player, new FactionOrderSyncPacket(false, "", "", 0xFFFFFF, 0));
            return;
        }
        PjmNetworking.sendToPlayer(player, buildPacket(player.getServer(), team));
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null) return;
        if (++tickCounter < 20) return;
        tickCounter = 0;
        FactionOrderSavedData data = FactionOrderSavedData.get(server);
        long now = server.overworld().getGameTime();
        for (Map.Entry<String, FactionOrderSavedData.OrderEntry> entry : data.orders().entrySet()) {
            FactionOrderSavedData.OrderEntry order = entry.getValue();
            if (order.expiresAtGameTime() >= 0 && now >= order.expiresAtGameTime()) {
                data.clearOrder(entry.getKey());
                broadcast(server, entry.getKey());
            }
        }
    }

    private static void broadcast(MinecraftServer server, String team) {
        PjmNetworking.sendToTeam(server, team, buildPacket(server, team));
    }

    private static FactionOrderSyncPacket buildPacket(MinecraftServer server, String team) {
        FactionOrderSavedData.OrderEntry order = FactionOrderSavedData.get(server).order(team);
        int color = FrontlineTeams.color(server, team);
        long now = server.overworld().getGameTime();
        if (order == null || (order.expiresAtGameTime() >= 0 && now >= order.expiresAtGameTime())) {
            return new FactionOrderSyncPacket(false, "", "", color, 0);
        }
        int secs = order.expiresAtGameTime() < 0 ? -1
                : (int) Math.max(1, (order.expiresAtGameTime() - now) / 20);
        return new FactionOrderSyncPacket(true, order.text(), order.author(), color, secs);
    }
}
```

- [ ] **Step 2: ServerPacketHandlers — handleSetFactionOrder**

В `ServerPacketHandlers.java` добавить импорты:

```java
import ru.liko.pjmbasemod.common.faction.FactionOrderManager;
import ru.liko.pjmbasemod.common.network.packet.SetFactionOrderPacket;
```

И метод после `handleManageFactionDeputy`:

```java
    public static void handleSetFactionOrder(SetFactionOrderPacket p, ServerPlayer player) {
        if (player == null) return;
        FactionOrderManager.setOrder(player, p.text(), p.ttlMinutes());
    }
```

- [ ] **Step 3: PjmServerEvents — wiring логина и серверного тика**

В `PjmServerEvents.java`:

(a) Импорт:

```java
import ru.liko.pjmbasemod.common.faction.FactionOrderManager;
```

(b) В `onLogin`, после `FactionMenuService.onPlayerLogin(sp);` добавить:

```java
        FactionOrderManager.syncTo(sp);
```

(c) В `onServerTick`, после `FrontlineBlueMapService.onServerTick(event.getServer());` добавить:

```java
        FactionOrderManager.onServerTick(event.getServer());
```

- [ ] **Step 4: Компиляция common (Task 6 + Task 7)**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit (Task 6 + Task 7)**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/network/packet/ManageFactionDeputyPacket.java src/main/java/ru/liko/pjmbasemod/common/network/packet/SetFactionOrderPacket.java src/main/java/ru/liko/pjmbasemod/common/network/packet/FactionOrderSyncPacket.java src/main/java/ru/liko/pjmbasemod/common/network/PjmNetworking.java src/main/java/ru/liko/pjmbasemod/common/network/ClientPacketProxy.java src/main/java/ru/liko/pjmbasemod/common/network/handler/ServerPacketHandlers.java src/main/java/ru/liko/pjmbasemod/common/faction/FactionOrderManager.java src/main/java/ru/liko/pjmbasemod/common/event/PjmServerEvents.java
git commit -m "feat(faction): сетевые пакеты замов/приказа, FactionOrderManager, wiring событий"
```

---

### Task 8: Клиент — состояние приказа + HUD-overlay

**Files:**
- Create: `src/client/java/ru/liko/pjmbasemod/client/faction/ClientFactionOrderState.java`
- Create: `src/client/java/ru/liko/pjmbasemod/client/gui/overlay/FactionOrderHudOverlay.java`
- Modify: `src/client/java/ru/liko/pjmbasemod/client/network/ClientPacketHandlersImpl.java`
- Modify: `src/client/java/ru/liko/pjmbasemod/client/gui/overlay/ClientOverlays.java`

**Interfaces:**
- Consumes: `FactionOrderSyncPacket` (Task 6), `ClientPacketProxy.factionOrderSync` (Task 6).
- Produces: `ClientFactionOrderState.update(FactionOrderSyncPacket)`, `ClientFactionOrderState.State current()` (null если приказа нет/истёк), `int remainingSeconds()` (-1 = бессрочно), `void reset()`; record `State(String text, String author, int color, int secondsRemaining)`.
- Produces: `FactionOrderHudOverlay.OVERLAY` (`LayeredDraw.Layer`).

- [ ] **Step 1: ClientFactionOrderState**

```java
package ru.liko.pjmbasemod.client.faction;

import ru.liko.pjmbasemod.common.network.packet.FactionOrderSyncPacket;

import javax.annotation.Nullable;

/** Клиентское зеркало текущего приказа фракции. */
public final class ClientFactionOrderState {

    private static volatile boolean active;
    private static volatile State state = State.empty();
    private static volatile long receivedAtMs;

    private ClientFactionOrderState() {
    }

    public static void update(FactionOrderSyncPacket packet) {
        active = packet.active();
        state = new State(packet.text(), packet.author(), packet.teamColor(), packet.secondsRemaining());
        receivedAtMs = System.currentTimeMillis();
    }

    public static void reset() {
        active = false;
        state = State.empty();
        receivedAtMs = 0L;
    }

    /** Текущий приказ или null, если его нет/истёк локально. */
    @Nullable
    public static State current() {
        if (!active) return null;
        if (state.secondsRemaining() >= 0 && remainingSeconds() <= 0) return null;
        return state;
    }

    /** Остаток в секундах: -1 = бессрочно, 0 = истёк. */
    public static int remainingSeconds() {
        State s = state;
        if (s.secondsRemaining() < 0) return -1;
        long elapsed = (System.currentTimeMillis() - receivedAtMs) / 1000L;
        return (int) Math.max(0, s.secondsRemaining() - elapsed);
    }

    public record State(String text, String author, int color, int secondsRemaining) {
        public static State empty() {
            return new State("", "", 0xFFFFFF, 0);
        }
    }
}
```

- [ ] **Step 2: FactionOrderHudOverlay**

```java
package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import ru.liko.pjmbasemod.client.faction.ClientFactionOrderState;

/** Постоянная плашка приказа фракции в левом верхнем углу. */
public final class FactionOrderHudOverlay {

    public static final LayeredDraw.Layer OVERLAY = (graphics, deltaTracker) -> render(graphics);

    private FactionOrderHudOverlay() {
    }

    private static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        ClientFactionOrderState.State order = ClientFactionOrderState.current();
        if (order == null) return;

        Font font = mc.font;
        String prefix = Component.translatable("gui.pjmbasemod.faction.order.hud_prefix").getString();
        String text = order.text();
        int remaining = ClientFactionOrderState.remainingSeconds();
        String timeText = remaining < 0
                ? Component.translatable("gui.pjmbasemod.faction.order.remaining_permanent").getString()
                : remaining + "С";

        int textWidth = Math.max(font.width(prefix + " " + text), font.width(timeText));
        int width = Math.min(260, textWidth + 12);
        int x = 5;
        int y = 45;
        int height = 30;
        int accent = 0xFF000000 | (order.color() & 0x00FFFFFF);

        graphics.pose().pushPose();
        RenderSystem.enableBlend();
        try {
            graphics.fill(x, y, x + width, y + height, 0x99000000);
            graphics.fill(x, y, x + 2, y + height, accent);
            graphics.drawString(font, prefix, x + 8, y + 5, accent, false);
            graphics.drawString(font, ellipsize(font, text, width - 12), x + 8, y + 16, 0xFFE8E8E8, false);
            graphics.drawString(font, timeText, x + width - font.width(timeText) - 6, y + 5, 0xFFFFCC00, false);
        } finally {
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    }

    private static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        while (!text.isEmpty() && font.width(text + ellipsis) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }
}
```

- [ ] **Step 3: ClientPacketHandlersImpl — обработчик**

В `ClientPacketHandlersImpl.java` добавить импорты:

```java
import ru.liko.pjmbasemod.client.faction.ClientFactionOrderState;
import ru.liko.pjmbasemod.common.network.packet.FactionOrderSyncPacket;
```

И метод после `factionManagementSync`:

```java
    @Override
    public void factionOrderSync(FactionOrderSyncPacket payload) {
        ClientFactionOrderState.update(payload);
    }
```

- [ ] **Step 4: ClientOverlays — регистрация слоя**

В `ClientOverlays.java` в `onRegister` после строки `rank_hud` добавить:

```java
        e.registerAbove(VanillaGuiLayers.HOTBAR, id("faction_order_hud"), FactionOrderHudOverlay.OVERLAY);
```

- [ ] **Step 5: Компиляция client**

Run: `./gradlew compileClientJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/client/java/ru/liko/pjmbasemod/client/faction/ClientFactionOrderState.java src/client/java/ru/liko/pjmbasemod/client/gui/overlay/FactionOrderHudOverlay.java src/client/java/ru/liko/pjmbasemod/client/network/ClientPacketHandlersImpl.java src/client/java/ru/liko/pjmbasemod/client/gui/overlay/ClientOverlays.java
git commit -m "feat(faction): клиентское зеркало приказа и HUD-плашка"
```

---

### Task 9: Переписать FactionManagementScreen (табы Роль/Зам/Приказ)

**Files:**
- Modify: `src/client/java/ru/liko/pjmbasemod/client/gui/screen/FactionManagementScreen.java` (полная замена)

**Interfaces:**
- Consumes: `FactionManagementSnapshot` с новыми полями (Task 4), `ManageFactionDeputyPacket`, `SetFactionOrderPacket` (Task 6), `DeputyPermission` (Task 1).
- Produces: тот же публичный API экрана (`open`, `updateSnapshot`).

- [ ] **Step 1: Полностью заменить файл**

```java
package ru.liko.pjmbasemod.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;
import ru.liko.pjmbasemod.common.faction.DeputyPermission;
import ru.liko.pjmbasemod.common.faction.FactionManagementSnapshot;
import ru.liko.pjmbasemod.common.faction.FactionSelectionSnapshot;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.ManageFactionDeputyPacket;
import ru.liko.pjmbasemod.common.network.packet.ManageFactionRolePacket;
import ru.liko.pjmbasemod.common.network.packet.SetFactionOrderPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FactionManagementScreen extends PjmBaseScreen {

    private static final int GUI_WIDTH = 520;
    private static final int GUI_HEIGHT = 300;
    private static final int HEADER_HEIGHT = 24;
    private static final int SIDEBAR_WIDTH = 184;
    private static final int MEMBER_ROW_HEIGHT = 28;
    private static final int ROLE_ROW_HEIGHT = 28;
    private static final int TAB_HEIGHT = 18;
    private static final int ORDER_MAX_LEN = 120;
    private static final int[] TTL_PRESETS = {0, 15, 30, 60, 120, 240}; // 0 = бессрочно (минуты)

    private enum Tab { ROLE, DEPUTY, ORDER }

    private FactionManagementSnapshot snapshot;
    private int selectedMember;
    private int scroll;
    private float appear;

    private Tab activeTab;
    private String orderInput = "";
    private boolean orderFocused;
    private int ttlIndex = 2; // default 30 мин

    public FactionManagementScreen(FactionManagementSnapshot snapshot) {
        super(Component.translatable("gui.pjmbasemod.faction.manage.title"), GUI_WIDTH, GUI_HEIGHT);
        this.snapshot = snapshot;
    }

    public static void open(FactionManagementSnapshot snapshot) {
        Minecraft.getInstance().setScreen(new FactionManagementScreen(snapshot));
    }

    public void updateSnapshot(FactionManagementSnapshot snapshot) {
        UUID previous = selectedMemberEntry() == null ? null : selectedMemberEntry().playerId();
        this.snapshot = snapshot;
        selectedMember = 0;
        if (previous != null) {
            for (int i = 0; i < snapshot.members().size(); i++) {
                if (previous.equals(snapshot.members().get(i).playerId())) {
                    selectedMember = i;
                    break;
                }
            }
        }
        clampScroll();
        ensureTab();
    }

    @Override
    protected void init() {
        super.init();
        ensureTab();
    }

    private List<Tab> availableTabs() {
        List<Tab> tabs = new ArrayList<>();
        if (snapshot.viewerCanAssignRoles()) tabs.add(Tab.ROLE);
        if (snapshot.viewerCanManageDeputies()) tabs.add(Tab.DEPUTY);
        if (snapshot.viewerCanSetOrder()) tabs.add(Tab.ORDER);
        return tabs;
    }

    private void ensureTab() {
        List<Tab> tabs = availableTabs();
        if (tabs.isEmpty()) {
            activeTab = null;
            return;
        }
        if (activeTab == null || !tabs.contains(activeTab)) {
            activeTab = tabs.get(0);
        }
    }

    private int rowsVisible() {
        return Math.max(1, (GUI_HEIGHT - HEADER_HEIGHT - 20) / MEMBER_ROW_HEIGHT);
    }

    private void clampScroll() {
        int max = Math.max(0, snapshot.members().size() - rowsVisible());
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
        if (selectedMember >= snapshot.members().size()) selectedMember = Math.max(0, snapshot.members().size() - 1);
    }

    @Override
    public void tick() {
        super.tick();
        appear += (1.0F - appear) * 0.2F;
    }

    @Override
    protected boolean mouseScrolledScaled(int mouseX, int mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scroll -= (int) Math.signum(scrollY);
            clampScroll();
            return true;
        }
        return super.mouseScrolledScaled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float eased = easeOut(appear);
        int left = guiLeft();
        int top = guiTop() + Math.round((1.0F - eased) * 14.0F);
        int accent = 0xFF000000 | snapshot.teamColor();

        graphics.fill(left, top, left + GUI_WIDTH, top + GUI_HEIGHT, 0xF216161A);
        drawBorder(graphics, left, top, GUI_WIDTH, GUI_HEIGHT, 0xFF353540);
        graphics.fill(left, top, left + GUI_WIDTH, top + HEADER_HEIGHT, 0xFF1F1F26);
        graphics.fill(left, top + HEADER_HEIGHT, left + SIDEBAR_WIDTH, top + GUI_HEIGHT, 0xFF1A1A20);
        graphics.fill(left, top + HEADER_HEIGHT - 2, left + GUI_WIDTH, top + HEADER_HEIGHT, accent);

        graphics.drawString(font, getTitle(), left + 8, top + 8, 0xFFE8E8E8, false);
        graphics.drawString(font, ellipsize(snapshot.teamName(), 160), left + 180, top + 8, 0xFFD8D8D8, false);
        boolean closeHovered = mouseX >= left + GUI_WIDTH - 28 && mouseX <= left + GUI_WIDTH
                && mouseY >= top && mouseY <= top + HEADER_HEIGHT;
        graphics.drawString(font, "X", left + GUI_WIDTH - 18, top + 8,
                closeHovered ? 0xFFD06060 : 0xFFB05050, false);

        drawMembers(graphics, left, top, mouseX, mouseY);
        drawTabs(graphics, left, top, mouseX, mouseY);

        if (activeTab == Tab.ROLE) {
            drawRolePanel(graphics, left, top, mouseX, mouseY);
        } else if (activeTab == Tab.DEPUTY) {
            drawDeputyPanel(graphics, left, top, mouseX, mouseY);
        } else if (activeTab == Tab.ORDER) {
            drawOrderPanel(graphics, left, top, mouseX, mouseY);
        }
    }

    private void drawMembers(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int x = left + 8;
        int y = top + HEADER_HEIGHT + 8;
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.members"), x, y, 0xFF9AA0A6, false);
        y += 14;

        List<FactionManagementSnapshot.MemberEntry> members = snapshot.members();
        int rows = rowsVisible();
        for (int i = scroll; i < members.size() && i < scroll + rows; i++) {
            FactionManagementSnapshot.MemberEntry member = members.get(i);
            boolean selected = i == selectedMember;
            boolean hovered = mouseX >= x && mouseX <= left + SIDEBAR_WIDTH - 8
                    && mouseY >= y && mouseY <= y + MEMBER_ROW_HEIGHT - 4;
            int bg = selected ? 0xFF35506E : hovered ? 0xFF2A2A33 : 0xFF222229;
            graphics.fill(x, y, left + SIDEBAR_WIDTH - 8, y + MEMBER_ROW_HEIGHT - 4, bg);
            String prefix = member.commander() ? "[КМД] " : member.deputy() ? "[ЗАМ] " : "";
            graphics.drawString(font, ellipsize(prefix + member.name(), SIDEBAR_WIDTH - 34),
                    x + 8, y + 5, selected ? 0xFFFFFFFF : 0xFFE0E0E0, false);
            graphics.drawString(font, roleName(member.roleId()), x + 8, y + 16,
                    member.roleId().isBlank() ? 0xFF777777 : 0xFFD8B15F, false);
            y += MEMBER_ROW_HEIGHT;
        }

        if (members.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.no_members"),
                    x, y + 4, 0xFF777777, false);
        }
    }

    private void drawTabs(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<Tab> tabs = availableTabs();
        if (tabs.isEmpty()) return;
        int x = left + SIDEBAR_WIDTH + 12;
        int y = top + HEADER_HEIGHT + 6;
        int panelW = GUI_WIDTH - SIDEBAR_WIDTH - 24;
        int tabW = panelW / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int tx = x + i * tabW;
            boolean current = tab == activeTab;
            boolean hovered = mouseX >= tx && mouseX <= tx + tabW - 2 && mouseY >= y && mouseY <= y + TAB_HEIGHT;
            int bg = current ? 0xFF35506E : hovered ? 0xFF2A2A33 : 0xFF222229;
            graphics.fill(tx, y, tx + tabW - 2, y + TAB_HEIGHT, bg);
            graphics.drawCenteredString(font, tabTitle(tab), tx + (tabW - 2) / 2, y + 5,
                    current ? 0xFFFFFFFF : 0xFFCCCCCC);
        }
    }

    private Component tabTitle(Tab tab) {
        return switch (tab) {
            case ROLE -> Component.translatable("gui.pjmbasemod.faction.manage.tab.role");
            case DEPUTY -> Component.translatable("gui.pjmbasemod.faction.manage.tab.deputy");
            case ORDER -> Component.translatable("gui.pjmbasemod.faction.manage.tab.order");
        };
    }

    private int contentTop(int top) {
        return top + HEADER_HEIGHT + 6 + TAB_HEIGHT + 8;
    }

    private void drawRolePanel(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        FactionManagementSnapshot.MemberEntry member = selectedMemberEntry();
        int x = left + SIDEBAR_WIDTH + 12;
        int y = contentTop(top);
        int w = GUI_WIDTH - SIDEBAR_WIDTH - 24;

        if (member == null) {
            graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.select_member"),
                    x, y, 0xFF888888, false);
            return;
        }

        graphics.drawString(font, member.name(), x, y, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.current_role",
                roleName(member.roleId())), x, y + 12, 0xFF9AA0A6, false);
        y += 30;

        for (FactionSelectionSnapshot.RoleEntry role : snapshot.roles()) {
            boolean selected = role.id().equals(member.roleId());
            boolean available = role.available() || selected;
            boolean hovered = available && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ROLE_ROW_HEIGHT - 4;
            int bg = !available ? 0xFF1D1D22 : selected ? 0xFF2B3E52 : hovered ? 0xFF2A2A33 : 0xFF222229;
            graphics.fill(x, y, x + w, y + ROLE_ROW_HEIGHT - 4, bg);
            graphics.fill(x, y, x + 3, y + ROLE_ROW_HEIGHT - 4, 0xFF000000 | role.color());
            graphics.drawString(font, ellipsize(role.displayName(), w - 116),
                    x + 10, y + 7, available ? 0xFFE8E8E8 : 0xFF777777, false);
            graphics.drawString(font, roleLimitText(role), x + w - 82, y + 7,
                    role.disabled() || role.full() ? 0xFFD8B15F : 0xFF9AA0A6, false);
            y += ROLE_ROW_HEIGHT;
        }

        int clearY = top + GUI_HEIGHT - 32;
        boolean clearHovered = mouseX >= x && mouseX <= x + w && mouseY >= clearY && mouseY <= clearY + 22;
        graphics.fill(x, clearY, x + w, clearY + 22, clearHovered ? 0xFF7A463E : 0xFF5A342E);
        graphics.drawCenteredString(font, Component.translatable("gui.pjmbasemod.faction.manage.clear_role"),
                x + w / 2, clearY + 7, 0xFFFFFFFF);
    }

    private void drawDeputyPanel(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        FactionManagementSnapshot.MemberEntry member = selectedMemberEntry();
        int x = left + SIDEBAR_WIDTH + 12;
        int y = contentTop(top);
        int w = GUI_WIDTH - SIDEBAR_WIDTH - 24;

        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.deputy.count",
                snapshot.deputyCount(), snapshot.maxDeputies()), x, y, 0xFF9AA0A6, false);
        y += 18;

        if (member == null) {
            graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.select_member"),
                    x, y, 0xFF888888, false);
            return;
        }

        graphics.drawString(font, member.name(), x, y, 0xFFFFFFFF, false);
        y += 18;

        // Переключатель «Назначить замом»
        boolean isDeputy = member.deputy();
        boolean toggleHovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 22;
        int toggleBg = isDeputy ? 0xFF2E5E3A : toggleHovered ? 0xFF2A2A33 : 0xFF222229;
        graphics.fill(x, y, x + w, y + 22, toggleBg);
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.deputy.toggle"), x + 10, y + 7,
                0xFFE8E8E8, false);
        graphics.drawString(font, isDeputy ? "ON" : "OFF", x + w - 30, y + 7, isDeputy ? 0xFF7CD68A : 0xFF888888, false);
        y += 30;

        // Чекбоксы прав (активны только если зам)
        DeputyPermission[] perms = DeputyPermission.values();
        for (DeputyPermission perm : perms) {
            boolean checked = DeputyPermission.has(member.deputyPerms(), perm);
            boolean enabled = isDeputy;
            boolean hovered = enabled && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 20;
            int bg = hovered ? 0xFF2A2A33 : 0xFF1E1E24;
            graphics.fill(x, y, x + w, y + 20, bg);
            int box = enabled ? (checked ? 0xFF7CD68A : 0xFF555560) : 0xFF333338;
            graphics.fill(x + 6, y + 5, x + 16, y + 15, box);
            graphics.drawString(font, permTitle(perm), x + 24, y + 6,
                    enabled ? 0xFFE8E8E8 : 0xFF666666, false);
            y += 24;
        }
    }

    private Component permTitle(DeputyPermission perm) {
        return switch (perm) {
            case ASSIGN_ROLES -> Component.translatable("gui.pjmbasemod.faction.manage.deputy.perm.assign_roles");
            case SET_ORDER -> Component.translatable("gui.pjmbasemod.faction.manage.deputy.perm.set_order");
            case OPEN_GUI -> Component.translatable("gui.pjmbasemod.faction.manage.deputy.perm.open_gui");
        };
    }

    private void drawOrderPanel(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int x = left + SIDEBAR_WIDTH + 12;
        int y = contentTop(top);
        int w = GUI_WIDTH - SIDEBAR_WIDTH - 24;

        // Текущий приказ
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.order.current"), x, y, 0xFF9AA0A6, false);
        y += 12;
        if (snapshot.orderText().isBlank()) {
            graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.order.none"), x, y, 0xFF777777, false);
        } else {
            graphics.drawString(font, ellipsize(snapshot.orderText(), w), x, y, 0xFFE8E8E8, false);
            y += 11;
            String meta = Component.translatable("gui.pjmbasemod.faction.order.author", snapshot.orderAuthor()).getString();
            if (snapshot.orderSecondsRemaining() >= 0) {
                meta += "  " + Component.translatable("gui.pjmbasemod.faction.order.remaining",
                        snapshot.orderSecondsRemaining()).getString();
            } else {
                meta += "  " + Component.translatable("gui.pjmbasemod.faction.order.remaining_permanent").getString();
            }
            graphics.drawString(font, meta, x, y, 0xFF888888, false);
        }
        y += 22;

        // Поле ввода
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.order.title"), x, y, 0xFF9AA0A6, false);
        y += 12;
        int boxH = 22;
        graphics.fill(x, y, x + w, y + boxH, orderFocused ? 0xFF2A3550 : 0xFF222229);
        drawBorder(graphics, x, y, w, boxH, orderFocused ? 0xFF4A6A9E : 0xFF353540);
        String shown = orderInput;
        boolean caret = orderFocused && (System.currentTimeMillis() / 500L) % 2 == 0;
        String display = ellipsize(shown.isEmpty() && !orderFocused
                ? Component.translatable("gui.pjmbasemod.faction.order.placeholder").getString() : shown, w - 12);
        int textColor = shown.isEmpty() && !orderFocused ? 0xFF666666 : 0xFFE8E8E8;
        graphics.drawString(font, display + (caret ? "_" : ""), x + 6, y + 7, textColor, false);
        y += boxH + 8;

        // TTL-переключатель
        int ttlW = w / 2 - 4;
        boolean ttlHovered = mouseX >= x && mouseX <= x + ttlW && mouseY >= y && mouseY <= y + 22;
        graphics.fill(x, y, x + ttlW, y + 22, ttlHovered ? 0xFF2A2A33 : 0xFF222229);
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.order.ttl", ttlLabel()),
                x + 8, y + 7, 0xFFE8E8E8, false);

        // Кнопка «Отправить»
        int sendX = x + ttlW + 8;
        int sendW = w - ttlW - 8;
        boolean sendHovered = mouseX >= sendX && mouseX <= sendX + sendW && mouseY >= y && mouseY <= y + 22;
        graphics.fill(sendX, y, sendX + sendW, y + 22, sendHovered ? 0xFF2E5E3A : 0xFF274D31);
        graphics.drawCenteredString(font, Component.translatable("gui.pjmbasemod.faction.order.send"),
                sendX + sendW / 2, y + 7, 0xFFFFFFFF);
        y += 30;

        // Кнопка «Снять»
        boolean clearHovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 22;
        graphics.fill(x, y, x + w, y + 22, clearHovered ? 0xFF7A463E : 0xFF5A342E);
        graphics.drawCenteredString(font, Component.translatable("gui.pjmbasemod.faction.order.clear"),
                x + w / 2, y + 7, 0xFFFFFFFF);
    }

    private String ttlLabel() {
        int ttl = TTL_PRESETS[ttlIndex];
        return ttl <= 0
                ? Component.translatable("gui.pjmbasemod.faction.order.ttl_permanent").getString()
                : Component.translatable("gui.pjmbasemod.faction.order.ttl_minutes", ttl).getString();
    }

    @Override
    protected boolean mouseClickedScaled(int mouseX, int mouseY, int button) {
        if (button != 0) return super.mouseClickedScaled(mouseX, mouseY, button);
        int left = guiLeft();
        int top = guiTop() + Math.round((1.0F - easeOut(appear)) * 14.0F);

        // Закрытие
        if (mouseX >= left + GUI_WIDTH - 28 && mouseX <= left + GUI_WIDTH
                && mouseY >= top && mouseY <= top + HEADER_HEIGHT) {
            PjmUiSounds.playClick();
            onClose();
            return true;
        }

        // Список членов
        int memberX = left + 8;
        int memberY = top + HEADER_HEIGHT + 22;
        for (int i = scroll; i < snapshot.members().size() && i < scroll + rowsVisible(); i++) {
            int y = memberY + (i - scroll) * MEMBER_ROW_HEIGHT;
            if (mouseX >= memberX && mouseX <= left + SIDEBAR_WIDTH - 8
                    && mouseY >= y && mouseY <= y + MEMBER_ROW_HEIGHT - 4) {
                selectedMember = i;
                orderFocused = false;
                PjmUiSounds.playClick();
                return true;
            }
        }

        // Табы
        List<Tab> tabs = availableTabs();
        int tabX = left + SIDEBAR_WIDTH + 12;
        int tabY = top + HEADER_HEIGHT + 6;
        int panelW = GUI_WIDTH - SIDEBAR_WIDTH - 24;
        if (!tabs.isEmpty()) {
            int tabW = panelW / tabs.size();
            for (int i = 0; i < tabs.size(); i++) {
                int tx = tabX + i * tabW;
                if (mouseX >= tx && mouseX <= tx + tabW - 2 && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT) {
                    activeTab = tabs.get(i);
                    orderFocused = false;
                    PjmUiSounds.playClick();
                    return true;
                }
            }
        }

        if (activeTab == Tab.ROLE) return roleClick(left, top, mouseX, mouseY);
        if (activeTab == Tab.DEPUTY) return deputyClick(left, top, mouseX, mouseY);
        if (activeTab == Tab.ORDER) return orderClick(left, top, mouseX, mouseY);
        return super.mouseClickedScaled(mouseX, mouseY, button);
    }

    private boolean roleClick(int left, int top, int mouseX, int mouseY) {
        FactionManagementSnapshot.MemberEntry member = selectedMemberEntry();
        if (member == null) return true;
        int x = left + SIDEBAR_WIDTH + 12;
        int w = GUI_WIDTH - SIDEBAR_WIDTH - 24;
        int y = contentTop(top) + 30;
        for (FactionSelectionSnapshot.RoleEntry role : snapshot.roles()) {
            boolean selected = role.id().equals(member.roleId());
            boolean available = role.available() || selected;
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ROLE_ROW_HEIGHT - 4) {
                if (available) {
                    PjmUiSounds.playPress();
                    PjmNetworking.sendToServer(new ManageFactionRolePacket(member.playerId(), role.id()));
                }
                return true;
            }
            y += ROLE_ROW_HEIGHT;
        }
        int clearY = top + GUI_HEIGHT - 32;
        if (mouseX >= x && mouseX <= x + w && mouseY >= clearY && mouseY <= clearY + 22) {
            PjmUiSounds.playPress();
            PjmNetworking.sendToServer(new ManageFactionRolePacket(member.playerId(), ""));
            return true;
        }
        return true;
    }

    private boolean deputyClick(int left, int top, int mouseX, int mouseY) {
        FactionManagementSnapshot.MemberEntry member = selectedMemberEntry();
        if (member == null) return true;
        int x = left + SIDEBAR_WIDTH + 12;
        int w = GUI_WIDTH - SIDEBAR_WIDTH - 24;
        int y = contentTop(top) + 18 + 18; // count + name

        // Toggle
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 22) {
            boolean newDeputy = !member.deputy();
            int perms = newDeputy ? member.deputyPerms() : 0;
            PjmUiSounds.playPress();
            PjmNetworking.sendToServer(new ManageFactionDeputyPacket(member.playerId(), newDeputy, perms));
            return true;
        }
        y += 30;

        // Чекбоксы прав
        if (member.deputy()) {
            for (DeputyPermission perm : DeputyPermission.values()) {
                if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 20) {
                    int perms = member.deputyPerms() ^ perm.bit();
                    PjmUiSounds.playClick();
                    PjmNetworking.sendToServer(new ManageFactionDeputyPacket(member.playerId(), true,
                            DeputyPermission.sanitize(perms)));
                    return true;
                }
                y += 24;
            }
        }
        return true;
    }

    private boolean orderClick(int left, int top, int mouseX, int mouseY) {
        int x = left + SIDEBAR_WIDTH + 12;
        int w = GUI_WIDTH - SIDEBAR_WIDTH - 24;
        int y = contentTop(top);
        // высота блока «текущий приказ»
        y += 12 + (snapshot.orderText().isBlank() ? 11 : 22) + 22;
        // заголовок поля
        y += 12;
        int boxH = 22;
        // Поле ввода → фокус
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + boxH) {
            orderFocused = true;
            PjmUiSounds.playClick();
            return true;
        }
        orderFocused = false;
        y += boxH + 8;

        int ttlW = w / 2 - 4;
        if (mouseX >= x && mouseX <= x + ttlW && mouseY >= y && mouseY <= y + 22) {
            ttlIndex = (ttlIndex + 1) % TTL_PRESETS.length;
            PjmUiSounds.playClick();
            return true;
        }
        int sendX = x + ttlW + 8;
        int sendW = w - ttlW - 8;
        if (mouseX >= sendX && mouseX <= sendX + sendW && mouseY >= y && mouseY <= y + 22) {
            sendOrder();
            return true;
        }
        y += 30;
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 22) {
            PjmUiSounds.playPress();
            PjmNetworking.sendToServer(new SetFactionOrderPacket("", 0));
            return true;
        }
        return true;
    }

    private void sendOrder() {
        if (orderInput.isBlank()) return;
        PjmUiSounds.playPress();
        PjmNetworking.sendToServer(new SetFactionOrderPacket(orderInput.trim(), TTL_PRESETS[ttlIndex]));
        orderInput = "";
        orderFocused = false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (orderFocused && activeTab == Tab.ORDER) {
            if (chr >= ' ' && orderInput.length() < ORDER_MAX_LEN) {
                orderInput += chr;
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (orderFocused && activeTab == Tab.ORDER) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!orderInput.isEmpty()) orderInput = orderInput.substring(0, orderInput.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                sendOrder();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                orderFocused = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Nullable
    private FactionManagementSnapshot.MemberEntry selectedMemberEntry() {
        List<FactionManagementSnapshot.MemberEntry> members = snapshot.members();
        return selectedMember >= 0 && selectedMember < members.size() ? members.get(selectedMember) : null;
    }

    private String roleName(String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return Component.translatable("gui.pjmbasemod.radial.role_none").getString();
        }
        return Component.translatable("role.pjmbasemod." + roleId).getString();
    }

    private String roleLimitText(FactionSelectionSnapshot.RoleEntry role) {
        if (role.limit() < 0) {
            return Component.translatable("gui.pjmbasemod.faction.role_limit_unlimited", role.current()).getString();
        }
        return Component.translatable("gui.pjmbasemod.faction.role_limit", role.current(), role.limit()).getString();
    }

    private String ellipsize(String text, int maxWidth) {
        return PjmGuiUtils.ellipsize(font, text, maxWidth);
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        PjmGuiUtils.drawBorder(graphics, x, y, w, h, color);
    }

    private static float easeOut(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
```

- [ ] **Step 2: Проверить наличие PjmUiSounds.playPress / playClick**

Run: `grep -n "public static void play" src/client/java/ru/liko/pjmbasemod/client/gui/PjmUiSounds.java`
Expected: присутствуют `playClick` и `playPress` (используются в старом коде экрана — значит есть). Если `playPress` нет — заменить вызовы `PjmUiSounds.playPress()` на `PjmUiSounds.playClick()`.

- [ ] **Step 3: Компиляция client**

Run: `./gradlew compileClientJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/client/java/ru/liko/pjmbasemod/client/gui/screen/FactionManagementScreen.java
git commit -m "feat(faction): экран управления с табами Роль/Зам/Приказ"
```

---

### Task 10: Локализация (5 языков)

**Files:**
- Modify: `src/client/resources/assets/pjmbasemod/lang/ru_ru.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/en_us.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/uk_ua.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/de_de.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/zh_cn.json`

**Interfaces:** ключи, использованные в Task 5/7/8/9.

- [ ] **Step 1: Найти существующий faction-блок как якорь**

Run: `grep -n "gui.pjmbasemod.faction.manage.title" src/client/resources/assets/pjmbasemod/lang/ru_ru.json`
Expected: одна строка — рядом с ней вставлять новые ключи.

- [ ] **Step 2: ru_ru.json — добавить ключи**

Вставить (в любое место объекта, соблюдая запятые):

```json
  "gui.pjmbasemod.faction.manage.tab.role": "Роль",
  "gui.pjmbasemod.faction.manage.tab.deputy": "Заместитель",
  "gui.pjmbasemod.faction.manage.tab.order": "Приказ",
  "gui.pjmbasemod.faction.manage.deputy.toggle": "Назначить замом",
  "gui.pjmbasemod.faction.manage.deputy.count": "Замы: %s / %s",
  "gui.pjmbasemod.faction.manage.deputy.limit_reached": "Достигнут лимит заместителей",
  "gui.pjmbasemod.faction.manage.deputy.added": "%s назначен заместителем",
  "gui.pjmbasemod.faction.manage.deputy.removed": "%s снят с должности заместителя",
  "gui.pjmbasemod.faction.manage.deputy.perm.assign_roles": "Назначать роли",
  "gui.pjmbasemod.faction.manage.deputy.perm.set_order": "Задавать приказ",
  "gui.pjmbasemod.faction.manage.deputy.perm.open_gui": "Доступ к меню",
  "gui.pjmbasemod.faction.order.title": "Новый приказ",
  "gui.pjmbasemod.faction.order.placeholder": "Введите текст приказа…",
  "gui.pjmbasemod.faction.order.ttl": "Срок: %s",
  "gui.pjmbasemod.faction.order.ttl_permanent": "бессрочно",
  "gui.pjmbasemod.faction.order.ttl_minutes": "%s мин",
  "gui.pjmbasemod.faction.order.send": "Отправить",
  "gui.pjmbasemod.faction.order.clear": "Снять приказ",
  "gui.pjmbasemod.faction.order.current": "Текущий приказ:",
  "gui.pjmbasemod.faction.order.none": "Приказа нет",
  "gui.pjmbasemod.faction.order.author": "— %s",
  "gui.pjmbasemod.faction.order.remaining": "осталось %s с",
  "gui.pjmbasemod.faction.order.remaining_permanent": "бессрочно",
  "gui.pjmbasemod.faction.order.notify_title": "Приказ фракции",
  "gui.pjmbasemod.faction.order.hud_prefix": "ПРИКАЗ"
```

- [ ] **Step 3: en_us.json — добавить ключи**

```json
  "gui.pjmbasemod.faction.manage.tab.role": "Role",
  "gui.pjmbasemod.faction.manage.tab.deputy": "Deputy",
  "gui.pjmbasemod.faction.manage.tab.order": "Order",
  "gui.pjmbasemod.faction.manage.deputy.toggle": "Make Deputy",
  "gui.pjmbasemod.faction.manage.deputy.count": "Deputies: %s / %s",
  "gui.pjmbasemod.faction.manage.deputy.limit_reached": "Deputy limit reached",
  "gui.pjmbasemod.faction.manage.deputy.added": "%s is now a deputy",
  "gui.pjmbasemod.faction.manage.deputy.removed": "%s is no longer a deputy",
  "gui.pjmbasemod.faction.manage.deputy.perm.assign_roles": "Assign roles",
  "gui.pjmbasemod.faction.manage.deputy.perm.set_order": "Set order",
  "gui.pjmbasemod.faction.manage.deputy.perm.open_gui": "Open this menu",
  "gui.pjmbasemod.faction.order.title": "New order",
  "gui.pjmbasemod.faction.order.placeholder": "Enter order text…",
  "gui.pjmbasemod.faction.order.ttl": "Duration: %s",
  "gui.pjmbasemod.faction.order.ttl_permanent": "permanent",
  "gui.pjmbasemod.faction.order.ttl_minutes": "%s min",
  "gui.pjmbasemod.faction.order.send": "Send",
  "gui.pjmbasemod.faction.order.clear": "Clear order",
  "gui.pjmbasemod.faction.order.current": "Current order:",
  "gui.pjmbasemod.faction.order.none": "No order",
  "gui.pjmbasemod.faction.order.author": "— %s",
  "gui.pjmbasemod.faction.order.remaining": "%s s left",
  "gui.pjmbasemod.faction.order.remaining_permanent": "permanent",
  "gui.pjmbasemod.faction.order.notify_title": "Faction order",
  "gui.pjmbasemod.faction.order.hud_prefix": "ORDER"
```

- [ ] **Step 4: uk_ua.json — добавить ключи**

```json
  "gui.pjmbasemod.faction.manage.tab.role": "Роль",
  "gui.pjmbasemod.faction.manage.tab.deputy": "Заступник",
  "gui.pjmbasemod.faction.manage.tab.order": "Наказ",
  "gui.pjmbasemod.faction.manage.deputy.toggle": "Призначити заступником",
  "gui.pjmbasemod.faction.manage.deputy.count": "Заступники: %s / %s",
  "gui.pjmbasemod.faction.manage.deputy.limit_reached": "Досягнуто ліміту заступників",
  "gui.pjmbasemod.faction.manage.deputy.added": "%s призначений заступником",
  "gui.pjmbasemod.faction.manage.deputy.removed": "%s знятий з посади заступника",
  "gui.pjmbasemod.faction.manage.deputy.perm.assign_roles": "Призначати ролі",
  "gui.pjmbasemod.faction.manage.deputy.perm.set_order": "Задавати наказ",
  "gui.pjmbasemod.faction.manage.deputy.perm.open_gui": "Доступ до меню",
  "gui.pjmbasemod.faction.order.title": "Новий наказ",
  "gui.pjmbasemod.faction.order.placeholder": "Введіть текст наказу…",
  "gui.pjmbasemod.faction.order.ttl": "Термін: %s",
  "gui.pjmbasemod.faction.order.ttl_permanent": "безстроково",
  "gui.pjmbasemod.faction.order.ttl_minutes": "%s хв",
  "gui.pjmbasemod.faction.order.send": "Надіслати",
  "gui.pjmbasemod.faction.order.clear": "Зняти наказ",
  "gui.pjmbasemod.faction.order.current": "Поточний наказ:",
  "gui.pjmbasemod.faction.order.none": "Наказу немає",
  "gui.pjmbasemod.faction.order.author": "— %s",
  "gui.pjmbasemod.faction.order.remaining": "залишилось %s с",
  "gui.pjmbasemod.faction.order.remaining_permanent": "безстроково",
  "gui.pjmbasemod.faction.order.notify_title": "Наказ фракції",
  "gui.pjmbasemod.faction.order.hud_prefix": "НАКАЗ"
```

- [ ] **Step 5: de_de.json — добавить ключи**

```json
  "gui.pjmbasemod.faction.manage.tab.role": "Rolle",
  "gui.pjmbasemod.faction.manage.tab.deputy": "Stellvertreter",
  "gui.pjmbasemod.faction.manage.tab.order": "Befehl",
  "gui.pjmbasemod.faction.manage.deputy.toggle": "Zum Stellvertreter ernennen",
  "gui.pjmbasemod.faction.manage.deputy.count": "Stellvertreter: %s / %s",
  "gui.pjmbasemod.faction.manage.deputy.limit_reached": "Stellvertreter-Limit erreicht",
  "gui.pjmbasemod.faction.manage.deputy.added": "%s ist jetzt Stellvertreter",
  "gui.pjmbasemod.faction.manage.deputy.removed": "%s ist nicht mehr Stellvertreter",
  "gui.pjmbasemod.faction.manage.deputy.perm.assign_roles": "Rollen zuweisen",
  "gui.pjmbasemod.faction.manage.deputy.perm.set_order": "Befehl setzen",
  "gui.pjmbasemod.faction.manage.deputy.perm.open_gui": "Dieses Menü öffnen",
  "gui.pjmbasemod.faction.order.title": "Neuer Befehl",
  "gui.pjmbasemod.faction.order.placeholder": "Befehlstext eingeben…",
  "gui.pjmbasemod.faction.order.ttl": "Dauer: %s",
  "gui.pjmbasemod.faction.order.ttl_permanent": "dauerhaft",
  "gui.pjmbasemod.faction.order.ttl_minutes": "%s Min",
  "gui.pjmbasemod.faction.order.send": "Senden",
  "gui.pjmbasemod.faction.order.clear": "Befehl aufheben",
  "gui.pjmbasemod.faction.order.current": "Aktueller Befehl:",
  "gui.pjmbasemod.faction.order.none": "Kein Befehl",
  "gui.pjmbasemod.faction.order.author": "— %s",
  "gui.pjmbasemod.faction.order.remaining": "noch %s s",
  "gui.pjmbasemod.faction.order.remaining_permanent": "dauerhaft",
  "gui.pjmbasemod.faction.order.notify_title": "Fraktionsbefehl",
  "gui.pjmbasemod.faction.order.hud_prefix": "BEFEHL"
```

- [ ] **Step 6: zh_cn.json — добавить ключи**

```json
  "gui.pjmbasemod.faction.manage.tab.role": "职责",
  "gui.pjmbasemod.faction.manage.tab.deputy": "副官",
  "gui.pjmbasemod.faction.manage.tab.order": "命令",
  "gui.pjmbasemod.faction.manage.deputy.toggle": "任命为副官",
  "gui.pjmbasemod.faction.manage.deputy.count": "副官：%s / %s",
  "gui.pjmbasemod.faction.manage.deputy.limit_reached": "副官数量已达上限",
  "gui.pjmbasemod.faction.manage.deputy.added": "%s 已被任命为副官",
  "gui.pjmbasemod.faction.manage.deputy.removed": "%s 已被解除副官职务",
  "gui.pjmbasemod.faction.manage.deputy.perm.assign_roles": "分配职责",
  "gui.pjmbasemod.faction.manage.deputy.perm.set_order": "下达命令",
  "gui.pjmbasemod.faction.manage.deputy.perm.open_gui": "打开此菜单",
  "gui.pjmbasemod.faction.order.title": "新命令",
  "gui.pjmbasemod.faction.order.placeholder": "输入命令内容…",
  "gui.pjmbasemod.faction.order.ttl": "时长：%s",
  "gui.pjmbasemod.faction.order.ttl_permanent": "永久",
  "gui.pjmbasemod.faction.order.ttl_minutes": "%s 分钟",
  "gui.pjmbasemod.faction.order.send": "发送",
  "gui.pjmbasemod.faction.order.clear": "取消命令",
  "gui.pjmbasemod.faction.order.current": "当前命令：",
  "gui.pjmbasemod.faction.order.none": "暂无命令",
  "gui.pjmbasemod.faction.order.author": "— %s",
  "gui.pjmbasemod.faction.order.remaining": "剩余 %s 秒",
  "gui.pjmbasemod.faction.order.remaining_permanent": "永久",
  "gui.pjmbasemod.faction.order.notify_title": "阵营命令",
  "gui.pjmbasemod.faction.order.hud_prefix": "命令"
```

- [ ] **Step 7: Валидация JSON всех 5 файлов**

Run:
```bash
for f in ru_ru en_us uk_ua de_de zh_cn; do python3 -m json.tool "src/client/resources/assets/pjmbasemod/lang/$f.json" > /dev/null && echo "$f OK" || echo "$f FAIL"; done
```
Expected: пять строк `OK`.

- [ ] **Step 8: Commit**

```bash
git add src/client/resources/assets/pjmbasemod/lang/
git commit -m "i18n(faction): ключи замов и приказа во всех 5 языках"
```

---

### Task 11: Документация

**Files:**
- Create: `docs/FACTION_MANAGEMENT.md`
- Modify: `CLAUDE.md` (раздел faction + индекс доков)

**Interfaces:** нет (только текст).

- [ ] **Step 1: Создать docs/FACTION_MANAGEMENT.md**

Содержание: назначение экрана; кто имеет доступ (командир/админ/зам с `OPEN_GUI`); табы Роль/Зам/Приказ и их права (`ASSIGN_ROLES`/`SET_ORDER`/`OPEN_GUI`); матрица доступа; назначение замов и лимит (`faction.maxDeputies`); приказ — текст, TTL-пресеты (`0`=бессрочно), HUD-плашка, разовое уведомление; конфиг-секция `faction` (`maxDeputies`, `orderMaxLength`, `orderDefaultTtlMinutes`, `orderMaxTtlMinutes`); персистентность (`FactionDeputySavedData`, `FactionOrderSavedData`); пакеты (`ManageFactionDeputyPacket`, `SetFactionOrderPacket` C→S; `FactionOrderSyncPacket` S→C); открытие через `/pjm faction manage` и `/pjm debug open faction_management`.

Документ должен описывать ТОЛЬКО реально реализованное в этом плане (без идеализаций).

- [ ] **Step 2: Обновить раздел `### faction` в CLAUDE.md**

Дополнить описание подсистемы faction строками о новых классах:

```markdown
`FactionDeputySavedData` — заместители фракции (teamId → playerId → битмаска прав), enum `DeputyPermission` (ASSIGN_ROLES/SET_ORDER/OPEN_GUI).
`FactionOrderSavedData`, `FactionOrderManager` — приказ фракции (текст + TTL), постоянная HUD-плашка у всех членов команды, разовое уведомление. Клиентское зеркало `ClientFactionOrderState`, overlay `FactionOrderHudOverlay`.
`FactionMenuService.Authority` — права зрителя (canOpen/canAssignRoles/canSetOrder/canManageDeputies). Конфиг-секция `faction` (maxDeputies, orderMaxLength, orderDefaultTtlMinutes, orderMaxTtlMinutes).
```

- [ ] **Step 3: Добавить строку в индекс доков в CLAUDE.md**

В таблицу раздела «Документация (`docs/`)» добавить строку:

```markdown
| [`docs/FACTION_MANAGEMENT.md`](./docs/FACTION_MANAGEMENT.md) | Экран управления фракцией: командир/зам, права заместителей, приказ фракции с TTL, конфиг `faction`. |
```

- [ ] **Step 4: Commit**

```bash
git add docs/FACTION_MANAGEMENT.md CLAUDE.md
git commit -m "docs(faction): документация управления фракцией (замы + приказ)"
```

---

## Self-Review

**Spec coverage:**
- Зам с настраиваемыми правами → Task 1 (enum), Task 2 (savedata), Task 5 (Authority, handleManageDeputy), Task 9 (вкладка Зам). ✓
- Несколько замов, лимит из конфига → Task 1 (`maxDeputies`), Task 5 (проверка лимита). ✓
- Права `ASSIGN_ROLES/SET_ORDER/OPEN_GUI` → Task 1, Task 5, Task 9. ✓
- Приказ через notification, постоянный, TTL → Task 3 (savedata), Task 7 (manager, NotificationPacket + sync), Task 8 (HUD). ✓
- Скрывать недоступные табы → Task 9 (`availableTabs`). ✓
- Без новых команд → план не добавляет команд. ✓
- Бамп VERSION → Task 6 (`"18"`→`"19"`). ✓
- Локализация 5 языков → Task 10. ✓
- Доки → Task 11. ✓

**Placeholder scan:** код приведён полностью для всех новых файлов; модификации — точные фрагменты. Нет «TODO/добавить обработку».

**Type consistency:**
- `DeputyPermission`: `bit()`, `pack`, `has`, `all`, `sanitize` — согласованы между Task 1, 5, 9.
- `Authority(teamId, canOpen, canAssignRoles, canSetOrder, canManageDeputies)` + `valid()` — Task 5; используется в Task 5/7 одинаково.
- `FactionManagementSnapshot` новый конструктор и `MemberEntry(...,deputy,deputyPerms)` — Task 4; заполняется в Task 5, читается в Task 9.
- `FactionOrderSavedData.OrderEntry(text, author, setAtGameTime, expiresAtGameTime)` — Task 3; используется в Task 5/7.
- `FactionOrderSyncPacket(active,text,author,teamColor,secondsRemaining)` — Task 6; строится в Task 7, читается в Task 8.
- `ClientFactionOrderState.State(text,author,color,secondsRemaining)` + `current()`/`remainingSeconds()` — Task 8; используется в Task 8 (overlay).

**Известные риски для исполнителя:**
- Координаты в `orderClick` должны точно соответствовать `drawOrderPanel` — оба метода используют одинаковую пошаговую инкрементацию `y`. При правке одного синхронно править второй.
- `PjmUiSounds.playPress` — проверка в Task 9 Step 2.
- Удаление `managementTeam`/`Nullable` импорта — только после grep-проверки (Task 5 Step 10).
