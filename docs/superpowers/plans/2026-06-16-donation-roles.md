# Донатные боевые роли (Фича A) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Сделать часть боевых ролей доступными только за донат (LuckPerms), не создавая «киборга» и сохранив игроку свободу самому переключаться между купленными ролями.

**Architecture:** Разделяем «владение» ролью (permission node `pjmbasemod.role.unlock.<id>`, выдаёт LuckPerms) и «активную» роль (`RoleSavedData`, всегда одна). Донатность ролей задаётся JSON-реестром `RoleAccessRegistry`. `RoleService.assignRole` получает проверку владения и гибридное самоназначение (донат-роль игрок ставит себе сам, обычные — командир). Клиент получает набор «доступных себе» ролей отдельным пакетом и рисует недоступные роли в радиальном меню заблокированными.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.172, NeoForge Permission API (backend — LuckPerms), Gson, NeoForge networking (PayloadRegistrar / StreamCodec).

## Global Constraints

- **Верификация — компиляцией, не тестами.** В проекте нет тестового харнесса; `runClient`/`runServer` не работают из этого пути (символ `!`). Каждая задача проверяется `./gradlew compileJava` (common) и `./gradlew compileClientJava` (client) + валидацией JSON. Внутриигровую проверку выполняет пользователь.
- **Source set isolation:** `main` (common) НИКОГДА не импортирует `client`. Клиентские изменения — только в `src/client`.
- **Сетевой VERSION:** при изменении состава пакетов бампать `PjmNetworking.VERSION` (сейчас `"16"` → `"17"`).
- **Permission node namespace:** `pjmbasemod` (MODID). Пути нод — `role.unlock.<roleId>`.
- **Конфиги** мода читаются из `config/pjmbasemod/...` (FMLPaths.CONFIGDIR), НЕ из ресурсов мода.
- **Локализация — во все 5 языков:** `ru_ru`, `en_us`, `uk_ua`, `de_de`, `zh_cn` (в `src/client/resources/assets/pjmbasemod/lang/`).
- **CombatRole** содержит 8 значений: `ASSAULT`, `MACHINE_GUNNER`, `SNIPER`, `UAV_OPERATOR`, `SSO`, `MARKSMAN`, `EW_SPECIALIST`, `CREW`. Метод `role.id()` даёт строковый id, `CombatRole.byIdOrAlias(String)` — обратный резолв.

---

### Task 1: `RoleAccessRegistry` — JSON-реестр донатности ролей

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/role/RoleAccessRegistry.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/event/PjmServerEvents.java` (метод `onServerStarted`)

**Interfaces:**
- Produces:
  - `RoleAccessRegistry.get()` → singleton
  - `RoleAccessRegistry.get().reload()` → `int` (число донат-ролей)
  - `RoleAccessRegistry.get().isPaid(CombatRole)` → `boolean`

- [ ] **Step 1: Создать `RoleAccessRegistry` по образцу `RoleLimitRegistry`**

Файл `src/main/java/ru/liko/pjmbasemod/common/role/RoleAccessRegistry.java`:

```java
package ru.liko.pjmbasemod.common.role;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Какие боевые роли являются донатными. Загружается из
 * config/pjmbasemod/roles/access.json. Донатная роль требует permission node
 * pjmbasemod.role.unlock.<id> (см. RolePermissions).
 */
