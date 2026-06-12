package ru.liko.pjmbasemod.common.garage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.entity.NotebookEntity;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.GarageSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenGaragePacket;
import ru.liko.pjmbasemod.common.role.RoleService;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверная логика системы гаража: открытие GUI, сборка (крафт), спавн и возврат техники.
 */
public final class GarageManager {

    private static final int TERMINAL_COMMAND_RADIUS = 12;
    private static final int TERMINAL_STORAGE_FALLBACK_RADIUS = 32;
    private static final int PLAYER_STORE_SEARCH_RADIUS = 48;
    private static final Set<String> SBW_NAMESPACES = Set.of("superbwarfare", "superb_warfare", "sbw");

    /** Конкретный гараж, от которого игрок сейчас работает. */
    private record Session(ServerLevel level, GarageTerminalSettings settings, boolean terminalBacked) {}

    private record StoredTarget(Entity entity, @Nullable VehicleDefinition def, ResourceLocation typeId,
                                @Nullable Session session) {}

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private GarageManager() {}

    // ---------------------------------------------------------------- открытие GUI

    public static void openGarage(ServerPlayer player, NotebookEntity terminal) {
        if (!Config.isGarageEnabled()) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.disabled"));
            return;
        }
        SESSIONS.put(player.getUUID(), sessionFor(terminal));
        PjmNetworking.sendToPlayer(player, new OpenGaragePacket(buildSnapshot(player)));
    }

    public static void openGarageAtPlayer(ServerPlayer player) {
        if (!Config.isGarageEnabled()) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.disabled"));
            return;
        }
        SESSIONS.put(player.getUUID(), fallbackSession(player));
        PjmNetworking.sendToPlayer(player, new OpenGaragePacket(buildSnapshot(player)));
    }

    private static void resync(ServerPlayer player) {
        PjmNetworking.sendToPlayer(player, new GarageSyncPacket(buildSnapshot(player)));
    }

    // ---------------------------------------------------------------- настройка точек

    public static boolean setSpawnPoint(ServerPlayer player) {
        Session session = editableSession(player);
        if (session == null) {
            player.sendSystemMessage(Component.literal("Открой нужный терминал гаража или встань рядом с ним."));
            return false;
        }

        BlockPos pos = player.blockPosition().immutable();
        float yaw = player.getYRot();
        GarageTerminalSettings settings = GarageTerminalSavedData.get(player.server)
                .setSpawn(session.settings().terminalId(), session.level(), session.settings().terminalPos(),
                        session.settings().terminalYaw(), pos, yaw);
        SESSIONS.put(player.getUUID(), new Session(session.level(), settings, true));
        player.sendSystemMessage(Component.literal("Точка спавна гаража сохранена: " + formatPos(pos) + ", yaw " + Math.round(yaw)));
        playGarageSound(player, SoundEvents.LEVER_CLICK, 0.6F, 1.25F);
        return true;
    }

    public static boolean setSpawnPoint(ServerPlayer player, String direction) {
        Float yaw = yawFromDirection(direction);
        if (yaw == null) {
            player.sendSystemMessage(Component.literal("Неизвестное направление: " + direction + ". Используй north, east, south или west."));
            return false;
        }

        Session session = editableSession(player);
        if (session == null) {
            player.sendSystemMessage(Component.literal("Открой нужный терминал гаража или встань рядом с ним."));
            return false;
        }

        BlockPos pos = player.blockPosition().immutable();
        GarageTerminalSettings settings = GarageTerminalSavedData.get(player.server)
                .setSpawn(session.settings().terminalId(), session.level(), session.settings().terminalPos(),
                        session.settings().terminalYaw(), pos, yaw);
        SESSIONS.put(player.getUUID(), new Session(session.level(), settings, true));
        player.sendSystemMessage(Component.literal("Точка спавна гаража сохранена: " + formatPos(pos)
                + ", направление " + normalizedDirection(direction)));
        playGarageSound(player, SoundEvents.LEVER_CLICK, 0.6F, 1.25F);
        return true;
    }

    public static boolean setSpawnFacing(ServerPlayer player) {
        Session session = editableSession(player);
        if (session == null) {
            player.sendSystemMessage(Component.literal("Открой нужный терминал гаража или встань рядом с ним."));
            return false;
        }

        float yaw = player.getYRot();
        GarageTerminalSettings settings = GarageTerminalSavedData.get(player.server)
                .setSpawnYaw(session.settings().terminalId(), session.level(), session.settings().terminalPos(),
                        session.settings().terminalYaw(), yaw);
        SESSIONS.put(player.getUUID(), new Session(session.level(), settings, true));
        player.sendSystemMessage(Component.literal("Направление спавна гаража сохранено: yaw " + Math.round(yaw)));
        playGarageSound(player, SoundEvents.LEVER_CLICK, 0.55F, 1.4F);
        return true;
    }

    public static boolean setSpawnFacing(ServerPlayer player, String direction) {
        Float yaw = yawFromDirection(direction);
        if (yaw == null) {
            player.sendSystemMessage(Component.literal("Неизвестное направление: " + direction + ". Используй north, east, south или west."));
            return false;
        }

        Session session = editableSession(player);
        if (session == null) {
            player.sendSystemMessage(Component.literal("Открой нужный терминал гаража или встань рядом с ним."));
            return false;
        }

        GarageTerminalSettings settings = GarageTerminalSavedData.get(player.server)
                .setSpawnYaw(session.settings().terminalId(), session.level(), session.settings().terminalPos(),
                        session.settings().terminalYaw(), yaw);
        SESSIONS.put(player.getUUID(), new Session(session.level(), settings, true));
        player.sendSystemMessage(Component.literal("Направление спавна гаража сохранено: " + normalizedDirection(direction)));
        playGarageSound(player, SoundEvents.LEVER_CLICK, 0.55F, 1.4F);
        return true;
    }

    public static boolean setStoragePoint(ServerPlayer player) {
        Session session = editableSession(player);
        if (session == null) {
            player.sendSystemMessage(Component.literal("Открой нужный терминал гаража или встань рядом с ним."));
            return false;
        }

        BlockPos pos = player.blockPosition().immutable();
        GarageTerminalSettings settings = GarageTerminalSavedData.get(player.server)
                .setStorage(session.settings().terminalId(), session.level(), session.settings().terminalPos(),
                        session.settings().terminalYaw(), pos);
        SESSIONS.put(player.getUUID(), new Session(session.level(), settings, true));
        player.sendSystemMessage(Component.literal("Точка хранения гаража сохранена: " + formatPos(pos)));
        playGarageSound(player, SoundEvents.LEVER_CLICK, 0.6F, 1.1F);
        return true;
    }

    public static boolean setStorageRadius(ServerPlayer player, int radius) {
        Session session = editableSession(player);
        if (session == null) {
            player.sendSystemMessage(Component.literal("Открой нужный терминал гаража или встань рядом с ним."));
            return false;
        }

        GarageTerminalSettings settings = GarageTerminalSavedData.get(player.server)
                .setStorageRadius(session.settings().terminalId(), session.level(), session.settings().terminalPos(),
                        session.settings().terminalYaw(), radius);
        SESSIONS.put(player.getUUID(), new Session(session.level(), settings, true));
        player.sendSystemMessage(Component.literal("Радиус зоны хранения гаража сохранён: " + settings.storageRadius()));
        playGarageSound(player, SoundEvents.LEVER_CLICK, 0.55F, 1.35F);
        return true;
    }

    public static boolean showPointInfo(ServerPlayer player) {
        Session session = editableSession(player);
        if (session == null) {
            session = SESSIONS.get(player.getUUID());
        }
        if (session == null) {
            player.sendSystemMessage(Component.literal("Открой нужный терминал гаража или встань рядом с ним."));
            return false;
        }

        GarageTerminalSettings settings = session.settings();
        player.sendSystemMessage(Component.literal("Гараж " + shortId(settings.terminalId()) + ":"));
        player.sendSystemMessage(Component.literal(" терминал: " + formatPos(settings.terminalPos()) + " [" + settings.dimension() + "]"));
        player.sendSystemMessage(Component.literal(" спавн: " + formatPos(settings.resolvedSpawnPos()) + ", yaw " + Math.round(settings.spawnYaw())));
        player.sendSystemMessage(Component.literal(" хранение: " + formatPos(settings.resolvedStoragePos()) + ", радиус " + settings.storageRadius()));
        return true;
    }

    // ---------------------------------------------------------------- крафт

    public static void handleCraft(ServerPlayer player, String defId) {
        if (!GaragePermissions.can(player, GaragePermissions.CRAFT)) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.no_permission_craft"));
            return;
        }
        VehicleDefinition def = VehicleRegistry.get().get(defId);
        if (def == null) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.unknown_vehicle", defId));
            return;
        }
        if (!canUseVehicle(player, def)) {
            return;
        }
        if (def.entityTypeId() == null || resolveType(def.entityTypeId()) == null) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.entity_missing", def.entityTypeString()));
            return;
        }
        if (!canAfford(player, def)) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.not_enough_resources"));
            resync(player);
            return;
        }

        consumeCost(player, def);
        CompoundTag nbt = buildDefaultNbt(player.serverLevel(), def.entityTypeId());
        if (nbt == null) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.entity_missing", def.entityTypeString()));
            return;
        }
        StoredVehicle stored = StoredVehicle.create(def.id(), def.displayName(), nbt);
        GarageSavedData.get(player.server).add(player.getUUID(), stored);
        player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.crafted", def.displayName()));
        playGarageSound(player, SoundEvents.SMITHING_TABLE_USE, 0.9F, 0.95F);
        resync(player);
    }

    // ---------------------------------------------------------------- спавн

    public static void handleSpawn(ServerPlayer player, UUID instanceId) {
        if (!GaragePermissions.can(player, GaragePermissions.SPAWN)) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.no_permission_spawn"));
            return;
        }
        GarageSavedData data = GarageSavedData.get(player.server);
        StoredVehicle stored = find(data.garageOf(player.getUUID()), instanceId);
        if (stored == null) {
            resync(player);
            return;
        }
        VehicleDefinition def = VehicleRegistry.get().get(stored.defId());
        ResourceLocation typeId = def != null ? def.entityTypeId() : ResourceLocation.tryParse(stored.defId());
        if (!canUseVehicle(player, def)) {
            return;
        }
        EntityType<?> type = typeId == null ? null : resolveType(typeId);
        if (type == null) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.entity_missing",
                    def == null ? stored.defId() : def.entityTypeString()));
            return;
        }
        String displayName = displayNameForStored(stored, def);

        Session session = SESSIONS.getOrDefault(player.getUUID(), fallbackSession(player));
        ServerLevel level = session.level();
        Entity entity = type.create(level);
        if (entity == null) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.entity_missing", typeId.toString()));
            return;
        }

        CompoundTag tag = stored.entityNbt().copy();
        tag.remove("UUID");
        entity.load(tag);
        entity.setUUID(UUID.randomUUID());

        BlockPos spawn = session.settings().resolvedSpawnPos();
        double x = spawn.getX() + 0.5D;
        double y = spawn.getY();
        double z = spawn.getZ() + 0.5D;
        float yaw = session.settings().spawnYaw();
        entity.moveTo(x, y, z, yaw, 0F);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        level.addFreshEntity(entity);

        data.remove(player.getUUID(), instanceId);
        player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.spawned", displayName));
        playGarageSound(level, entity.position(), SoundEvents.PISTON_EXTEND, 0.9F, 0.85F);
        playGarageSound(level, entity.position(), SoundEvents.DISPENSER_LAUNCH, 0.65F, 0.75F);
        resync(player);
    }

    public static void handleStore(ServerPlayer player) {
        if (!GaragePermissions.can(player, GaragePermissions.STORE)) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.no_permission_store"));
            return;
        }

        Session session = activeStoreSession(player);
        StoredTarget target = session == null ? null : findNearestStorableVehicle(session);
        if (target == null) {
            target = findNearestStorableVehicleNearPlayer(player);
        }
        if (target == null) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.no_vehicle_to_store"));
            return;
        }
        if (!canUseVehicle(player, target.def())) {
            return;
        }

        if (target.session() != null) {
            SESSIONS.put(player.getUUID(), target.session());
        }
        storeResolvedVehicle(player, target.entity(), target.def(), target.typeId());
    }

    public static void handleRecycle(ServerPlayer player, UUID instanceId) {
        if (!GaragePermissions.can(player, GaragePermissions.STORE)) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.no_permission_store"));
            return;
        }

        GarageSavedData data = GarageSavedData.get(player.server);
        StoredVehicle stored = find(data.garageOf(player.getUUID()), instanceId);
        if (stored == null) {
            resync(player);
            return;
        }

        VehicleDefinition def = VehicleRegistry.get().get(stored.defId());
        if (!canUseVehicle(player, def)) {
            return;
        }
        String displayName = displayNameForStored(stored, def);
        int metal = salvageMetalAmount(player.serverLevel(), stored, def);
        StoredVehicle removed = data.remove(player.getUUID(), instanceId);
        if (removed == null) {
            resync(player);
            return;
        }

        giveMetal(player, metal);
        player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.recycled", displayName, metal));
        playGarageSound(player, SoundEvents.GRINDSTONE_USE, 0.75F, 0.85F);
        playGarageSound(player, SoundEvents.ITEM_PICKUP, 0.45F, 1.1F);
        resync(player);
    }

    // ---------------------------------------------------------------- возврат в гараж

    /** Обрабатывает попытку возврата техники в гараж. true — взаимодействие с техникой нужно поглотить. */
    public static boolean storeVehicle(ServerPlayer player, Entity entity) {
        if (!GaragePermissions.can(player, GaragePermissions.STORE)) {
            return false;
        }
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        VehicleDefinition def = findByEntityType(typeId);
        if (def == null && !isSuperbWarfareType(typeId)) {
            return false;
        }
        if (!canUseVehicle(player, def)) {
            return true;
        }
        if (!canStoreAtConfiguredPoint(player, entity)) {
            return true;
        }

        storeResolvedVehicle(player, entity, def, typeId);
        return true;
    }

    private static void storeResolvedVehicle(ServerPlayer player, Entity entity,
                                             @Nullable VehicleDefinition def, ResourceLocation typeId) {
        CompoundTag tag = entity.saveWithoutId(new CompoundTag());
        tag.remove("UUID");
        VehicleDefinition resolvedDef = def;
        String displayName = displayNameForEntity(entity, resolvedDef, typeId);
        if (resolvedDef == null && isSuperbWarfareType(typeId)) {
            resolvedDef = VehicleRegistry.get().ensureDefinitionForEntity(typeId, displayName);
            displayName = resolvedDef.displayName();
        }
        String id = resolvedDef == null ? typeId.toString() : resolvedDef.id();
        StoredVehicle stored = StoredVehicle.create(id, displayName, tag);
        GarageSavedData.get(player.server).add(player.getUUID(), stored);
        playGarageSound(player.serverLevel(), entity.position(), SoundEvents.PISTON_CONTRACT, 0.8F, 0.85F);
        playGarageSound(player.serverLevel(), entity.position(), SoundEvents.IRON_GOLEM_REPAIR, 0.65F, 1.2F);
        entity.discard();
        player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.stored", displayName));
        resync(player);
    }

    /** Выдаёт игроку дефолтный экземпляр техники в гараж (для админ-команды). */
    public static boolean giveDefault(ServerPlayer target, String defId) {
        VehicleDefinition def = VehicleRegistry.get().get(defId);
        if (def == null || def.entityTypeId() == null) return false;
        CompoundTag nbt = buildDefaultNbt(target.serverLevel(), def.entityTypeId());
        if (nbt == null) return false;
        GarageSavedData.get(target.server).add(target.getUUID(), StoredVehicle.create(def.id(), def.displayName(), nbt));
        return true;
    }

    // ---------------------------------------------------------------- снимок для GUI

    public static GarageSnapshot buildSnapshot(ServerPlayer player) {
        List<GarageSnapshot.DefEntry> defs = new ArrayList<>();
        for (VehicleDefinition def : VehicleRegistry.get().all()) {
            List<GarageSnapshot.CostView> costViews = new ArrayList<>();
            boolean affordable = true;
            for (CostEntry cost : def.cost()) {
                Item item = cost.resolveItem();
                int have = item == null ? 0 : countItem(player.getInventory(), item);
                boolean enough = item != null && have >= cost.count();
                if (!enough) affordable = false;
                costViews.add(new GarageSnapshot.CostView(cost.item(), cost.count(), enough));
            }
            String iconItem = def.iconStack().getItem().builtInRegistryHolder().key().location().toString();
            boolean roleAllowed = RoleService.hasAllowedRole(player, def.allowedRoles());
            defs.add(new GarageSnapshot.DefEntry(def.id(), def.displayName(), def.entityTypeString(), iconItem,
                    def.category(), def.assemblyTime(), List.copyOf(costViews), affordable,
                    roleAllowed, def.allowedRoles()));
        }

        List<GarageSnapshot.InstanceEntry> instances = new ArrayList<>();
        for (StoredVehicle stored : GarageSavedData.get(player.server).garageOf(player.getUUID())) {
            VehicleDefinition def = VehicleRegistry.get().get(stored.defId());
            ResourceLocation typeId = def != null ? def.entityTypeId() : ResourceLocation.tryParse(stored.defId());
            String entityType = typeId == null ? "" : typeId.toString();
            boolean roleAllowed = def == null || RoleService.hasAllowedRole(player, def.allowedRoles());
            List<String> allowedRoles = def == null ? List.of() : def.allowedRoles();
            instances.add(new GarageSnapshot.InstanceEntry(stored.instanceId(), stored.defId(),
                    displayNameForStored(stored, def), entityType, stored.entityNbt().copy(),
                    roleAllowed, allowedRoles));
        }

        return new GarageSnapshot(List.copyOf(defs), List.copyOf(instances),
                GaragePermissions.can(player, GaragePermissions.CRAFT),
                GaragePermissions.can(player, GaragePermissions.SPAWN),
                GaragePermissions.can(player, GaragePermissions.STORE));
    }

    // ---------------------------------------------------------------- утилиты

    private static boolean canUseVehicle(ServerPlayer player, @Nullable VehicleDefinition def) {
        if (def == null || RoleService.hasAllowedRole(player, def.allowedRoles())) {
            return true;
        }
        player.sendSystemMessage(RoleService.requiredRoleMessage(def.allowedRoles()));
        return false;
    }

    @Nullable
    private static EntityType<?> resolveType(ResourceLocation id) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        return type.orElse(null);
    }

    @Nullable
    private static CompoundTag buildDefaultNbt(ServerLevel level, ResourceLocation typeId) {
        EntityType<?> type = resolveType(typeId);
        if (type == null) return null;
        Entity temp = type.create(level);
        if (temp == null) return null;
        CompoundTag tag = temp.saveWithoutId(new CompoundTag());
        tag.remove("UUID");
        temp.discard();
        return tag;
    }

    @Nullable
    private static VehicleDefinition findByEntityType(@Nullable ResourceLocation typeId) {
        return VehicleRegistry.get().findByEntityType(typeId);
    }

    @Nullable
    private static StoredVehicle find(List<StoredVehicle> list, UUID instanceId) {
        for (StoredVehicle vehicle : list) {
            if (vehicle.instanceId().equals(instanceId)) return vehicle;
        }
        return null;
    }

    private static Session fallbackSession(ServerPlayer player) {
        return new Session(player.serverLevel(),
                GarageTerminalSettings.temporary(player.serverLevel().dimension(), player.blockPosition(), player.getYRot()),
                false);
    }

    private static Session sessionFor(NotebookEntity terminal) {
        ServerLevel level = (ServerLevel) terminal.level();
        GarageTerminalSettings settings = GarageTerminalSavedData.get(level.getServer())
                .remember(terminal.getUUID(), level, terminal.blockPosition(), terminal.getYRot());
        return new Session(level, settings, true);
    }

    private static boolean canStoreAtConfiguredPoint(ServerPlayer player, Entity entity) {
        Session session = findStorageSession(player, entity);
        if (session == null) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.outside_storage_zone"));
            return false;
        }

        SESSIONS.put(player.getUUID(), session);
        return true;
    }

    @Nullable
    private static Session activeStoreSession(ServerPlayer player) {
        Session current = SESSIONS.get(player.getUUID());
        if (current != null && current.terminalBacked()) {
            return current;
        }
        return editableSession(player);
    }

    @Nullable
    private static StoredTarget findNearestStorableVehicle(Session session) {
        GarageTerminalSettings settings = session.settings();
        Vec3 center = Vec3.atCenterOf(settings.resolvedStoragePos());
        double radius = settings.storageRadius();
        AABB searchBox = AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        StoredTarget best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : session.level().getEntities((Entity) null, searchBox, candidate -> !candidate.isRemoved())) {
            ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            VehicleDefinition def = storableDefinition(typeId);
            if (!canStoreType(typeId, entity, def)) continue;
            double distance = entity.getBoundingBox().distanceToSqr(center);
            if (distance > radius * radius) continue;
            if (distance < bestDistance) {
                best = new StoredTarget(entity, def, typeId, session);
                bestDistance = distance;
            }
        }

        return best;
    }

    @Nullable
    private static StoredTarget findNearestStorableVehicleNearPlayer(ServerPlayer player) {
        AABB searchBox = player.getBoundingBox().inflate(PLAYER_STORE_SEARCH_RADIUS);
        Session playerStorageSession = findStorageSessionAtPlayer(player);
        StoredTarget best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : player.serverLevel().getEntities((Entity) null, searchBox, candidate -> !candidate.isRemoved())) {
            ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            VehicleDefinition def = storableDefinition(typeId);
            if (!canStoreType(typeId, entity, def)) continue;

            Session storageSession = findStorageSession(player, entity);
            if (storageSession == null && playerStorageSession != null && isSuperbWarfareType(typeId)) {
                storageSession = playerStorageSession;
            }
            if (storageSession == null) continue;

            double distance = entity.distanceToSqr(player);
            if (distance < bestDistance) {
                best = new StoredTarget(entity, def, typeId, storageSession);
                bestDistance = distance;
            }
        }

        return best;
    }

    @Nullable
    private static Session findStorageSession(ServerPlayer player, Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return null;
        }

        GarageTerminalSettings storedZone = GarageTerminalSavedData.get(level.getServer()).findStorage(level, entity);
        if (storedZone != null) {
            return new Session(level, storedZone, true);
        }

        NotebookEntity terminal = findNearestTerminal(level, entity, TERMINAL_STORAGE_FALLBACK_RADIUS);
        if (terminal != null) {
            Session terminalSession = sessionFor(terminal);
            if (isInsideStorageZone(entity, terminalSession.settings())) {
                return terminalSession;
            }
        }

        Session current = SESSIONS.get(player.getUUID());
        if (current != null && current.level() == level && isInsideStorageZone(entity, current.settings())) {
            return current;
        }

        return null;
    }

    @Nullable
    private static Session findStorageSessionAtPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 pos = player.position();
        String dimension = level.dimension().location().toString();
        GarageTerminalSettings best = null;
        double bestDistance = Double.MAX_VALUE;

        for (GarageTerminalSettings settings : GarageTerminalSavedData.get(player.server).all()) {
            if (!dimension.equals(settings.dimension())) continue;
            Vec3 center = Vec3.atCenterOf(settings.resolvedStoragePos());
            double distance = center.distanceToSqr(pos);
            double radius = settings.storageRadius();
            if (distance > radius * radius) continue;
            if (distance < bestDistance) {
                best = settings;
                bestDistance = distance;
            }
        }

        if (best != null) {
            return new Session(level, best, true);
        }

        Session current = SESSIONS.get(player.getUUID());
        if (current != null && current.level() == level && isPointInsideStorageZone(pos, current.settings())) {
            return current;
        }

        return null;
    }

    @Nullable
    private static Session editableSession(ServerPlayer player) {
        NotebookEntity terminal = findNearestTerminal(player.serverLevel(), player, TERMINAL_COMMAND_RADIUS);
        if (terminal != null) {
            return sessionFor(terminal);
        }

        Session current = SESSIONS.get(player.getUUID());
        if (current != null && current.terminalBacked() && current.level() == player.serverLevel()) {
            return current;
        }

        return null;
    }

    private static boolean isInsideStorageZone(Entity entity, GarageTerminalSettings settings) {
        Vec3 center = Vec3.atCenterOf(settings.resolvedStoragePos());
        double radius = settings.storageRadius();
        return entity.getBoundingBox().distanceToSqr(center) <= radius * radius;
    }

    private static boolean isPointInsideStorageZone(Vec3 pos, GarageTerminalSettings settings) {
        Vec3 center = Vec3.atCenterOf(settings.resolvedStoragePos());
        double radius = settings.storageRadius();
        return center.distanceToSqr(pos) <= radius * radius;
    }

    @Nullable
    private static NotebookEntity findNearestTerminal(ServerLevel level, Entity entity, int radius) {
        AABB searchBox = entity.getBoundingBox().inflate(radius);
        NotebookEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (NotebookEntity terminal : level.getEntitiesOfClass(NotebookEntity.class, searchBox, terminal -> !terminal.isRemoved())) {
            double distance = terminal.position().distanceToSqr(entity.position());
            if (distance < bestDistance) {
                best = terminal;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void playGarageSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        playGarageSound(player.serverLevel(), player.position(), sound, volume, pitch);
    }

    private static void playGarageSound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String displayNameForStored(StoredVehicle stored, @Nullable VehicleDefinition def) {
        if (def != null && !def.displayName().isBlank()) {
            return def.displayName();
        }

        String customName = stored.customName();
        ResourceLocation typeId = ResourceLocation.tryParse(stored.defId());
        if (isUsefulStoredName(customName, typeId)) {
            return customName;
        }

        return typeId == null ? stored.defId() : displayNameForType(typeId);
    }

    private static String displayNameForEntity(Entity entity, @Nullable VehicleDefinition def, ResourceLocation typeId) {
        if (def != null && !def.displayName().isBlank()) {
            return def.displayName();
        }

        String entityName = entity.getName().getString();
        if (isUsefulStoredName(entityName, typeId)) {
            return entityName;
        }

        return displayNameForType(typeId);
    }

    private static boolean isUsefulStoredName(@Nullable String name, @Nullable ResourceLocation typeId) {
        if (name == null || name.isBlank()) return false;
        if (typeId != null && name.equals(typeId.toString())) return false;
        return !name.startsWith("entity.") && !name.contains(":");
    }

    private static String prettyEntityName(ResourceLocation typeId) {
        String[] parts = typeId.getPath().split("[_\\-]+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(prettyNamePart(part));
        }
        return result.isEmpty() ? typeId.toString() : result.toString();
    }

    private static String displayNameForType(ResourceLocation typeId) {
        String key = "entity." + typeId.getNamespace() + "." + typeId.getPath();
        String translated = Language.getInstance().getOrDefault(key);
        if (!translated.equals(key) && !translated.isBlank()) {
            return translated.trim();
        }
        return prettyEntityName(typeId);
    }

    private static String prettyNamePart(String part) {
        if (part.length() <= 2 && part.chars().allMatch(Character::isLetter)) {
            return part.toUpperCase(Locale.ROOT);
        }
        if (part.chars().allMatch(Character::isDigit)) {
            return part;
        }
        return Character.toUpperCase(part.charAt(0)) + part.substring(1);
    }

    @Nullable
    private static VehicleDefinition storableDefinition(@Nullable ResourceLocation typeId) {
        return findByEntityType(typeId);
    }

    private static boolean canStoreType(@Nullable ResourceLocation typeId, Entity entity, @Nullable VehicleDefinition def) {
        if (def != null) return true;
        if (!isSuperbWarfareType(typeId)) return false;
        if (entity instanceof ServerPlayer || entity instanceof NotebookEntity) return false;
        return true;
    }

    private static boolean isSuperbWarfareType(@Nullable ResourceLocation typeId) {
        return typeId != null && SBW_NAMESPACES.contains(typeId.getNamespace());
    }

    @Nullable
    private static Float yawFromDirection(String direction) {
        return switch (normalizedDirection(direction)) {
            case "south" -> 0F;
            case "west" -> 90F;
            case "north" -> 180F;
            case "east" -> 270F;
            default -> null;
        };
    }

    private static String normalizedDirection(String direction) {
        String value = direction == null ? "" : direction.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "n" -> "north";
            case "e" -> "east";
            case "s", "soulth" -> "south";
            case "w" -> "west";
            default -> value;
        };
    }

    private static boolean canAfford(ServerPlayer player, VehicleDefinition def) {
        Inventory inv = player.getInventory();
        for (CostEntry cost : def.cost()) {
            Item item = cost.resolveItem();
            if (item == null || countItem(inv, item) < cost.count()) return false;
        }
        return true;
    }

    private static void consumeCost(ServerPlayer player, VehicleDefinition def) {
        Inventory inv = player.getInventory();
        for (CostEntry cost : def.cost()) {
            Item item = cost.resolveItem();
            if (item == null) continue;
            removeItems(inv, item, cost.count());
        }
    }

    private static int salvageMetalAmount(ServerLevel level, StoredVehicle stored, @Nullable VehicleDefinition def) {
        int fromCost = 0;
        if (def != null) {
            for (CostEntry cost : def.cost()) {
                fromCost += metalValue(cost);
            }
        }
        if (fromCost > 0) {
            return Mth.clamp(Math.max(4, fromCost / 2), 4, 256);
        }

        ResourceLocation typeId = def != null ? def.entityTypeId() : ResourceLocation.tryParse(stored.defId());
        EntityType<?> type = typeId == null ? null : resolveType(typeId);
        Entity entity = type == null ? null : type.create(level);
        if (entity == null) {
            return 24;
        }

        try {
            CompoundTag tag = stored.entityNbt().copy();
            tag.remove("UUID");
            entity.load(tag);
        } catch (RuntimeException ignored) {
            // Если чужая техника не прочитала NBT, считаем металл по дефолтным размерам EntityType.
        }

        float width = Math.max(1.0F, entity.getBbWidth());
        float height = Math.max(1.0F, entity.getBbHeight());
        entity.discard();
        return Mth.clamp(Math.round(width * width * height * 2.5F), 12, 192);
    }

    private static int metalValue(CostEntry cost) {
        ResourceLocation itemId = ResourceLocation.tryParse(cost.item());
        if (itemId == null) return 0;
        String path = itemId.getPath().toLowerCase(Locale.ROOT);
        int count = cost.count();
        if (path.endsWith("iron_block") || path.endsWith("steel_block")
                || path.equals("block_of_iron") || path.equals("block_of_steel")) {
            return count * 9;
        }
        if (path.endsWith("iron_ingot") || path.endsWith("steel_ingot")
                || path.equals("ingot_iron") || path.equals("ingot_steel")) {
            return count;
        }
        if (path.endsWith("iron_nugget") || path.endsWith("steel_nugget")) {
            return Math.max(1, count / 9);
        }
        if (path.contains("iron") || path.contains("steel")) {
            return count;
        }
        return 0;
    }

    private static void giveMetal(ServerPlayer player, int amount) {
        int remaining = Math.max(1, amount);
        while (remaining > 0) {
            int count = Math.min(64, remaining);
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.IRON_INGOT, count));
            remaining -= count;
        }
    }

    private static int countItem(Inventory inv, Item item) {
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void removeItems(Inventory inv, Item item, int count) {
        int remaining = count;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.is(item)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        if (remaining > 0) {
            Pjmbasemod.LOGGER.warn("Garage: не удалось списать {}x {} у игрока {}", count, item, inv.player.getName().getString());
        }
    }
}
