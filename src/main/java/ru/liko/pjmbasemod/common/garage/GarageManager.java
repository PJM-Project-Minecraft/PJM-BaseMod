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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.entity.NotebookEntity;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.GarageSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.OpenGaragePacket;
import ru.liko.pjmbasemod.common.network.packet.SpawnPointOptionsPacket;
import ru.liko.pjmbasemod.common.network.packet.StoreOptionsPacket;
import ru.liko.pjmbasemod.common.rank.RankService;
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
    /** Тип гаража, открытого игроком сейчас (определяет, какую технику показывать в снимке). */
    private static final Map<UUID, GarageType> OPEN_TYPE = new ConcurrentHashMap<>();

    private GarageManager() {}

    /**
     * Ключ гаража для игрока. Игрок в команде → общий гараж команды ({@code team:<id>}),
     * сокомандники делят один пул. Игрок без команды → личный гараж ({@code player:<uuid>}).
     */
    private static String garageKey(ServerPlayer player) {
        String teamId = FrontlineTeams.resolvePlayerTeamId(player);
        return teamId == null || teamId.isBlank()
                ? GarageSavedData.playerKey(player.getUUID())
                : GarageSavedData.teamKey(teamId);
    }

    // ---------------------------------------------------------------- открытие GUI

    public static void openGarage(ServerPlayer player, NotebookEntity terminal) {
        if (!Config.isGarageEnabled()) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.disabled"));
            return;
        }
        SESSIONS.put(player.getUUID(), sessionFor(terminal));
        OPEN_TYPE.put(player.getUUID(), terminal.getGarageType());
        PjmNetworking.sendToPlayer(player, new OpenGaragePacket(buildSnapshot(player)));
    }

    public static void openGarageAtPlayer(ServerPlayer player) {
        openGarageAtPlayer(player, GarageType.GROUND);
    }

    public static void openGarageAtPlayer(ServerPlayer player, GarageType type) {
        if (!Config.isGarageEnabled()) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.disabled"));
            return;
        }
        SESSIONS.put(player.getUUID(), fallbackSession(player));
        OPEN_TYPE.put(player.getUUID(), type == null ? GarageType.GROUND : type);
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

    public static boolean addSpawnPoint(ServerPlayer player) {
        Session session = editableSession(player);
        if (session == null) {
            player.sendSystemMessage(Component.literal("Открой нужный терминал гаража или встань рядом с ним."));
            return false;
        }
        BlockPos pos = player.blockPosition().immutable();
        float yaw = player.getYRot();
        GarageTerminalSettings settings = GarageTerminalSavedData.get(player.server)
                .addSpawnPoint(session.settings().terminalId(), session.level(), session.settings().terminalPos(),
                        session.settings().terminalYaw(), pos, yaw);
        SESSIONS.put(player.getUUID(), new Session(session.level(), settings, true));
        player.sendSystemMessage(Component.literal("Точка спавна №" + settings.spawnPoints().size()
                + " добавлена: " + formatPos(pos) + ", yaw " + Math.round(yaw)));
        playGarageSound(player, SoundEvents.LEVER_CLICK, 0.6F, 1.25F);
        return true;
    }

    public static boolean addSpawnPoint(ServerPlayer player, String direction) {
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
                .addSpawnPoint(session.settings().terminalId(), session.level(), session.settings().terminalPos(),
                        session.settings().terminalYaw(), pos, yaw);
        SESSIONS.put(player.getUUID(), new Session(session.level(), settings, true));
        player.sendSystemMessage(Component.literal("Точка спавна №" + settings.spawnPoints().size()
                + " добавлена: " + formatPos(pos) + ", направление " + normalizedDirection(direction)));
        playGarageSound(player, SoundEvents.LEVER_CLICK, 0.6F, 1.25F);
        return true;
    }

    public static boolean listSpawnPoints(ServerPlayer player) {
        Session session = editableSession(player);
        if (session == null) {
            session = SESSIONS.get(player.getUUID());
        }
        if (session == null) {
            player.sendSystemMessage(Component.literal("Открой нужный терминал гаража или встань рядом с ним."));
            return false;
        }
        GarageTerminalSettings settings = session.settings();
        List<GarageSpawnPoint> points = settings.spawnPoints();
        if (points.isEmpty()) {
            player.sendSystemMessage(Component.literal("Точек спавна нет (дефолт над терминалом: "
                    + formatPos(settings.resolvedSpawnPos()) + ")."));
            return true;
        }
        player.sendSystemMessage(Component.literal("Точки спавна гаража " + shortId(settings.terminalId()) + ":"));
        ServerLevel level = session.level();
        for (int i = 0; i < points.size(); i++) {
            GarageSpawnPoint point = points.get(i);
            String state = isSpawnPointFree(level, point) ? "свободна" : "занята";
            player.sendSystemMessage(Component.literal(" " + (i + 1) + ") " + formatPos(point.pos())
                    + ", yaw " + Math.round(point.yaw()) + " — " + state));
        }
        return true;
    }

    public static boolean removeSpawnPoint(ServerPlayer player, int number) {
        Session session = editableSession(player);
        if (session == null) {
            player.sendSystemMessage(Component.literal("Открой нужный терминал гаража или встань рядом с ним."));
            return false;
        }
        GarageTerminalSettings current = session.settings();
        int size = current.spawnPoints().size();
        if (number < 1 || number > size) {
            player.sendSystemMessage(Component.literal("Нет точки спавна №" + number + " (всего " + size + ")."));
            return false;
        }
        GarageTerminalSettings settings = GarageTerminalSavedData.get(player.server)
                .removeSpawnPoint(current.terminalId(), session.level(), current.terminalPos(),
                        current.terminalYaw(), number - 1);
        SESSIONS.put(player.getUUID(), new Session(session.level(), settings, true));
        player.sendSystemMessage(Component.literal("Точка спавна №" + number + " удалена (осталось "
                + settings.spawnPoints().size() + ")."));
        playGarageSound(player, SoundEvents.LEVER_CLICK, 0.55F, 1.0F);
        return true;
    }

    public static boolean clearSpawnPoints(ServerPlayer player) {
        Session session = editableSession(player);
        if (session == null) {
            player.sendSystemMessage(Component.literal("Открой нужный терминал гаража или встань рядом с ним."));
            return false;
        }
        GarageTerminalSettings settings = GarageTerminalSavedData.get(player.server)
                .clearSpawnPoints(session.settings().terminalId(), session.level(),
                        session.settings().terminalPos(), session.settings().terminalYaw());
        SESSIONS.put(player.getUUID(), new Session(session.level(), settings, true));
        player.sendSystemMessage(Component.literal("Все точки спавна гаража очищены (дефолт над терминалом)."));
        playGarageSound(player, SoundEvents.LEVER_CLICK, 0.55F, 0.9F);
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
        int pointCount = settings.spawnPoints().size();
        player.sendSystemMessage(Component.literal(" спавн: " + formatPos(settings.resolvedSpawnPos())
                + ", yaw " + Math.round(settings.spawnYaw())
                + (pointCount > 1 ? " (точек: " + pointCount + ", см. /pjm garage spawn list)"
                        : pointCount == 0 ? " (дефолт над терминалом)" : "")));
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
        GarageSavedData.get(player.server).add(garageKey(player), stored);
        player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.crafted", def.displayName()));
        playGarageSound(player, SoundEvents.SMITHING_TABLE_USE, 0.9F, 0.95F);
        resync(player);
    }

    // ---------------------------------------------------------------- спавн

    /** Радиус вокруг точки спавна, в котором наличие техники делает точку занятой. */
    private static final double SPAWN_OCCUPANCY_RADIUS = 2.5D;

    public static void handleSpawn(ServerPlayer player, UUID instanceId) {
        if (!GaragePermissions.can(player, GaragePermissions.SPAWN)) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.no_permission_spawn"));
            return;
        }
        GarageSavedData data = GarageSavedData.get(player.server);
        StoredVehicle stored = find(data.garageOf(garageKey(player)), instanceId);
        if (stored == null) {
            resync(player);
            return;
        }
        VehicleDefinition def = VehicleRegistry.get().get(stored.defId());
        if (!canUseVehicle(player, def)) {
            return;
        }
        ResourceLocation typeId = def != null ? def.entityTypeId() : ResourceLocation.tryParse(stored.defId());
        if (typeId == null || resolveType(typeId) == null) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.entity_missing",
                    def == null ? stored.defId() : def.entityTypeString()));
            return;
        }

        Session session = SESSIONS.getOrDefault(player.getUUID(), fallbackSession(player));
        List<GarageSpawnPoint> points = session.settings().resolvedSpawnPoints();
        if (points.size() <= 1) {
            performSpawn(player, instanceId, points.get(0));
            return;
        }

        // Несколько точек — открыть на клиенте меню выбора с пометкой занятости.
        ServerLevel level = session.level();
        List<SpawnPointOptionsPacket.PointOption> options = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            GarageSpawnPoint point = points.get(i);
            boolean free = isSpawnPointFree(level, point);
            String label = "Точка " + (i + 1) + " (" + formatPos(point.pos()) + ")";
            options.add(new SpawnPointOptionsPacket.PointOption(i, label, free));
        }
        PjmNetworking.sendToPlayer(player, new SpawnPointOptionsPacket(instanceId, List.copyOf(options)));
    }

    /** Выбор точки спавна из меню: спавнит технику на точке по индексу. */
    public static void handleSpawnAtPoint(ServerPlayer player, UUID instanceId, int index) {
        if (!GaragePermissions.can(player, GaragePermissions.SPAWN)) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.no_permission_spawn"));
            return;
        }
        Session session = SESSIONS.getOrDefault(player.getUUID(), fallbackSession(player));
        List<GarageSpawnPoint> points = session.settings().resolvedSpawnPoints();
        if (index < 0 || index >= points.size()) {
            resync(player);
            return;
        }
        performSpawn(player, instanceId, points.get(index));
    }

    /** Непосредственно спавнит технику на точке с проверкой занятости. Не списывает технику, если точка занята. */
    private static void performSpawn(ServerPlayer player, UUID instanceId, GarageSpawnPoint point) {
        GarageSavedData data = GarageSavedData.get(player.server);
        StoredVehicle stored = find(data.garageOf(garageKey(player)), instanceId);
        if (stored == null) {
            resync(player);
            return;
        }
        VehicleDefinition def = VehicleRegistry.get().get(stored.defId());
        if (!canUseVehicle(player, def)) {
            return;
        }
        ResourceLocation typeId = def != null ? def.entityTypeId() : ResourceLocation.tryParse(stored.defId());
        EntityType<?> type = typeId == null ? null : resolveType(typeId);
        if (type == null) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.entity_missing",
                    def == null ? stored.defId() : def.entityTypeString()));
            return;
        }
        String displayName = displayNameForStored(stored, def);

        Session session = SESSIONS.getOrDefault(player.getUUID(), fallbackSession(player));
        ServerLevel level = session.level();

        if (!isSpawnPointFree(level, point)) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.spawn_point_occupied"));
            resync(player);
            return;
        }

        // Проверка лимита флота: до списания техники со склада, чтобы при отказе не снимать экземпляр
        GarageType fleetType = vehicleGarageType(def, typeId);
        if (!ru.liko.pjmbasemod.common.fleet.VehicleFleetManager.canSpawn(player, fleetType)) {
            resync(player);
            return;
        }

        Entity entity = type.create(level);
        if (entity == null) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.entity_missing", typeId.toString()));
            return;
        }

        CompoundTag tag = stored.entityNbt().copy();
        tag.remove("UUID");
        entity.load(tag);
        entity.setUUID(UUID.randomUUID());

        BlockPos spawn = point.pos();
        double x = spawn.getX() + 0.5D;
        double y = spawn.getY();
        double z = spawn.getZ() + 0.5D;
        float yaw = point.yaw();
        entity.moveTo(x, y, z, yaw, 0F);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        level.addFreshEntity(entity);
        // Регистрируем единицу в учёте флота
        ru.liko.pjmbasemod.common.fleet.VehicleFleetManager.register(entity, player, stored.defId(), fleetType);

        data.remove(garageKey(player), instanceId);
        player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.spawned", displayName));
        ru.liko.pjmbasemod.common.logging.PjmActionLogger.instance().logSubsystem(
                ru.liko.pjmbasemod.common.logging.LogCategory.GARAGE,
                player.getGameProfile().getName() + " выгнал из гаража " + displayName
                        + " @ " + spawn.getX() + "," + spawn.getY() + "," + spawn.getZ());
        playGarageSound(level, entity.position(), SoundEvents.PISTON_EXTEND, 0.9F, 0.85F);
        playGarageSound(level, entity.position(), SoundEvents.DISPENSER_LAUNCH, 0.65F, 0.75F);
        resync(player);
    }

    /** true — на точке нет техники/сущностей (кроме игроков, ноутбуков и мелочи вроде предметов). */
    private static boolean isSpawnPointFree(ServerLevel level, GarageSpawnPoint point) {
        Vec3 center = Vec3.atCenterOf(point.pos());
        AABB box = AABB.ofSize(center, SPAWN_OCCUPANCY_RADIUS * 2.0D,
                SPAWN_OCCUPANCY_RADIUS * 2.0D, SPAWN_OCCUPANCY_RADIUS * 2.0D);
        for (Entity entity : level.getEntities((Entity) null, box, c -> !c.isRemoved())) {
            if (entity instanceof ServerPlayer || entity instanceof NotebookEntity) continue;
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity
                    || entity instanceof net.minecraft.world.entity.ExperienceOrb) continue;
            if (entity.getBoundingBox().distanceToSqr(center) > SPAWN_OCCUPANCY_RADIUS * SPAWN_OCCUPANCY_RADIUS) continue;
            return false;
        }
        return true;
    }

    /** Кнопка «Убрать»: собирает подходящую технику и открывает на клиенте меню выбора. */
    public static void handleStore(ServerPlayer player) {
        if (!GaragePermissions.can(player, GaragePermissions.STORE)) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.no_permission_store"));
            return;
        }

        List<StoredTarget> targets = collectStorableTargets(player);
        if (targets.isEmpty()) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.no_vehicle_to_store"));
            return;
        }

        List<StoreOptionsPacket.Option> options = new ArrayList<>();
        for (StoredTarget target : targets) {
            String name = displayNameForEntity(target.entity(), target.def(), target.typeId());
            String entityType = target.typeId() == null ? "" : target.typeId().toString();
            options.add(new StoreOptionsPacket.Option(target.entity().getUUID(), name, entityType));
        }
        PjmNetworking.sendToPlayer(player, new StoreOptionsPacket(List.copyOf(options)));
    }

    /** Выбор конкретной техники из меню: убирает именно её в гараж. */
    public static void handleStoreSelected(ServerPlayer player, UUID entityId) {
        if (!GaragePermissions.can(player, GaragePermissions.STORE)) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.no_permission_store"));
            return;
        }

        StoredTarget chosen = null;
        for (StoredTarget target : collectStorableTargets(player)) {
            if (target.entity().getUUID().equals(entityId)) {
                chosen = target;
                break;
            }
        }
        if (chosen == null) {
            // Техника уехала/исчезла, пока было открыто меню.
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.no_vehicle_to_store"));
            return;
        }
        if (!canUseVehicle(player, chosen.def())) {
            return;
        }
        if (chosen.session() != null) {
            SESSIONS.put(player.getUUID(), chosen.session());
        }
        storeResolvedVehicle(player, chosen.entity(), chosen.def(), chosen.typeId());
    }

    /** Вся техника, доступная для возврата из текущего гаража игрока (отфильтрованная по типу гаража). */
    private static List<StoredTarget> collectStorableTargets(ServerPlayer player) {
        GarageType openType = OPEN_TYPE.getOrDefault(player.getUUID(), GarageType.GROUND);
        java.util.LinkedHashMap<UUID, StoredTarget> byEntity = new java.util.LinkedHashMap<>();

        Session session = activeStoreSession(player);
        if (session != null) {
            GarageTerminalSettings settings = session.settings();
            Vec3 center = Vec3.atCenterOf(settings.resolvedStoragePos());
            double radius = settings.storageRadius();
            AABB box = AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);
            for (Entity entity : session.level().getEntities((Entity) null, box, c -> !c.isRemoved())) {
                ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                VehicleDefinition def = storableDefinition(typeId);
                if (!canStoreType(typeId, entity, def)) continue;
                if (entity.getBoundingBox().distanceToSqr(center) > radius * radius) continue;
                byEntity.putIfAbsent(entity.getUUID(), new StoredTarget(entity, def, typeId, session));
            }
        }

        AABB near = player.getBoundingBox().inflate(PLAYER_STORE_SEARCH_RADIUS);
        Session playerStorageSession = findStorageSessionAtPlayer(player);
        for (Entity entity : player.serverLevel().getEntities((Entity) null, near, c -> !c.isRemoved())) {
            if (byEntity.containsKey(entity.getUUID())) continue;
            ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            VehicleDefinition def = storableDefinition(typeId);
            if (!canStoreType(typeId, entity, def)) continue;
            Session storageSession = findStorageSession(player, entity);
            if (storageSession == null && playerStorageSession != null && isSuperbWarfareType(typeId)) {
                storageSession = playerStorageSession;
            }
            if (storageSession == null) continue;
            byEntity.put(entity.getUUID(), new StoredTarget(entity, def, typeId, storageSession));
        }

        List<StoredTarget> result = new ArrayList<>();
        for (StoredTarget target : byEntity.values()) {
            if (vehicleGarageType(target.def(), target.typeId()) == openType) {
                result.add(target);
            }
        }
        return result;
    }

    public static void handleRecycle(ServerPlayer player, UUID instanceId) {
        if (!GaragePermissions.can(player, GaragePermissions.STORE)) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.no_permission_store"));
            return;
        }

        GarageSavedData data = GarageSavedData.get(player.server);
        StoredVehicle stored = find(data.garageOf(garageKey(player)), instanceId);
        if (stored == null) {
            resync(player);
            return;
        }

        VehicleDefinition def = VehicleRegistry.get().get(stored.defId());
        if (!canUseVehicle(player, def)) {
            return;
        }
        String displayName = displayNameForStored(stored, def);
        StoredVehicle removed = data.remove(garageKey(player), instanceId);
        if (removed == null) {
            resync(player);
            return;
        }

        player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.recycled", displayName));
        playGarageSound(player, SoundEvents.GRINDSTONE_USE, 0.75F, 0.85F);
        resync(player);
    }

    // ---------------------------------------------------------------- возврат в гараж

    /**
     * Обрабатывает попытку возврата техники в гараж. true — взаимодействие с техникой нужно поглотить.
     * {@code garageType} — тип гаража, из которого выполняется возврат (определяется ноутбуком в руке).
     */
    public static boolean storeVehicle(ServerPlayer player, Entity entity, GarageType garageType) {
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
        if (vehicleGarageType(def, typeId) != garageType) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.wrong_garage_type",
                    Component.translatable(vehicleGarageType(def, typeId).translationKey())));
            return true;
        }
        if (!canStoreAtConfiguredPoint(player, entity)) {
            return true;
        }

        storeResolvedVehicle(player, entity, def, typeId);
        return true;
    }

    /** Тип гаража, к которому относится техника: по определению, иначе авто-классификация по entity id. */
    private static GarageType vehicleGarageType(@Nullable VehicleDefinition def, @Nullable ResourceLocation typeId) {
        return def != null ? def.garageType()
                : ru.liko.pjmbasemod.common.compat.SbwVehicleClassifier.classify(typeId);
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
        GarageSavedData.get(player.server).add(garageKey(player), stored);
        playGarageSound(player.serverLevel(), entity.position(), SoundEvents.PISTON_CONTRACT, 0.8F, 0.85F);
        playGarageSound(player.serverLevel(), entity.position(), SoundEvents.IRON_GOLEM_REPAIR, 0.65F, 1.2F);
        // Снимаем запись из учёта флота перед удалением сущности
        ru.liko.pjmbasemod.common.fleet.VehicleFleetManager.unregister(player.server, entity.getUUID());
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
        GarageSavedData.get(target.server).add(garageKey(target), StoredVehicle.create(def.id(), def.displayName(), nbt));
        return true;
    }

    // ---------------------------------------------------------------- снимок для GUI

    public static GarageSnapshot buildSnapshot(ServerPlayer player) {
        GarageType type = OPEN_TYPE.getOrDefault(player.getUUID(), GarageType.GROUND);
        List<GarageSnapshot.DefEntry> defs = new ArrayList<>();
        for (VehicleDefinition def : VehicleRegistry.get().all()) {
            if (def.garageType() != type) continue;
            // Техника, ограниченная по командам, не показывается чужим командам в каталоге.
            if (!teamAllows(player, def)) continue;
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
            boolean rankAllowed = RankService.meetsMinRank(player, def.minRank());
            String requiredRankName = RankService.rankDisplayName(def.minRank());
            defs.add(new GarageSnapshot.DefEntry(def.id(), def.displayName(), def.entityTypeString(), iconItem,
                    def.category(), def.assemblyTime(), List.copyOf(costViews), affordable,
                    roleAllowed, def.allowedRoles(), rankAllowed, requiredRankName));
        }

        List<GarageSnapshot.InstanceEntry> instances = new ArrayList<>();
        for (StoredVehicle stored : GarageSavedData.get(player.server).garageOf(garageKey(player))) {
            VehicleDefinition def = VehicleRegistry.get().get(stored.defId());
            ResourceLocation typeId = def != null ? def.entityTypeId() : ResourceLocation.tryParse(stored.defId());
            // Тип гаража: по определению, иначе авто-классификация по entity id (авиация SBW → авиагараж).
            GarageType instType = def != null
                    ? def.garageType()
                    : ru.liko.pjmbasemod.common.compat.SbwVehicleClassifier.classify(typeId);
            if (instType != type) continue;
            String entityType = typeId == null ? "" : typeId.toString();
            boolean roleAllowed = def == null || RoleService.hasAllowedRole(player, def.allowedRoles());
            List<String> allowedRoles = def == null ? List.of() : def.allowedRoles();
            boolean rankAllowed = def == null || RankService.meetsMinRank(player, def.minRank());
            String requiredRankName = def == null ? "" : RankService.rankDisplayName(def.minRank());
            instances.add(new GarageSnapshot.InstanceEntry(stored.instanceId(), stored.defId(),
                    displayNameForStored(stored, def), entityType, stored.entityNbt().copy(),
                    roleAllowed, allowedRoles, rankAllowed, requiredRankName));
        }

        return new GarageSnapshot(List.copyOf(defs), List.copyOf(instances),
                GaragePermissions.can(player, GaragePermissions.CRAFT),
                GaragePermissions.can(player, GaragePermissions.SPAWN),
                GaragePermissions.can(player, GaragePermissions.STORE));
    }

    // ---------------------------------------------------------------- утилиты

    private static boolean canUseVehicle(ServerPlayer player, @Nullable VehicleDefinition def) {
        if (def == null) {
            return true;
        }
        if (!teamAllows(player, def)) {
            player.sendSystemMessage(Component.translatable("gui.pjmbasemod.garage.team_restricted"));
            return false;
        }
        if (!RoleService.hasAllowedRole(player, def.allowedRoles())) {
            player.sendSystemMessage(RoleService.requiredRoleMessage(def.allowedRoles()));
            return false;
        }
        if (!RankService.meetsMinRank(player, def.minRank())) {
            player.sendSystemMessage(RankService.requiredRankMessage(def.minRank()));
            return false;
        }
        return true;
    }

    /** Доступна ли техника команде игрока (true, если ограничения по командам нет). */
    private static boolean teamAllows(ServerPlayer player, VehicleDefinition def) {
        if (!def.teamRestricted()) return true;
        String team = FrontlineTeams.resolvePlayerTeamId(player);
        return team != null && def.allowedTeams().contains(team);
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
