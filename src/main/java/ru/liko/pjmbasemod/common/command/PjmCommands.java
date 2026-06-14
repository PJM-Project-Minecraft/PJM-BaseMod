package ru.liko.pjmbasemod.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.chat.ChatMode;
import ru.liko.pjmbasemod.common.faction.FactionCommanderSavedData;
import ru.liko.pjmbasemod.common.faction.FactionCommanderService;
import ru.liko.pjmbasemod.common.faction.FactionMenuService;
import ru.liko.pjmbasemod.common.faction.FactionPermissions;
import ru.liko.pjmbasemod.common.frontline.FrontlineChunkKey;
import ru.liko.pjmbasemod.common.frontline.FrontlineManager;
import ru.liko.pjmbasemod.common.frontline.FrontlineSavedData;
import ru.liko.pjmbasemod.common.frontline.FrontlineSectorKey;
import ru.liko.pjmbasemod.common.frontline.FrontlineSectorState;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.frontline.bluemap.FrontlineBlueMapService;
import ru.liko.pjmbasemod.common.garage.GarageManager;
import ru.liko.pjmbasemod.common.garage.VehicleDefinition;
import ru.liko.pjmbasemod.common.garage.VehicleRegistry;
import ru.liko.pjmbasemod.common.network.handler.ServerPacketHandlers;
import ru.liko.pjmbasemod.common.network.packet.ChangeChatModePacket;
import ru.liko.pjmbasemod.common.rank.RankRegistry;
import ru.liko.pjmbasemod.common.rank.RankService;
import ru.liko.pjmbasemod.common.rank.RankSnapshot;
import ru.liko.pjmbasemod.common.region.Region;
import ru.liko.pjmbasemod.common.region.RegionManager;
import ru.liko.pjmbasemod.common.region.RegionSavedData;
import ru.liko.pjmbasemod.common.role.CombatRole;
import ru.liko.pjmbasemod.common.role.RolePermissions;
import ru.liko.pjmbasemod.common.role.RoleSavedData;
import ru.liko.pjmbasemod.common.role.RoleService;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class PjmCommands {

    private static final Map<UUID, SectorSelection> SECTOR_SELECTIONS = new ConcurrentHashMap<>();

    private PjmCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pjm")
                // --- игроковые подсистемы ---
                .then(chatCommand())
                .then(factionCommand())
                .then(roleCommand())
                .then(rankCommand())
                .then(garageCommand())
                .then(WarehouseCommands.build())
                // --- админ / управление миром ---
                .then(regionCommand())
                .then(frontlineCommand())
                .then(inventoryCommand())
                .then(configCommand())
                .then(debugCommand()));
    }

    // ---------------------------------------------------------------- debug (принудительное открытие меню)

    private static LiteralArgumentBuilder<CommandSourceStack> debugCommand() {
        return Commands.literal("debug")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("open")
                        .then(Commands.literal("faction_selection")
                                .executes(ctx -> debugOpenSelection(ctx.getSource(), requirePlayer(ctx.getSource())))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> debugOpenSelection(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target")))))
                        .then(Commands.literal("faction_management")
                                .executes(ctx -> debugOpenManagement(ctx.getSource(), requirePlayer(ctx.getSource())))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> debugOpenManagement(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target"))))));
    }

    private static int debugOpenSelection(CommandSourceStack source, @Nullable ServerPlayer target) {
        if (target == null) return 0;
        FactionMenuService.debugOpenSelection(target);
        source.sendSuccess(() -> Component.literal(
                "Открыт экран выбора фракции у игрока " + target.getName().getString()), false);
        return 1;
    }

    private static int debugOpenManagement(CommandSourceStack source, @Nullable ServerPlayer target) {
        if (target == null) return 0;
        if (!FactionMenuService.debugOpenManagement(target)) {
            source.sendFailure(Component.literal(
                    "У игрока " + target.getName().getString() + " нет боевой команды"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Открыт экран управления фракцией у игрока " + target.getName().getString()), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> inventoryCommand() {
        return Commands.literal("inventory")
                .then(Commands.literal("info")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> inventoryLimitInfo(ctx.getSource())));
    }

    // ---------------------------------------------------------------- config (централизованная перезагрузка)

    /** Секции, которые умеет перезагружать {@code /pjm config reload <section>}. */
    private static final String[] CONFIG_SECTIONS = {"all", "general", "vehicles", "warehouse", "ranks", "roles", "inventory", "skins"};

    private static LiteralArgumentBuilder<CommandSourceStack> configCommand() {
        return Commands.literal("config")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(ctx -> reloadConfig(ctx.getSource(), "all"))
                        .then(Commands.argument("section", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestConfigSections(builder))
                                .executes(ctx -> reloadConfig(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "section")))));
    }

    /**
     * Централизованная перезагрузка JSON-реестров мода. {@code section} = all или конкретная
     * подсистема (vehicles/warehouse/ranks/roles/inventory). Зеркалит логику {@code onServerStarted}.
     */
    private static int reloadConfig(CommandSourceStack source, String section) {
        boolean all = section.equalsIgnoreCase("all");
        var server = source.getServer();
        StringBuilder report = new StringBuilder();
        int sections = 0;

        if (all || section.equalsIgnoreCase("general")) {
            boolean ok = Config.reload();
            ru.liko.pjmbasemod.common.network.handler.ServerPacketHandlers.sendHudConfigAll(server);
            report.append("общий конфиг ").append(ok ? "ок" : "ошибка (дефолты)").append("; ");
            sections++;
        }
        if (all || section.equalsIgnoreCase("vehicles")) {
            ru.liko.pjmbasemod.common.compat.SbwVehicleClassifier.reload(server);
            int count = VehicleRegistry.get().reload();
            report.append("техника ").append(count).append("; ");
            sections++;
        }
        if (all || section.equalsIgnoreCase("warehouse")) {
            int items = ru.liko.pjmbasemod.common.warehouse.WarehouseItemRegistry.get().reload();
            int crates = ru.liko.pjmbasemod.common.warehouse.CrateRegistry.get().reload();
            report.append("склад: предметы ").append(items).append(", ящики ").append(crates).append("; ");
            sections++;
        }
        if (all || section.equalsIgnoreCase("ranks")) {
            RankRegistry.get().reload();
            RankService.syncAll(server);
            report.append("ранги ").append(RankRegistry.get().config().ranks().size()).append("; ");
            sections++;
        }
        if (all || section.equalsIgnoreCase("roles")) {
            int count = ru.liko.pjmbasemod.common.role.RoleLimitRegistry.get().reload();
            RoleService.syncAll(server);
            report.append("лимиты ролей ").append(count).append("; ");
            sections++;
        }
        if (all || section.equalsIgnoreCase("inventory")) {
            ru.liko.pjmbasemod.common.inventory.InventoryLimitRegistry.get().reload();
            ru.liko.pjmbasemod.common.inventory.InventoryLimitService.syncAll(server);
            int locked = ru.liko.pjmbasemod.common.inventory.InventoryLimitRegistry.get().config().lockedSlots().size();
            report.append("инвентарь: заблок. слотов ").append(locked).append("; ");
            sections++;
        }
        if (all || section.equalsIgnoreCase("skins")) {
            ru.liko.pjmbasemod.common.customization.SkinRegistry.get().reload();
            ru.liko.pjmbasemod.common.customization.SkinService.syncAll(server);
            int teams = ru.liko.pjmbasemod.common.customization.SkinRegistry.get().teamCount();
            report.append("скины: пулов ").append(teams).append("; ");
            sections++;
        }

        if (sections == 0) {
            source.sendFailure(Component.literal("Неизвестная секция '" + section
                    + "'. Используй all, general, vehicles, warehouse, ranks, roles или inventory."));
            return 0;
        }

        String summary = report.toString().trim();
        source.sendSuccess(() -> Component.literal("Конфиги перезагружены [" + section + "]: " + summary), true);
        return sections;
    }

    private static CompletableFuture<Suggestions> suggestConfigSections(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (String s : CONFIG_SECTIONS) {
            builder.suggest(s);
        }
        return builder.buildFuture();
    }

    private static int inventoryLimitInfo(CommandSourceStack source) {
        var cfg = ru.liko.pjmbasemod.common.inventory.InventoryLimitRegistry.get().config();
        String slots = cfg.lockedSlots().isEmpty()
                ? "-"
                : cfg.lockedSlots().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable("pjmbasemod.command.inventory.info",
                cfg.enabled() ? "on" : "off", cfg.lockedSlots().size(), slots), false);
        return cfg.lockedSlots().size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> garageCommand() {
        return Commands.literal("garage")
                .then(Commands.literal("open")
                        .executes(ctx -> openGarage(ctx.getSource()))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestGarageTypes(builder))
                                .executes(ctx -> openGarage(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "type")))))
                .then(Commands.literal("info")
                        .executes(ctx -> garagePointInfo(ctx.getSource())))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("storage")
                                .executes(ctx -> setGarageStorage(ctx.getSource())))
                        .then(Commands.literal("radius")
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 128))
                                        .executes(ctx -> setGarageRadius(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "blocks"))))))
                .then(Commands.literal("spawn")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .executes(ctx -> addSpawnPoint(ctx.getSource()))
                                .then(Commands.argument("direction", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestGarageDirections(builder))
                                        .executes(ctx -> addSpawnPoint(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "direction")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listSpawnPoints(ctx.getSource())))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(ctx -> removeSpawnPoint(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "index")))))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearSpawnPoints(ctx.getSource()))))
                .then(Commands.literal("list")
                        .executes(ctx -> listVehicles(ctx.getSource())))
                .then(Commands.literal("add")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("entityType", StringArgumentType.word())
                                        .executes(ctx -> addVehicle(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "entityType"),
                                                null))
                                        .then(Commands.argument("displayName", StringArgumentType.greedyString())
                                                .executes(ctx -> addVehicle(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        StringArgumentType.getString(ctx, "entityType"),
                                                        StringArgumentType.getString(ctx, "displayName")))))))
                .then(Commands.literal("give")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("defId", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestVehicleIds(builder))
                                        .executes(ctx -> giveVehicle(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "defId"))))));
    }

    private static int openGarage(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) return 0;
        GarageManager.openGarageAtPlayer(player);
        return 1;
    }

    private static int openGarage(CommandSourceStack source, String type) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) return 0;
        GarageManager.openGarageAtPlayer(player,
                ru.liko.pjmbasemod.common.garage.GarageType.fromString(type));
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestGarageTypes(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (ru.liko.pjmbasemod.common.garage.GarageType type
                : ru.liko.pjmbasemod.common.garage.GarageType.values()) {
            builder.suggest(type.id());
        }
        return builder.buildFuture();
    }

    private static int setGarageStorage(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        return player != null && GarageManager.setStoragePoint(player) ? 1 : 0;
    }

    private static int setGarageRadius(CommandSourceStack source, int radius) {
        ServerPlayer player = requirePlayer(source);
        return player != null && GarageManager.setStorageRadius(player, radius) ? 1 : 0;
    }

    private static int garagePointInfo(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        return player != null && GarageManager.showPointInfo(player) ? 1 : 0;
    }

    private static int addSpawnPoint(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        return player != null && GarageManager.addSpawnPoint(player) ? 1 : 0;
    }

    private static int addSpawnPoint(CommandSourceStack source, String direction) {
        ServerPlayer player = requirePlayer(source);
        return player != null && GarageManager.addSpawnPoint(player, direction) ? 1 : 0;
    }

    private static int listSpawnPoints(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        return player != null && GarageManager.listSpawnPoints(player) ? 1 : 0;
    }

    private static int removeSpawnPoint(CommandSourceStack source, int index) {
        ServerPlayer player = requirePlayer(source);
        return player != null && GarageManager.removeSpawnPoint(player, index) ? 1 : 0;
    }

    private static int clearSpawnPoints(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        return player != null && GarageManager.clearSpawnPoints(player) ? 1 : 0;
    }

    private static int listVehicles(CommandSourceStack source) {
        var defs = VehicleRegistry.get().all();
        if (defs.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Каталог техники пуст"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Техника в каталоге:"), false);
        for (VehicleDefinition def : defs) {
            source.sendSuccess(() -> Component.literal(" - " + def.id() + " (" + def.displayName() + ") -> " + def.entityTypeString()), false);
        }
        return defs.size();
    }

    private static int addVehicle(CommandSourceStack source, String id, String entityType, String displayName) {
        String cleanId = VehicleRegistry.sanitizeId(id);
        VehicleDefinition def = new VehicleDefinition(cleanId,
                displayName == null || displayName.isBlank() ? cleanId : displayName,
                entityType, "minecraft:iron_block", "", 0, new java.util.ArrayList<>());
        if (def.entityTypeId() == null) {
            source.sendFailure(Component.literal("Некорректный entityType: " + entityType));
            return 0;
        }
        if (!VehicleRegistry.get().addDefinition(def)) {
            source.sendFailure(Component.literal("Не удалось добавить '" + cleanId + "' (возможно, id уже занят)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Техника '" + cleanId + "' добавлена. Отредактируй стоимость в config/pjmbasemod/vehicles/" + cleanId + ".json"), true);
        return 1;
    }

    private static int giveVehicle(CommandSourceStack source, ServerPlayer target, String defId) {
        if (!GarageManager.giveDefault(target, defId)) {
            source.sendFailure(Component.literal("Не удалось выдать технику '" + defId + "' (нет в каталоге или тип не найден)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Выдана техника '" + defId + "' игроку " + target.getName().getString()), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestVehicleIds(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (VehicleDefinition def : VehicleRegistry.get().all()) {
            builder.suggest(def.id());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestGarageDirections(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        builder.suggest("north");
        builder.suggest("east");
        builder.suggest("south");
        builder.suggest("west");
        return builder.buildFuture();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> chatCommand() {
        return Commands.literal("chat")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                            ChatMode m = ChatMode.byId(StringArgumentType.getString(ctx, "mode"));
                            ServerPacketHandlers.handleChangeChatMode(ChangeChatModePacket.setMode(m), sp);
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "gui.pjmbasemod.chat.mode_set", m.getDisplayName()), false);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> factionCommand() {
        return Commands.literal("faction")
                .then(Commands.literal("manage")
                        .requires(PjmCommands::canManageRoles)
                        .executes(ctx -> factionManage(ctx.getSource())))
                .then(Commands.literal("commander")
                        .then(Commands.literal("set")
                                .requires(PjmCommands::canManageFactionCommanders)
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestCombatTeams(builder))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> factionCommanderSet(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "team"),
                                                        EntityArgument.getPlayer(ctx, "player"))))))
                        .then(Commands.literal("clear")
                                .requires(PjmCommands::canManageFactionCommanders)
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestCombatTeams(builder))
                                        .executes(ctx -> factionCommanderClear(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team")))))
                        .then(Commands.literal("info")
                                .executes(ctx -> factionCommanderInfo(ctx.getSource(), null))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestCombatTeams(builder))
                                        .executes(ctx -> factionCommanderInfo(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team")))))
                .then(Commands.literal("list")
                        .executes(ctx -> factionCommanderList(ctx.getSource()))));
    }

    private static int factionManage(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        return player != null && FactionMenuService.openManagement(player) ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> roleCommand() {
        return Commands.literal("role")
                .then(Commands.literal("info")
                        .executes(ctx -> roleInfo(ctx.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(PjmCommands::canManageRoles)
                                .executes(ctx -> roleInfo(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .requires(PjmCommands::canManageRoles)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestRoleIds(builder))
                                        .executes(ctx -> roleSet(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "role"))))))
                .then(Commands.literal("clear")
                        .requires(PjmCommands::canManageRoles)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> roleClear(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("list")
                        .requires(PjmCommands::canManageRoles)
                        .executes(ctx -> roleList(ctx.getSource(), null))
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestCombatTeams(builder))
                                .executes(ctx -> roleList(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "team")))));
    }

    private static int roleInfo(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        return player == null ? 0 : roleInfo(source, player);
    }

    private static int roleInfo(CommandSourceStack source, ServerPlayer target) {
        CombatRole role = RoleService.currentRole(target);
        String team = FrontlineTeams.resolvePlayerTeamId(target);
        String teamLabel = team == null ? "нет фракции" : FrontlineTeams.displayName(source.getServer(), team);
        if (role == null) {
            source.sendSuccess(() -> Component.literal(target.getName().getString()
                    + ": роль не назначена | фракция: " + teamLabel), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(target.getName().getString()
                + ": " + role.displayName()
                + " [" + role.id() + "] | фракция: " + teamLabel), false);
        return 1;
    }

    private static int roleSet(CommandSourceStack source, ServerPlayer target, String roleArg) {
        CombatRole role = parseRole(source, roleArg);
        if (role == null) return 0;
        ServerPlayer actor = source.getEntity() instanceof ServerPlayer player ? player : null;
        RoleService.AssignmentResult result = RoleService.assignRole(actor, target, role, false);
        if (!result.success()) {
            source.sendFailure(result.message());
            return 0;
        }
        source.sendSuccess(result::message, true);
        return 1;
    }

    private static int roleClear(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer actor = source.getEntity() instanceof ServerPlayer player ? player : null;
        RoleService.AssignmentResult result = RoleService.assignRole(actor, target, null, false);
        if (!result.success()) {
            source.sendFailure(result.message());
            return 0;
        }
        source.sendSuccess(result::message, true);
        return 1;
    }

    private static int roleList(CommandSourceStack source, @Nullable String teamArg) {
        String teamFilter = null;
        if (teamArg != null) {
            teamFilter = parseCombatTeam(source, teamArg);
            if (teamFilter == null) return 0;
        }

        RoleSavedData data = RoleSavedData.get(source.getServer());
        if (data.entries().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Назначенных ролей нет."), false);
            return 0;
        }

        final String filter = teamFilter;
        source.sendSuccess(() -> Component.literal(filter == null
                ? "Назначенные роли:"
                : "Назначенные роли фракции " + FrontlineTeams.displayName(source.getServer(), filter) + ":"), false);
        int count = 0;
        for (Map.Entry<UUID, RoleSavedData.RoleEntry> entry : data.entries().entrySet()) {
            RoleSavedData.RoleEntry roleEntry = entry.getValue();
            if (filter != null && !filter.equals(roleEntry.teamId())) continue;
            CombatRole role = CombatRole.byIdOrAlias(roleEntry.roleId());
            String roleName = role == null ? roleEntry.roleId() : role.displayName();
            String teamName = FrontlineTeams.displayName(source.getServer(), roleEntry.teamId());
            ServerPlayer online = source.getServer().getPlayerList().getPlayer(entry.getKey());
            String status = online == null ? "offline" : "online";
            source.sendSuccess(() -> Component.literal(" - " + roleEntry.lastKnownName()
                    + " (" + entry.getKey() + ", " + status + "): "
                    + roleName + " | " + teamName), false);
            count++;
        }
        if (count == 0) {
            source.sendSuccess(() -> Component.literal(" - нет назначений"), false);
        }
        return count;
    }

    private static int factionCommanderSet(CommandSourceStack source, String teamArg, ServerPlayer target) {
        String team = parseCombatTeam(source, teamArg);
        if (team == null) return 0;

        String targetTeam = FrontlineTeams.resolvePlayerTeamId(target);
        if (!team.equals(targetTeam)) {
            source.sendFailure(Component.literal("Игрок " + target.getName().getString()
                    + " не состоит во фракции " + FrontlineTeams.displayName(source.getServer(), team) + "."));
            return 0;
        }

        FactionCommanderService.AssignmentResult result = FactionCommanderService.setCommander(source.getServer(), team, target);
        String teamName = FrontlineTeams.displayName(source.getServer(), team);
        source.sendSuccess(() -> Component.literal(target.getName().getString()
                + " назначен КМД фракции " + teamName + "."), true);
        if (result.previous() != null && !result.previous().playerId().equals(target.getUUID())) {
            source.sendSuccess(() -> Component.literal("Предыдущий КМД снят: "
                    + result.previous().lastKnownName() + "."), false);
        }
        return 1;
    }

    private static int factionCommanderClear(CommandSourceStack source, String teamArg) {
        String team = parseCombatTeam(source, teamArg);
        if (team == null) return 0;

        FactionCommanderSavedData.CommanderEntry removed = FactionCommanderService.clearCommander(source.getServer(), team);
        String teamName = FrontlineTeams.displayName(source.getServer(), team);
        if (removed == null) {
            source.sendFailure(Component.literal("КМД фракции " + teamName + " не назначен."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("КМД фракции " + teamName + " снят: "
                + removed.lastKnownName() + "."), true);
        return 1;
    }

    private static int factionCommanderInfo(CommandSourceStack source, @Nullable String teamArg) {
        FactionCommanderService.validateOnlineAssignments(source.getServer());
        String team;
        if (teamArg == null) {
            if (source.getEntity() instanceof ServerPlayer player) {
                team = FrontlineTeams.resolvePlayerTeamId(player);
                if (team == null) {
                    source.sendFailure(Component.literal("Вы не состоите в настроенной фракции."));
                    return 0;
                }
            } else {
                return factionCommanderList(source);
            }
        } else {
            team = parseCombatTeam(source, teamArg);
            if (team == null) return 0;
        }

        FactionCommanderSavedData.CommanderEntry entry = FactionCommanderSavedData.get(source.getServer()).commander(team);
        String teamName = FrontlineTeams.displayName(source.getServer(), team);
        if (entry == null) {
            source.sendSuccess(() -> Component.literal("КМД фракции " + teamName + ": не назначен."), false);
            return 0;
        }

        ServerPlayer online = source.getServer().getPlayerList().getPlayer(entry.playerId());
        String status = online == null ? "offline" : "online";
        source.sendSuccess(() -> Component.literal("КМД фракции " + teamName + ": "
                + entry.lastKnownName() + " (" + entry.playerId() + ", " + status + ")."), false);
        return 1;
    }

    private static int factionCommanderList(CommandSourceStack source) {
        FactionCommanderService.validateOnlineAssignments(source.getServer());
        FactionCommanderSavedData data = FactionCommanderSavedData.get(source.getServer());
        int assigned = 0;
        source.sendSuccess(() -> Component.literal("КМД фракций:"), false);
        for (var team : FrontlineTeams.all()) {
            FactionCommanderSavedData.CommanderEntry entry = data.commander(team.id());
            String teamName = FrontlineTeams.displayName(source.getServer(), team.id());
            if (entry == null) {
                source.sendSuccess(() -> Component.literal(" - " + teamName + " [" + team.id() + "]: не назначен"), false);
                continue;
            }
            assigned++;
            ServerPlayer online = source.getServer().getPlayerList().getPlayer(entry.playerId());
            String status = online == null ? "offline" : "online";
            source.sendSuccess(() -> Component.literal(" - " + teamName + " [" + team.id() + "]: "
                    + entry.lastKnownName() + " (" + status + ")"), false);
        }
        return assigned;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rankCommand() {
        return Commands.literal("rank")
                .then(Commands.literal("info")
                        .executes(ctx -> rankInfo(ctx.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> rankInfo(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("addxp")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> rankAddXp(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("setxp")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ctx -> rankSetXp(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> rankReset(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int rankInfo(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        return player == null ? 0 : rankInfo(source, player);
    }

    private static int rankInfo(CommandSourceStack source, ServerPlayer target) {
        RankSnapshot snapshot = RankService.snapshot(target);
        String next = snapshot.nextMinXp() < 0
                ? "максимальное звание"
                : snapshot.nextDisplayName() + " через " + Math.max(0, snapshot.nextMinXp() - snapshot.xp()) + " XP";
        source.sendSuccess(() -> Component.literal(target.getName().getString()
                + ": " + snapshot.displayName()
                + " [" + snapshot.shortName() + "]"
                + " | XP " + snapshot.xp()
                + " | следующее: " + next), false);
        return 1;
    }

    private static int rankAddXp(CommandSourceStack source, ServerPlayer target, int amount) {
        RankService.addXp(target, amount, "admin");
        RankSnapshot snapshot = RankService.snapshot(target);
        source.sendSuccess(() -> Component.literal("XP игрока " + target.getName().getString()
                + ": " + snapshot.xp()
                + " (" + snapshot.displayName() + ")"), true);
        return 1;
    }

    private static int rankSetXp(CommandSourceStack source, ServerPlayer target, int amount) {
        RankService.setXp(target, amount, "admin");
        RankSnapshot snapshot = RankService.snapshot(target);
        source.sendSuccess(() -> Component.literal("XP игрока " + target.getName().getString()
                + " установлен: " + snapshot.xp()
                + " (" + snapshot.displayName() + ")"), true);
        return 1;
    }

    private static int rankReset(CommandSourceStack source, ServerPlayer target) {
        RankService.reset(target);
        source.sendSuccess(() -> Component.literal("XP рангов игрока " + target.getName().getString() + " сброшен"), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> frontlineCommand() {
        return Commands.literal("frontline")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("active")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> setActive(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("sync")
                        .executes(ctx -> sync(ctx.getSource())))
                .then(Commands.literal("bluemap")
                        .then(Commands.literal("sync")
                                .executes(ctx -> blueMapSync(ctx.getSource())))
                        .then(Commands.literal("status")
                                .executes(ctx -> blueMapStatus(ctx.getSource()))))
                .then(Commands.literal("journeymap")
                        .then(Commands.literal("sync")
                                .executes(ctx -> journeyMapSync(ctx.getSource())))
                        .then(Commands.literal("status")
                                .executes(ctx -> journeyMapStatus(ctx.getSource()))))
                .then(Commands.literal("fill")
                        .then(Commands.argument("region", StringArgumentType.word())
                                .executes(ctx -> fillRegion(ctx.getSource(), StringArgumentType.getString(ctx, "region"), FrontlineTeams.NEUTRAL_ID))
                                .then(Commands.argument("owner", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestTeams(builder))
                                        .executes(ctx -> fillRegion(ctx.getSource(), StringArgumentType.getString(ctx, "region"), StringArgumentType.getString(ctx, "owner"))))))
                .then(Commands.literal("sector")
                        .then(Commands.literal("info")
                                .executes(ctx -> sectorInfo(ctx.getSource())))
                        .then(Commands.literal("owner")
                                .then(Commands.argument("owner", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestTeams(builder))
                                        .executes(ctx -> setCurrentSectorOwner(ctx.getSource(), StringArgumentType.getString(ctx, "owner")))))
                        .then(Commands.literal("pos1")
                                .executes(ctx -> setSectorSelectionPos(ctx.getSource(), true)))
                        .then(Commands.literal("pos2")
                                .executes(ctx -> setSectorSelectionPos(ctx.getSource(), false)))
                        .then(Commands.literal("assign")
                                .then(Commands.argument("owner", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestTeams(builder))
                                        .executes(ctx -> assignSelectedSectors(ctx.getSource(), StringArgumentType.getString(ctx, "owner"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> regionCommand() {
        return Commands.literal("region")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> createRegion(ctx.getSource(), StringArgumentType.getString(ctx, "name"), null))
                                .then(Commands.argument("displayName", StringArgumentType.greedyString())
                                        .executes(ctx -> createRegion(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "displayName"))))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> deleteRegion(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listRegions(ctx.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> regionInfo(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("displayname")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("displayName", StringArgumentType.greedyString())
                                        .executes(ctx -> setRegionDisplayName(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "displayName"))))))
                .then(Commands.literal("frontline")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> setRegionFrontline(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                BoolArgumentType.getBool(ctx, "enabled"))))))
                .then(Commands.literal("pos1")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> setRegionPos(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true))))
                .then(Commands.literal("pos2")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> setRegionPos(ctx.getSource(), StringArgumentType.getString(ctx, "name"), false))));
    }

    private static int setActive(CommandSourceStack source, boolean active) {
        FrontlineSavedData data = FrontlineSavedData.get(source.getServer());
        data.setManualActive(active);
        FrontlineManager.broadcastMapSync(source.getServer(), data);
        source.sendSuccess(() -> Component.literal("Линия фронта: захват " + (active ? "открыт" : "закрыт")), true);
        return 1;
    }

    private static int sync(CommandSourceStack source) {
        FrontlineSavedData data = FrontlineSavedData.get(source.getServer());
        RegionManager.broadcastMapSync(source.getServer(), RegionSavedData.get(source.getServer()), "command_frontline_sync");
        FrontlineManager.broadcastMapSync(source.getServer(), data);
        source.sendSuccess(() -> Component.literal("Синхронизация регионов и линии фронта отправлена игрокам"), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        FrontlineSavedData data = FrontlineSavedData.get(source.getServer());
        RegionSavedData regions = RegionSavedData.get(source.getServer());
        boolean captureActive = FrontlineManager.isCaptureActive(source.getServer(), data);
        long completeRegions = regions.regions().stream().filter(Region::isComplete).count();
        long frontlineRegions = regions.regions().stream().filter(Region::isFrontline).count();
        long activeSectors = data.sectors().stream().filter(FrontlineSectorState::hasProgress).count();
        int online = source.getServer().getPlayerList().getPlayerCount();

        source.sendSuccess(() -> Component.literal("Frontline: enabled=" + Config.isFrontlineEnabled()
                + ", captureActive=" + captureActive
                + ", manualActive=" + data.isManualActive()
                + ", onlinePlayers=" + online), false);
        source.sendSuccess(() -> Component.literal("Time window: enabled=" + Config.useFrontlineRealTimeWindow()
                + ", " + Config.getFrontlineRealTimeStart()
                + "-" + Config.getFrontlineRealTimeEnd()
                + " " + Config.getFrontlineRealTimeZone()), false);
        source.sendSuccess(() -> Component.literal("Data: regions=" + regions.regions().size()
                + " (" + completeRegions + " complete)"
                + ", frontlineRegions=" + frontlineRegions
                + ", chunks=" + data.chunks().size()
                + ", sectorsWithProgress=" + activeSectors), false);

        FrontlineManager.MapSyncStatus sync = FrontlineManager.mapSyncStatus();
        source.sendSuccess(() -> Component.literal("Map sync: revision=" + sync.revision()
                + ", reason=" + sync.lastReason()
                + ", last=" + formatTimestamp(sync.lastBroadcastAtMs())), false);
        return 1;
    }

    private static int blueMapSync(CommandSourceStack source) {
        if (!Config.isFrontlineBlueMapEnabled()) {
            source.sendFailure(Component.literal("BlueMap-интеграция линии фронта отключена в конфиге."));
            return 0;
        }
        boolean synced = FrontlineBlueMapService.forceSyncNow(source.getServer(), "command_sync");
        if (synced) {
            source.sendSuccess(() -> Component.literal("BlueMap: marker-set линии фронта синхронизирован."), false);
            return 1;
        }
        FrontlineBlueMapService.requestSync("command_sync_queued");
        source.sendSuccess(() -> Component.literal("BlueMap сейчас недоступен. Синхронизация поставлена в очередь."), false);
        return 1;
    }

    private static int journeyMapSync(CommandSourceStack source) {
        FrontlineSavedData data = FrontlineSavedData.get(source.getServer());
        RegionManager.broadcastMapSync(source.getServer(), RegionSavedData.get(source.getServer()), "command_journeymap_sync");
        FrontlineManager.broadcastMapSync(source.getServer(), data, "command_journeymap_sync");
        source.sendSuccess(() -> Component.literal("JourneyMap: синхронизация линии фронта отправлена всем игрокам."), false);
        return 1;
    }

    private static int journeyMapStatus(CommandSourceStack source) {
        FrontlineManager.MapSyncStatus status = FrontlineManager.mapSyncStatus();
        int online = source.getServer().getPlayerList().getPlayerCount();
        source.sendSuccess(() -> Component.literal("JourneyMap frontline: enabled=" + Config.isFrontlineJourneyMapEnabled()
                + ", onlinePlayers=" + online), false);
        source.sendSuccess(() -> Component.literal("Last map broadcast: revision=" + status.revision()
                + ", reason=" + status.lastReason()), false);

        if (status.lastBroadcastAtMs() > 0L) {
            String ts = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                    Instant.ofEpochMilli(status.lastBroadcastAtMs()).atZone(ZoneId.systemDefault()).toLocalDateTime()
            );
            source.sendSuccess(() -> Component.literal("Last map broadcast at: " + ts), false);
        } else {
            source.sendSuccess(() -> Component.literal("Last map broadcast at: none"), false);
        }
        return 1;
    }

    private static int blueMapStatus(CommandSourceStack source) {
        FrontlineBlueMapService.StatusSnapshot status = FrontlineBlueMapService.status();
        source.sendSuccess(() -> Component.literal("BlueMap frontline: enabled=" + status.enabledByConfig()
                + ", api=" + status.apiPresent()
                + (status.blueMapVersion().isBlank() ? "" : ", version=" + status.blueMapVersion())), false);
        source.sendSuccess(() -> Component.literal("Queue: requested=" + status.syncRequested()
                + ", pendingSnapshot=" + status.hasPendingSnapshot()
                + ", debounceTicks=" + status.debounceTicksLeft()
                + ", reason=" + status.lastReason()), false);
        if (status.lastSuccessfulSyncAtMs() > 0L) {
            String ts = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                    Instant.ofEpochMilli(status.lastSuccessfulSyncAtMs()).atZone(ZoneId.systemDefault()).toLocalDateTime()
            );
            source.sendSuccess(() -> Component.literal("Last successful sync: " + ts), false);
        } else {
            source.sendSuccess(() -> Component.literal("Last successful sync: none"), false);
        }

        if (status.dimensionMapping().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Dimension mapping: empty"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Dimension mapping:"), false);
            for (Map.Entry<String, String> entry : status.dimensionMapping().entrySet()) {
                source.sendSuccess(() -> Component.literal(" - " + entry.getKey() + " -> " + entry.getValue()), false);
            }
        }
        return 1;
    }

    private static int createRegion(CommandSourceStack source, String name, @Nullable String displayName) {
        RegionSavedData regions = RegionSavedData.get(source.getServer());
        boolean existed = regions.region(name) != null;
        Region region = regions.getOrCreateRegion(name);
        if (displayName != null && !displayName.isBlank()) {
            region.setDisplayName(displayName);
            regions.setDirty();
        }
        if (!existed || displayName != null) {
            RegionManager.broadcastMapSync(source.getServer(), regions, "region_created");
        }
        source.sendSuccess(() -> Component.literal(existed
                ? "Регион '" + name + "' уже существует"
                : "Регион '" + name + "' создан: " + region.displayName() + ". Для захвата включи /pjm region frontline " + name + " true"), true);
        return existed ? 0 : 1;
    }

    private static int deleteRegion(CommandSourceStack source, String name) {
        RegionSavedData regions = RegionSavedData.get(source.getServer());
        if (!regions.deleteRegion(name)) {
            source.sendFailure(Component.literal("Регион '" + name + "' не найден"));
            return 0;
        }
        broadcastRegionAndFrontline(source, regions, "region_deleted");
        source.sendSuccess(() -> Component.literal("Регион '" + name + "' удален вместе с секторами внутри него"), true);
        return 1;
    }

    private static int listRegions(CommandSourceStack source) {
        RegionSavedData regions = RegionSavedData.get(source.getServer());
        if (regions.regions().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Регионы не созданы"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Регионы:"), false);
        for (Region region : regions.regions()) {
            source.sendSuccess(() -> Component.literal(" - " + describeRegion(region)), false);
        }
        return regions.regions().size();
    }

    private static int regionInfo(CommandSourceStack source, String name) {
        Region region = RegionSavedData.get(source.getServer()).region(name);
        if (region == null) {
            source.sendFailure(Component.literal("Регион '" + name + "' не найден"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(describeRegion(region)), false);
        return 1;
    }

    private static int setRegionDisplayName(CommandSourceStack source, String name, String displayName) {
        RegionSavedData regions = RegionSavedData.get(source.getServer());
        Region region = regions.region(name);
        if (region == null) {
            source.sendFailure(Component.literal("Регион '" + name + "' не найден"));
            return 0;
        }
        region.setDisplayName(displayName);
        regions.setDirty();
        RegionManager.broadcastMapSync(source.getServer(), regions, "region_displayname_changed");
        source.sendSuccess(() -> Component.literal("DisplayName региона '" + region.name() + "' = " + region.displayName()), true);
        return 1;
    }

    private static int setRegionFrontline(CommandSourceStack source, String name, boolean enabled) {
        RegionSavedData regions = RegionSavedData.get(source.getServer());
        Region region = regions.region(name);
        if (region == null) {
            source.sendFailure(Component.literal("Регион '" + name + "' не найден"));
            return 0;
        }
        region.setFrontline(enabled);
        regions.setDirty();
        broadcastRegionAndFrontline(source, regions, enabled ? "region_frontline_enabled" : "region_frontline_disabled");
        source.sendSuccess(() -> Component.literal("Регион '" + region.name() + "': frontline=" + enabled), true);
        return 1;
    }

    private static int setRegionPos(CommandSourceStack source, String name, boolean first) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) return 0;

        RegionSavedData regions = RegionSavedData.get(source.getServer());
        Region region = regions.getOrCreateRegion(name);
        String dimension = dimensionId(player);
        if (!region.dimension().isBlank() && !region.dimension().equals(dimension)) {
            source.sendFailure(Component.literal("Регион уже привязан к измерению " + region.dimension()));
            return 0;
        }

        ChunkPos pos = player.chunkPosition();
        if (first) region.setPos1(dimension, pos);
        else region.setPos2(dimension, pos);
        regions.setDirty();
        broadcastRegionAndFrontline(source, regions, "region_bounds_changed");

        FrontlineSectorKey sector = FrontlineSectorKey.of(region, pos);
        source.sendSuccess(() -> Component.literal((first ? "pos1" : "pos2") + " региона '" + name + "' = " + sectorLabel(sector)), true);
        if (region.isComplete()) {
            source.sendSuccess(() -> Component.literal("Регион готов: " + describeRegion(region)), false);
        }
        return 1;
    }

    private static int fillRegion(CommandSourceStack source, String name, String ownerArg) {
        RegionSavedData regions = RegionSavedData.get(source.getServer());
        FrontlineSavedData data = FrontlineSavedData.get(source.getServer());
        Region region = regions.region(name);
        if (region == null || !region.isComplete()) {
            source.sendFailure(Component.literal("Регион '" + name + "' не найден или не завершен pos1/pos2"));
            return 0;
        }
        if (!region.isFrontline()) {
            source.sendFailure(Component.literal("Регион '" + name + "' обычный. Включи захват: /pjm region frontline " + region.name() + " true"));
            return 0;
        }
        if (region.chunkCount() > Config.getRegionMaxChunks()) {
            source.sendFailure(Component.literal("Регион слишком большой: " + sectorCount(region) + " секторов. Лимит площади в конфиге: " + Config.getRegionMaxChunks()));
            return 0;
        }

        String owner = parseTeam(source, ownerArg);
        if (owner == null) return 0;

        int changed = 0;
        java.util.Set<FrontlineChunkKey> preferred = new java.util.LinkedHashSet<>();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                FrontlineChunkKey key = new FrontlineChunkKey(region.dimension(), x, z);
                String before = data.ownerOf(key);
                data.setOwner(key, owner);
                if (!before.equals(owner)) preferred.add(key);
                changed++;
            }
        }
        data.rebuildGrayZones(region, preferred);
        FrontlineManager.broadcastMapSync(source.getServer(), data);
        int sectorCount = sectorCount(region);
        source.sendSuccess(() -> Component.literal("Регион '" + name + "' заполнен: " + sectorCount + " секторов, владелец " + FrontlineTeams.displayName(source.getServer(), owner)), true);
        return changed;
    }

    private static int sectorInfo(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) return 0;

        FrontlineSavedData data = FrontlineSavedData.get(source.getServer());
        RegionSavedData regions = RegionSavedData.get(source.getServer());
        ChunkPos pos = player.chunkPosition();
        String dimension = dimensionId(player);
        Region region = regions.findFrontlineRegion(dimension, pos);
        FrontlineChunkKey key = FrontlineChunkKey.of(dimension, pos);
        String owner = data.ownerOf(key);
        FrontlineSectorKey sector = region == null
                ? new FrontlineSectorKey(dimension, "", Math.floorDiv(pos.x, FrontlineSectorKey.SIZE_CHUNKS), Math.floorDiv(pos.z, FrontlineSectorKey.SIZE_CHUNKS))
                : FrontlineSectorKey.of(region, pos);
        FrontlineSectorState sectorState = data.sector(sector);
        String playerTeam = FrontlineTeams.resolvePlayerTeamId(player);
        boolean canAttack = region != null && playerTeam != null && FrontlineManager.canAttackSector(data, region, sector, playerTeam);

        source.sendSuccess(() -> Component.literal(sectorLabel(sector)
                + " | регион: " + (region == null ? "нет" : region.displayName())
                + " | владелец: " + FrontlineTeams.displayName(source.getServer(), owner)), false);
        source.sendSuccess(() -> Component.literal("Фракция игрока: "
                + (playerTeam == null ? "нет" : FrontlineTeams.displayName(source.getServer(), playerTeam))
                + " | можно атаковать: " + (canAttack ? "да" : "нет")), false);
        if (sectorState != null && sectorState.hasProgress()) {
            source.sendSuccess(() -> Component.literal("Захват: "
                    + FrontlineTeams.displayName(source.getServer(), sectorState.captureTeamId())
                    + " " + capturePercent(sectorState.progressTicks()) + "%"), false);
        }
        return 1;
    }

    private static int setCurrentSectorOwner(CommandSourceStack source, String ownerArg) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) return 0;

        String owner = parseTeam(source, ownerArg);
        if (owner == null) return 0;

        FrontlineSavedData data = FrontlineSavedData.get(source.getServer());
        RegionSavedData regions = RegionSavedData.get(source.getServer());
        ChunkPos pos = player.chunkPosition();
        String dimension = dimensionId(player);
        Region region = regions.findFrontlineRegion(dimension, pos);
        if (region == null) {
            source.sendFailure(Component.literal("Текущий сектор не входит ни в один регион линии фронта"));
            return 0;
        }
        FrontlineSectorKey sector = FrontlineSectorKey.of(region, pos);
        Set<FrontlineChunkKey> changed = data.setSectorOwnerRaw(region, sector, owner);
        data.rebuildGrayZones(region, changed);
        FrontlineManager.broadcastMapSync(source.getServer(), data);
        source.sendSuccess(() -> Component.literal("Владелец " + sectorLabel(sector) + " = " + FrontlineTeams.displayName(source.getServer(), owner)), true);
        return 1;
    }

    private static int setSectorSelectionPos(CommandSourceStack source, boolean first) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) return 0;

        RegionSavedData regions = RegionSavedData.get(source.getServer());
        ChunkPos pos = player.chunkPosition();
        String dimension = dimensionId(player);
        Region region = regions.findFrontlineRegion(dimension, pos);
        if (region == null || !region.isComplete()) {
            source.sendFailure(Component.literal("Текущий сектор не входит в завершенный регион линии фронта"));
            return 0;
        }

        UUID playerId = player.getUUID();
        SectorSelection current = SECTOR_SELECTIONS.getOrDefault(playerId, SectorSelection.empty());
        SectorSelection.Point point = new SectorSelection.Point(dimension, region.name(), pos.x, pos.z);
        SectorSelection next = first ? current.withPos1(point) : current.withPos2(point);
        SECTOR_SELECTIONS.put(playerId, next);

        FrontlineSectorKey sector = FrontlineSectorKey.of(region, pos);
        source.sendSuccess(() -> Component.literal((first ? "sector pos1" : "sector pos2")
                + " = " + sectorLabel(sector)
                + " | регион " + region.name()), true);
        return 1;
    }

    private static int assignSelectedSectors(CommandSourceStack source, String ownerArg) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) return 0;

        SectorSelection selection = SECTOR_SELECTIONS.get(player.getUUID());
        if (selection == null || selection.pos1() == null || selection.pos2() == null) {
            source.sendFailure(Component.literal("Сначала выставь /pjm frontline sector pos1 и pos2"));
            return 0;
        }
        if (!selection.pos1().dimension().equals(selection.pos2().dimension())) {
            source.sendFailure(Component.literal("sector pos1 и pos2 должны быть в одном измерении"));
            return 0;
        }
        if (!selection.pos1().regionName().equalsIgnoreCase(selection.pos2().regionName())) {
            source.sendFailure(Component.literal("sector pos1 и pos2 должны быть в одном регионе линии фронта"));
            return 0;
        }

        String owner = parseTeam(source, ownerArg);
        if (owner == null) return 0;

        FrontlineSavedData data = FrontlineSavedData.get(source.getServer());
        Region region = RegionSavedData.get(source.getServer()).region(selection.pos1().regionName());
        if (region == null || !region.isFrontline() || !region.isComplete()) {
            source.sendFailure(Component.literal("Регион '" + selection.pos1().regionName() + "' не найден, не завершен или не включен для frontline"));
            return 0;
        }

        int minSectorX = Math.min(Math.floorDiv(selection.pos1().chunkX(), FrontlineSectorKey.SIZE_CHUNKS), Math.floorDiv(selection.pos2().chunkX(), FrontlineSectorKey.SIZE_CHUNKS));
        int maxSectorX = Math.max(Math.floorDiv(selection.pos1().chunkX(), FrontlineSectorKey.SIZE_CHUNKS), Math.floorDiv(selection.pos2().chunkX(), FrontlineSectorKey.SIZE_CHUNKS));
        int minSectorZ = Math.min(Math.floorDiv(selection.pos1().chunkZ(), FrontlineSectorKey.SIZE_CHUNKS), Math.floorDiv(selection.pos2().chunkZ(), FrontlineSectorKey.SIZE_CHUNKS));
        int maxSectorZ = Math.max(Math.floorDiv(selection.pos1().chunkZ(), FrontlineSectorKey.SIZE_CHUNKS), Math.floorDiv(selection.pos2().chunkZ(), FrontlineSectorKey.SIZE_CHUNKS));

        int sectors = 0;
        java.util.Set<FrontlineChunkKey> preferred = new java.util.LinkedHashSet<>();
        for (int sx = minSectorX; sx <= maxSectorX; sx++) {
            for (int sz = minSectorZ; sz <= maxSectorZ; sz++) {
                FrontlineSectorKey sectorKey = new FrontlineSectorKey(region.dimension(), region.name(), sx, sz);
                if (data.sectorChunks(region, sectorKey).isEmpty()) continue;
                sectors++;
                Set<FrontlineChunkKey> changed = data.setSectorOwnerRaw(region, sectorKey, owner);
                preferred.addAll(changed);
            }
        }

        int grayChanged = data.rebuildGrayZones(region, preferred);
        FrontlineManager.broadcastMapSync(source.getServer(), data);

        int sectorCount = sectors;
        source.sendSuccess(() -> Component.literal("Назначено секторов: " + sectorCount
                + ", обновлено серой зоны: " + grayChanged
                + ", владелец: " + FrontlineTeams.displayName(source.getServer(), owner)), true);
        return Math.max(1, sectorCount);
    }

    private static CompletableFuture<Suggestions> suggestTeams(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        builder.suggest("neutral");
        builder.suggest("none");
        for (var team : FrontlineTeams.all()) {
            builder.suggest(team.id());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCombatTeams(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (var team : FrontlineTeams.all()) {
            builder.suggest(team.id());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestRoleIds(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (CombatRole role : CombatRole.values()) {
            builder.suggest(role.id());
        }
        return builder.buildFuture();
    }

    @Nullable
    private static String parseCombatTeam(CommandSourceStack source, String raw) {
        String team = FrontlineTeams.resolveAlias(raw);
        if (team == null || !FrontlineTeams.isCombatTeam(team)) {
            source.sendFailure(Component.literal("Неизвестная боевая фракция '" + raw + "'. Используй id из teams.definitions."));
            return null;
        }
        return team;
    }

    @Nullable
    private static CombatRole parseRole(CommandSourceStack source, String raw) {
        CombatRole role = CombatRole.byIdOrAlias(raw);
        if (role == null) {
            source.sendFailure(Component.literal("Неизвестная роль '" + raw + "'. Используй assault, machine_gunner, sniper, uav_operator, sso, marksman, ew_specialist или crew."));
            return null;
        }
        return role;
    }

    private static boolean canManageFactionCommanders(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return FactionPermissions.can(player, FactionPermissions.COMMANDER_ADMIN);
        }
        return source.hasPermission(2);
    }

    private static boolean canManageRoles(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return RolePermissions.can(player, RolePermissions.ADMIN)
                    || FactionCommanderService.isActiveCommander(player);
        }
        return source.hasPermission(2);
    }

    @Nullable
    private static String parseTeam(CommandSourceStack source, String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("neutral") || raw.equalsIgnoreCase("none")) {
            return FrontlineTeams.NEUTRAL_ID;
        }
        String team = FrontlineTeams.resolveAlias(raw);
        if (team == null) {
            source.sendFailure(Component.literal("Неизвестная фракция '" + raw + "'. Используй id из teams.definitions или neutral."));
            return null;
        }
        return team;
    }

    @Nullable
    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) return player;
        source.sendFailure(Component.literal("Команда доступна только игроку"));
        return null;
    }

    private static void broadcastRegionAndFrontline(CommandSourceStack source, RegionSavedData regions, String reason) {
        RegionManager.broadcastMapSync(source.getServer(), regions, reason);
        FrontlineSavedData frontline = FrontlineSavedData.get(source.getServer());
        frontline.removeStateOutsideRegions(regions);
        FrontlineManager.broadcastMapSync(source.getServer(), frontline, reason);
    }

    private static String describeRegion(Region region) {
        if (!region.isComplete()) {
            return region.name() + " (" + region.displayName() + ") | не завершен | frontline=" + region.isFrontline() + " | dimension: " + (region.dimension().isBlank() ? "не задано" : region.dimension());
        }
        return region.name() + " (" + region.displayName() + ") | " + region.dimension()
                + " | frontline=" + region.isFrontline()
                + " | сектора X " + Math.floorDiv(region.minX(), FrontlineSectorKey.SIZE_CHUNKS) + ".." + Math.floorDiv(region.maxX(), FrontlineSectorKey.SIZE_CHUNKS)
                + ", Z " + Math.floorDiv(region.minZ(), FrontlineSectorKey.SIZE_CHUNKS) + ".." + Math.floorDiv(region.maxZ(), FrontlineSectorKey.SIZE_CHUNKS)
                + " | всего " + sectorCount(region);
    }

    private static String dimensionId(ServerPlayer player) {
        return player.serverLevel().dimension().location().toString();
    }

    private static String sectorLabel(FrontlineSectorKey sector) {
        return "Сектор №" + sector.x() + ", " + sector.z();
    }

    private static int capturePercent(int progressTicks) {
        int requiredTicks = Math.max(1, Config.getFrontlineCaptureTimeSeconds() * 20);
        if (progressTicks <= 0) return 0;
        if (progressTicks >= requiredTicks) return 100;
        return Math.max(0, Math.min(99, (int) (progressTicks * 100L / requiredTicks)));
    }

    private static String formatTimestamp(long epochMs) {
        if (epochMs <= 0L) return "none";
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
        );
    }

    private static int sectorCount(Region region) {
        if (region == null || !region.isComplete()) return 0;
        int minSectorX = Math.floorDiv(region.minX(), FrontlineSectorKey.SIZE_CHUNKS);
        int maxSectorX = Math.floorDiv(region.maxX(), FrontlineSectorKey.SIZE_CHUNKS);
        int minSectorZ = Math.floorDiv(region.minZ(), FrontlineSectorKey.SIZE_CHUNKS);
        int maxSectorZ = Math.floorDiv(region.maxZ(), FrontlineSectorKey.SIZE_CHUNKS);
        return Math.max(0, maxSectorX - minSectorX + 1) * Math.max(0, maxSectorZ - minSectorZ + 1);
    }

    private record SectorSelection(Point pos1, Point pos2) {
        private static SectorSelection empty() {
            return new SectorSelection(null, null);
        }

        private SectorSelection withPos1(Point point) {
            return new SectorSelection(point, pos2);
        }

        private SectorSelection withPos2(Point point) {
            return new SectorSelection(pos1, point);
        }

        private record Point(String dimension, String regionName, int chunkX, int chunkZ) {}
    }
}