public final class RoleAccessRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final RoleAccessRegistry INSTANCE = new RoleAccessRegistry();

    private final Set<String> paidRoles = new LinkedHashSet<>();

    private RoleAccessRegistry() {
    }

    public static RoleAccessRegistry get() {
        return INSTANCE;
    }

    private Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(Pjmbasemod.MODID).resolve("roles");
    }

    private Path configFile() {
        return directory().resolve("access.json");
    }

    public synchronized int reload() {
        paidRoles.clear();
        Path dir = directory();
        Path file = configFile();
        try {
            Files.createDirectories(dir);
            if (!Files.isRegularFile(file)) {
                writeExampleConfig(file);
            }
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Roles: не удалось подготовить конфиг доступа {}", file, e);
            return 0;
        }

        if (Files.isRegularFile(file)) {
            loadConfigFile(file);
        }

        Pjmbasemod.LOGGER.info("Roles: загружены донат-роли: {}", paidRoles);
        return paidRoles.size();
    }

    public synchronized boolean isPaid(CombatRole role) {
        return role != null && paidRoles.contains(role.id());
    }

    private void loadConfigFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            if (root == null || !root.isJsonObject()) {
                Pjmbasemod.LOGGER.warn("Roles: {} должен быть JSON-объектом", file.getFileName());
                return;
            }
            JsonObject roles = root.getAsJsonObject().getAsJsonObject("roles");
            if (roles == null) {
                Pjmbasemod.LOGGER.warn("Roles: {} должен содержать объект roles", file.getFileName());
                return;
            }
            for (Map.Entry<String, JsonElement> entry : roles.entrySet()) {
                CombatRole role = CombatRole.byIdOrAlias(entry.getKey());
                if (role == null) {
                    Pjmbasemod.LOGGER.warn("Roles: пропущена неизвестная роль '{}'", entry.getKey());
                    continue;
                }
                if (entry.getValue().isJsonObject()) {
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    if (obj.has("paid") && obj.get("paid").getAsBoolean()) {
                        paidRoles.add(role.id());
                    }
                }
            }
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("Roles: не удалось прочитать конфиг доступа {}", file.getFileName(), e);
            paidRoles.clear();
        }
    }

    private void writeExampleConfig(Path file) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        JsonObject roles = new JsonObject();
        for (CombatRole role : CombatRole.values()) {
            JsonObject obj = new JsonObject();
            boolean paid = role == CombatRole.UAV_OPERATOR || role == CombatRole.SSO;
            obj.addProperty("paid", paid);
            roles.add(role.id(), obj);
        }
        root.add("roles", roles);

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Roles: не удалось создать пример конфига доступа {}", file, e);
            return;
        }
        Pjmbasemod.LOGGER.info("Roles: создан конфиг доступа ролей в {}", file);
    }
}
```

- [ ] **Step 2: Вызвать `reload()` на старте сервера**

В `PjmServerEvents.onServerStarted` найди строку `RoleLimitRegistry.get().reload();` и добавь сразу после неё:

```java
        RoleAccessRegistry.get().reload();
```

(Импорт `RoleAccessRegistry` не нужен — тот же пакет `common.role`, но `PjmServerEvents` в другом пакете; добавь `import ru.liko.pjmbasemod.common.role.RoleAccessRegistry;` если его нет.)

- [ ] **Step 3: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/role/RoleAccessRegistry.java src/main/java/ru/liko/pjmbasemod/common/event/PjmServerEvents.java
git commit -m "feat(roles): реестр донатности ролей RoleAccessRegistry (access.json)"
```

---

### Task 2: Permission nodes разблокировки + `canUseRole`

**Files:**
- Modify: `src/main/java/ru/liko/pjmbasemod/common/role/RolePermissions.java`

**Interfaces:**
- Consumes: `RoleAccessRegistry.get().isPaid(CombatRole)` (Task 1)
- Produces:
  - `RolePermissions.canUseRole(ServerPlayer player, CombatRole role)` → `boolean` (роль бесплатная ИЛИ у игрока есть node разблокировки; OP уровня 2+ владеет всем)

- [ ] **Step 1: Добавить карту unlock-нод и `canUseRole`**

Замени всё содержимое класса `RolePermissions` на:

```java
package ru.liko.pjmbasemod.common.role;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.EnumMap;
import java.util.Map;

@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class RolePermissions {

    public static final PermissionNode<Boolean> ADMIN = boolNode("role.admin",
            (player, uuid, ctx) -> player != null && player.hasPermissions(2));

    /** Нода владения каждой ролью: pjmbasemod.role.unlock.<id>. По умолчанию владеет только OP 2+. */
    private static final Map<CombatRole, PermissionNode<Boolean>> UNLOCK_NODES = new EnumMap<>(CombatRole.class);

    static {
        for (CombatRole role : CombatRole.values()) {
            UNLOCK_NODES.put(role, boolNode("role.unlock." + role.id(),
                    (player, uuid, ctx) -> player != null && player.hasPermissions(2)));
        }
    }

    private RolePermissions() {}

    private static PermissionNode<Boolean> boolNode(String path, PermissionNode.PermissionResolver<Boolean> resolver) {
        return new PermissionNode<>(Pjmbasemod.MODID, path, PermissionTypes.BOOLEAN, resolver);
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(ADMIN);
        for (PermissionNode<Boolean> node : UNLOCK_NODES.values()) {
            event.addNodes(node);
        }
    }

    public static boolean can(ServerPlayer player, PermissionNode<Boolean> node) {
        return player != null && Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
    }

    /** true, если роль бесплатная (по RoleAccessRegistry) ИЛИ у игрока есть нода разблокировки. */
    public static boolean canUseRole(ServerPlayer player, CombatRole role) {
        if (role == null) return false;
        if (!RoleAccessRegistry.get().isPaid(role)) return true;
        PermissionNode<Boolean> node = UNLOCK_NODES.get(role);
        return node != null && can(player, node);
    }
}
```

- [ ] **Step 2: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/role/RolePermissions.java
git commit -m "feat(roles): permission nodes разблокировки + canUseRole"
```

---

### Task 3: Проверка владения и гибридное самоназначение в `RoleService`

**Files:**
- Modify: `src/main/java/ru/liko/pjmbasemod/common/role/RoleService.java` (метод `assignRole`)

**Interfaces:**
- Consumes: `RolePermissions.canUseRole(...)` (Task 2), `RoleAccessRegistry.get().isPaid(...)` (Task 1)
- Produces: поведение `assignRole`/`assignRoleById`:
  - donat-роль можно назначить, только если у **цели** есть владение;
  - игрок может назначить **себе** донат-роль, которой владеет, без прав командира.

- [ ] **Step 1: Разрешить самоназначение донат-роли в блоке проверки прав**

В `RoleService.assignRole`, найди блок (примерно строки 76–84):

```java
        if (actor != null && !RolePermissions.can(actor, RolePermissions.ADMIN)) {
            String commanderTeam = FactionCommanderService.activeCommanderTeam(actor);
            if (commanderTeam == null) {
                return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.no_assign_permission"));
            }
            if (!commanderTeam.equals(targetTeam)) {
                return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.target_wrong_faction"));
            }
        }
```

Замени его на:

```java
        boolean selfAssignPaid = actor != null && actor == target && role != null
                && RoleAccessRegistry.get().isPaid(role)
                && RolePermissions.canUseRole(target, role);

        if (actor != null && !selfAssignPaid && !RolePermissions.can(actor, RolePermissions.ADMIN)) {
            String commanderTeam = FactionCommanderService.activeCommanderTeam(actor);
            if (commanderTeam == null) {
                return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.no_assign_permission"));
            }
            if (!commanderTeam.equals(targetTeam)) {
                return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.target_wrong_faction"));
            }
        }
```

- [ ] **Step 2: Добавить проверку владения целью перед проверкой лимита**

В том же методе найди (примерно строки 97–100):

```java
        AssignmentResult capResult = validateRoleCap(target.getServer(), target.getUUID(), targetTeam, role);
        if (!capResult.success()) {
            return capResult;
        }
```

Вставь ПЕРЕД этим блоком:

```java
        if (!RolePermissions.canUseRole(target, role)) {
            return AssignmentResult.failure(Component.translatable("gui.pjmbasemod.role.not_unlocked",
                    Component.translatable(role.translationKey())));
        }

```

(На этой точке `role != null` уже гарантировано — выше есть ранний `if (role == null) { ... return; }`.)

- [ ] **Step 3: Компиляция**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/role/RoleService.java
git commit -m "feat(roles): проверка владения донат-ролью + самоназначение"
```

---

### Task 4: Пакет `RoleAccessSyncPacket` и клиентское зеркало

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/network/packet/RoleAccessSyncPacket.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/network/PjmNetworking.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/network/ClientPacketProxy.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/role/RoleService.java`
- Modify: `src/client/java/ru/liko/pjmbasemod/client/network/ClientPacketHandlersImpl.java`
- Modify: `src/client/java/ru/liko/pjmbasemod/client/role/ClientRoleState.java`

**Interfaces:**
- Consumes: `RolePermissions.canUseRole(...)`, `RoleAccessRegistry.get().isPaid(...)`
- Produces:
  - `RoleAccessSyncPacket(List<String> selfAssignableRoles)`
  - `ClientPacketProxy.roleAccessSync(RoleAccessSyncPacket)`
  - `ClientRoleState.isSelfAssignable(CombatRole)` → `boolean`, `ClientRoleState.hasSelfAssignable()` → `boolean`

- [ ] **Step 1: Создать пакет**

Файл `src/main/java/ru/liko/pjmbasemod/common/network/packet/RoleAccessSyncPacket.java`:

```java
package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.List;

/** S→C: какие донат-роли игрок может назначить себе сам (владеет ими). */
public record RoleAccessSyncPacket(List<String> selfAssignableRoles) implements CustomPacketPayload {

    public static final Type<RoleAccessSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "role_access_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoleAccessSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RoleAccessSyncPacket::selfAssignableRoles,
                    RoleAccessSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 2: Зарегистрировать пакет + бамп VERSION**

В `PjmNetworking`:
1. Измени `public static final String VERSION = "16";` на `"17"`.
2. В секции `// ===== Server → Client =====`, после строки с `RoleSyncPacket`, добавь:

```java
        r.playToClient(RoleAccessSyncPacket.TYPE, RoleAccessSyncPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.roleAccessSync(p)));
```

3. В строке логирования измени число `33` на `34`:

```java
        Pjmbasemod.LOGGER.info("PJM-BaseMod: registered {} network payloads.", 34);
```

(Импорт не нужен — `import ...packet.*;` уже есть.)

- [ ] **Step 3: Добавить метод в прокси**

В `ClientPacketProxy`:
1. Добавь импорт: `import ru.liko.pjmbasemod.common.network.packet.RoleAccessSyncPacket;`
2. После строки `default void roleSync(RoleSyncPacket payload) {}` добавь:

```java
    default void roleAccessSync(RoleAccessSyncPacket payload) {}
```

- [ ] **Step 4: Отправлять пакет из `RoleService.sync`**

В `RoleService`:
1. Добавь импорт: `import ru.liko.pjmbasemod.common.network.packet.RoleAccessSyncPacket;` и `import java.util.ArrayList;`
2. Замени метод `sync`:

```java
    public static void sync(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        PjmNetworking.sendToPlayer(player, new RoleSyncPacket(player.getUUID(),
                currentRoleId(player), canAssignAny(player)));
        PjmNetworking.sendToPlayer(player, new RoleAccessSyncPacket(selfAssignableRoleIds(player)));
    }

    /** id донат-ролей, которыми игрок владеет (может назначить себе сам). */
    private static List<String> selfAssignableRoleIds(ServerPlayer player) {
        List<String> ids = new ArrayList<>();
        for (CombatRole role : CombatRole.values()) {
            if (RoleAccessRegistry.get().isPaid(role) && RolePermissions.canUseRole(player, role)) {
                ids.add(role.id());
            }
        }
        return ids;
    }
```

- [ ] **Step 5: Клиентский стейт**

Замени содержимое `src/client/java/ru/liko/pjmbasemod/client/role/ClientRoleState.java` на:

```java
package ru.liko.pjmbasemod.client.role;

import ru.liko.pjmbasemod.common.network.packet.RoleAccessSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.RoleSyncPacket;
import ru.liko.pjmbasemod.common.role.CombatRole;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public final class ClientRoleState {

    private static String currentRole = "";
    private static boolean canAssignRoles;
    private static Set<String> selfAssignableRoles = new HashSet<>();

    private ClientRoleState() {
    }

    public static void update(RoleSyncPacket packet) {
        currentRole = packet.currentRole() == null ? "" : packet.currentRole();
        canAssignRoles = packet.canAssignRoles();
    }

    public static void updateAccess(RoleAccessSyncPacket packet) {
        selfAssignableRoles = packet.selfAssignableRoles() == null
                ? new HashSet<>()
                : new HashSet<>(packet.selfAssignableRoles());
    }

    public static void reset() {
        currentRole = "";
        canAssignRoles = false;
        selfAssignableRoles = new HashSet<>();
    }

    public static String currentRole() {
        return currentRole;
    }

    @Nullable
    public static CombatRole currentRoleEnum() {
        return CombatRole.byIdOrAlias(currentRole);
    }

    public static boolean canAssignRoles() {
        return canAssignRoles;
    }

    /** Может ли игрок назначить себе эту (донатную) роль — владеет ли он ею. */
    public static boolean isSelfAssignable(CombatRole role) {
        return role != null && selfAssignableRoles.contains(role.id());
    }

    public static boolean hasSelfAssignable() {
        return !selfAssignableRoles.isEmpty();
    }
}
```

- [ ] **Step 6: Реализация обработчика на клиенте**

В `ClientPacketHandlersImpl`:
1. Добавь импорт `import ru.liko.pjmbasemod.common.network.packet.RoleAccessSyncPacket;` (рядом с импортом `RoleSyncPacket`).
2. После метода `roleSync(...)` добавь:

```java
    @Override
    public void roleAccessSync(RoleAccessSyncPacket payload) {
        ClientRoleState.updateAccess(payload);
    }
```

(`ClientRoleState` уже импортирован в этом файле — он используется в `roleSync`.)

- [ ] **Step 7: Компиляция (common + client)**

Run: `./gradlew compileJava` затем `./gradlew compileClientJava`
Expected: оба BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/network/packet/RoleAccessSyncPacket.java src/main/java/ru/liko/pjmbasemod/common/network/PjmNetworking.java src/main/java/ru/liko/pjmbasemod/common/network/ClientPacketProxy.java src/main/java/ru/liko/pjmbasemod/common/role/RoleService.java src/client/java/ru/liko/pjmbasemod/client/role/ClientRoleState.java src/client/java/ru/liko/pjmbasemod/client/network/ClientPacketHandlersImpl.java
git commit -m "feat(roles): синхронизация доступных себе донат-ролей на клиент"
```

---

### Task 5: Радиальное меню — самоназначение донат-ролей

**Files:**
- Modify: `src/client/java/ru/liko/pjmbasemod/client/gui/RadialMenuScreen.java` (метод `openRoleSubmenu`)

**Interfaces:**
- Consumes: `ClientRoleState.isSelfAssignable(CombatRole)`, `ClientRoleState.canAssignRoles()` (Task 4)
- Produces: поведение меню — в режиме командира (как раньше) все роли кликабельны для цели под прицелом; иначе режим самоназначения: кликабельны только донат-роли, которыми игрок владеет, остальные заблокированы (затемнены).

- [ ] **Step 1: Переписать `openRoleSubmenu`**

Замени метод `openRoleSubmenu()` целиком на:

```java
    private void openRoleSubmenu() {
        page = Page.ROLE;
        hoveredIndex = -1;
        lastHoveredIndex = -1;
        hoveredProgress = 0f;
        actions = new ArrayList<>();

        boolean commanderMode = ClientRoleState.canAssignRoles() && roleTargetId != null;
        UUID selfId = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getUUID()
                : null;

        for (CombatRole role : CombatRole.values()) {
            boolean enabled;
            UUID assignTarget;
            if (commanderMode) {
                enabled = true;
                assignTarget = roleTargetId;
            } else {
                enabled = ClientRoleState.isSelfAssignable(role);
                assignTarget = selfId;
            }
            final UUID target = assignTarget;
            actions.add(new RadialAction(
                    Component.translatable(role.translationKey()),
                    0xFF000000 | role.color(),
                    iconFor(role),
                    null,
                    null,
                    role.id(),
                    enabled,
                    p -> {
                        if (target != null) {
                            PjmNetworking.sendToServer(new SelectRolePacket(target, role.id()));
                        }
                    }));
        }

        if (commanderMode) {
            actions.add(new RadialAction(
                    Component.translatable("gui.pjmbasemod.radial.role_clear"),
                    0xFFB05050,
                    new ItemStack(Items.BARRIER),
                    null,
                    null,
                    "",
                    true,
                    p -> PjmNetworking.sendToServer(new SelectRolePacket(roleTargetId, ""))));
        }

        actions.add(new RadialAction(
                Component.translatable("gui.pjmbasemod.radial.back"),
                0xFFAAAAAA,
                new ItemStack(Items.ARROW),
                null,
                null,
                null,
                true,
                (SubmenuAction) p -> buildMainActions()));
    }
```

- [ ] **Step 2: Компиляция (client)**

Run: `./gradlew compileClientJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/client/java/ru/liko/pjmbasemod/client/gui/RadialMenuScreen.java
git commit -m "feat(roles): самоназначение донат-ролей в радиальном меню"
```

---

### Task 6: Локализация сообщения о неразблокированной роли

**Files:**
- Modify: `src/client/resources/assets/pjmbasemod/lang/ru_ru.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/en_us.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/uk_ua.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/de_de.json`
- Modify: `src/client/resources/assets/pjmbasemod/lang/zh_cn.json`

**Interfaces:**
- Consumes: ключ `gui.pjmbasemod.role.not_unlocked` (используется в Task 3, один аргумент `%s` — имя роли)

- [ ] **Step 1: Добавить ключ во все 5 файлов**

В каждом файле найди существующую группу ключей `gui.pjmbasemod.role.*` (например `gui.pjmbasemod.role.required`) и добавь рядом соответствующую строку:

`ru_ru.json`:
```json
  "gui.pjmbasemod.role.not_unlocked": "§cРоль %s ещё не разблокирована (доступна за донат).",
```
`en_us.json`:
```json
  "gui.pjmbasemod.role.not_unlocked": "§cRole %s is not unlocked yet (available via donation).",
```
`uk_ua.json`:
```json
  "gui.pjmbasemod.role.not_unlocked": "§cРоль %s ще не розблокована (доступна за донат).",
```
`de_de.json`:
```json
  "gui.pjmbasemod.role.not_unlocked": "§cRolle %s ist noch nicht freigeschaltet (per Spende verfügbar).",
```
`zh_cn.json`:
```json
  "gui.pjmbasemod.role.not_unlocked": "§c职业 %s 尚未解锁（可通过捐赠获得）。",
```

Следи за запятыми: если ключ добавляется не последним в объекте — ставь запятую; если последним — убери лишнюю.

- [ ] **Step 2: Валидация JSON**

Run: `for f in src/client/resources/assets/pjmbasemod/lang/*.json; do python3 -c "import json,sys; json.load(open('$f', encoding='utf-8'))" && echo "OK $f"; done`
Expected: `OK` для всех пяти файлов (без ошибок парсинга).

- [ ] **Step 3: Финальная компиляция**

Run: `./gradlew compileJava` затем `./gradlew compileClientJava`
Expected: оба BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/client/resources/assets/pjmbasemod/lang/ru_ru.json src/client/resources/assets/pjmbasemod/lang/en_us.json src/client/resources/assets/pjmbasemod/lang/uk_ua.json src/client/resources/assets/pjmbasemod/lang/de_de.json src/client/resources/assets/pjmbasemod/lang/zh_cn.json
git commit -m "i18n(roles): сообщение о неразблокированной донат-роли (5 языков)"
```

---

## Итог и ручная проверка пользователем

После всех задач (выполняется пользователем из пути без `!`):
1. Запустить сервер — проверить, что создан `config/pjmbasemod/roles/access.json` с `uav_operator`/`sso` = `paid: true`.
2. Без LuckPerms-ноды: открыть радиальное меню (не наводясь на игрока) → донат-роли затемнены, выбрать нельзя.
3. Командир пытается выдать донат-роль игроку без ноды → сообщение «роль не разблокирована».
4. Выдать через LuckPerms `pjmbasemod.role.unlock.uav_operator` → роль становится кликабельной для самоназначения; выбор работает; вторая купленная роль (`sso`) тоже доступна, но активна всегда одна.

## Self-Review (выполнено при написании плана)

- **Spec coverage:** ядро владение/активная (Tasks 2–4), `access.json` (Task 1), permission nodes (Task 2), гибридное назначение + лимит как обычно (Task 3, лимит не трогаем — `validateRoleCap` остаётся), синхронизация (Task 4), UI с замками (Task 5), локализация (Task 6). Все разделы спека покрыты.
- **Placeholder scan:** плейсхолдеров нет, весь код приведён.
- **Type consistency:** `canUseRole(ServerPlayer, CombatRole)`, `isPaid(CombatRole)`, `isSelfAssignable(CombatRole)`, `RoleAccessSyncPacket(List<String>)`, `roleAccessSync(...)` — имена согласованы между задачами.
