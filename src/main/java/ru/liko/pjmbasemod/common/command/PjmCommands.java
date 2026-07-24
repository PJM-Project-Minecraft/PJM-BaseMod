package ru.liko.pjmbasemod.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.capturepoint.CapturePointManager;
import ru.liko.pjmbasemod.common.capturepoint.CapturePointSavedData;
import ru.liko.pjmbasemod.common.chat.ChatMode;
import ru.liko.pjmbasemod.common.web.WebAuthService;
import ru.liko.pjmbasemod.common.entity.NotebookEntity;
import ru.liko.pjmbasemod.common.entity.QuartermasterEntity;
import ru.liko.pjmbasemod.common.faction.FactionCommanderSavedData;
import ru.liko.pjmbasemod.common.faction.FactionCommanderService;
import ru.liko.pjmbasemod.common.faction.FactionMenuService;
import ru.liko.pjmbasemod.common.faction.FactionPermissions;
import ru.liko.pjmbasemod.common.teams.Teams;
import ru.liko.pjmbasemod.common.report.ReportManager;
import ru.liko.pjmbasemod.common.garage.GarageManager;
import ru.liko.pjmbasemod.common.garage.GarageTerminalSavedData;
import ru.liko.pjmbasemod.common.garage.VehicleDefinition;
import ru.liko.pjmbasemod.common.garage.VehicleRegistry;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.handler.ServerPacketHandlers;
import ru.liko.pjmbasemod.common.network.packet.ChangeChatModePacket;
import ru.liko.pjmbasemod.common.rank.RankRegistry;
import ru.liko.pjmbasemod.common.rank.RankService;
import ru.liko.pjmbasemod.common.rank.RankSnapshot;
import ru.liko.pjmbasemod.common.basezone.BaseZone;
import ru.liko.pjmbasemod.common.basezone.BaseZoneSavedData;
import net.minecraft.core.BlockPos;
import ru.liko.pjmbasemod.common.role.CombatRole;
import ru.liko.pjmbasemod.common.role.RolePermissions;
import ru.liko.pjmbasemod.common.role.RoleSavedData;
import ru.liko.pjmbasemod.common.role.RoleService;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class PjmCommands {

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
                .then(allianceCommand())
                .then(roleCommand())
                .then(rankCommand())
                .then(garageCommand())
                .then(WarehouseCommands.build())
                // --- админ / управление миром ---
                .then(webCommand())
                .then(baseZoneCommand())
                .then(vanishCommand())
                .then(invseeCommand())
                .then(skinCommand())
                .then(eventCommand())
                .then(capturePointCommand())
                .then(campaignCommand())
                .then(inventoryCommand())
                .then(entityCommand())
                .then(ModerationCommands.build())
                .then(reportsCommand())
                .then(WipeCommands.build())
                .then(configCommand())
                .then(debugCommand()));

        // Короткий алиас /vanish — команда используется часто, отдельное дерево дешевле, чем /pjm vanish.
        d.register(vanishCommand());
    }

    // ---------------------------------------------------------------- skin (админ: назначить скин)

    /**
     * {@code /pjm skin set <цель> <скин>} — принудительно назначить скин (любой из известных, минуя
     * пул команды); {@code /pjm skin clear <цель>} — снять назначение (вернётся выбор/дефолт команды).
     */
    private static LiteralArgumentBuilder<CommandSourceStack> skinCommand() {
        return Commands.literal("skin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("target", EntityArgument.players())
                                .then(Commands.argument("skin", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                ru.liko.pjmbasemod.common.customization.SkinRegistry.KNOWN_SKINS, builder))
                                        .executes(ctx -> assignSkin(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "target"),
                                                StringArgumentType.getString(ctx, "skin"))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("target", EntityArgument.players())
                                .executes(ctx -> clearSkin(ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "target")))));
    }

    private static int assignSkin(CommandSourceStack source, Collection<ServerPlayer> targets, String skinId) {
        int count = 0;
        for (ServerPlayer target : targets) {
            if (ru.liko.pjmbasemod.common.customization.SkinService.adminAssign(target, skinId)) count++;
        }
        if (count == 0) {
            source.sendFailure(Component.literal("Неизвестный скин '" + skinId + "'. Доступные: "
                    + String.join(", ", ru.liko.pjmbasemod.common.customization.SkinRegistry.KNOWN_SKINS)));
            return 0;
        }
        int applied = count;
        source.sendSuccess(() -> Component.literal("Скин '" + skinId + "' назначен: " + applied + " игрок(ов)"), true);
        return applied;
    }

    private static int clearSkin(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer target : targets) {
            ru.liko.pjmbasemod.common.customization.SkinService.adminClear(target);
        }
        source.sendSuccess(() -> Component.literal("Назначение скина снято: " + targets.size() + " игрок(ов)"), true);
        return targets.size();
    }

    // ---------------------------------------------------------------- reports (админский GUI жалоб)

    /** {@code /pjm reports} — открыть админский GUI обращений игроков. */
    private static LiteralArgumentBuilder<CommandSourceStack> reportsCommand() {
        return Commands.literal("reports")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player != null) ReportManager.openAdmin(player);
                    return 1;
                });
    }

    // ---------------------------------------------------------------- entity (удаление сущностей мода)

    /** Радиус рейкаста для «/pjm entity remove». */
    private static final double ENTITY_REMOVE_REACH = 6.0D;

    /**
     * Сущности мода (терминал гаража, кладовщик склада) иммунны к ванильному /kill.
     * Снести их можно только этой командой — рейкастом по сущности, на которую смотрит игрок.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> entityCommand() {
        return Commands.literal("entity")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("remove")
                        .executes(ctx -> removeLookedAtEntity(ctx.getSource(), requirePlayer(ctx.getSource()))));
    }

    private static int removeLookedAtEntity(CommandSourceStack source, @Nullable ServerPlayer player) {
        if (player == null) return 0;
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0F);
        Vec3 end = eye.add(view.scale(ENTITY_REMOVE_REACH));
        AABB searchBox = player.getBoundingBox().expandTowards(view.scale(ENTITY_REMOVE_REACH)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player, eye, end, searchBox,
                e -> e instanceof NotebookEntity || e instanceof QuartermasterEntity,
                ENTITY_REMOVE_REACH * ENTITY_REMOVE_REACH);
        if (hit == null) {
            source.sendFailure(Component.literal("Наведись на терминал гаража или кладовщика склада (не дальше "
                    + (int) ENTITY_REMOVE_REACH + " блоков)"));
            return 0;
        }
        Entity target = hit.getEntity();
        final String label;
        if (target instanceof NotebookEntity) {
            label = "терминал гаража";
            // Чистим осиротевшие настройки терминала, привязанные к UUID сущности.
            GarageTerminalSavedData.get(source.getServer()).forget(target.getUUID());
        } else {
            label = "кладовщика склада";
        }
        target.discard();
        source.sendSuccess(() -> Component.literal("Удалён " + label), true);
        return 1;
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
                                                EntityArgument.getPlayer(ctx, "target")))))
                        .then(Commands.literal("garage")
                                .executes(ctx -> debugOpenGarage(ctx.getSource(), requirePlayer(ctx.getSource()), null))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> debugOpenGarage(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target"), null)))
                                .then(Commands.literal("ground")
                                        .executes(ctx -> debugOpenGarage(ctx.getSource(), requirePlayer(ctx.getSource()), "ground"))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(ctx -> debugOpenGarage(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "target"), "ground"))))
                                .then(Commands.literal("aviation")
                                        .executes(ctx -> debugOpenGarage(ctx.getSource(), requirePlayer(ctx.getSource()), "aviation"))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(ctx -> debugOpenGarage(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "target"), "aviation")))))
                        .then(Commands.literal("warehouse")
                                .executes(ctx -> debugOpenWarehouse(ctx.getSource(), requirePlayer(ctx.getSource())))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> debugOpenWarehouse(ctx.getSource(),
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

    private static int debugOpenGarage(CommandSourceStack source, @Nullable ServerPlayer target, @Nullable String type) {
        if (target == null) return 0;
        if (type == null) {
            GarageManager.openGarageAtPlayer(target);
        } else {
            GarageManager.openGarageAtPlayer(target,
                    ru.liko.pjmbasemod.common.garage.GarageType.fromString(type));
        }
        String typeSuffix = type == null ? "" : " (" + type + ")";
        source.sendSuccess(() -> Component.literal(
                "Открыт гараж" + typeSuffix + " у игрока " + target.getName().getString()), false);
        return 1;
    }

    private static int debugOpenWarehouse(CommandSourceStack source, @Nullable ServerPlayer target) {
        if (target == null) return 0;
        ru.liko.pjmbasemod.common.warehouse.WarehouseManager.openWarehouseDebug(target);
        source.sendSuccess(() -> Component.literal(
                "Открыт склад (debug) у игрока " + target.getName().getString()), false);
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
    private static final String[] CONFIG_SECTIONS = {"all", "general", "vehicles", "warehouse", "ranks", "roles", "inventory", "skins", "events", "missiles"};

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
            ru.liko.pjmbasemod.common.inventory.EquipmentRoleIndex.get().rebuild();
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
        if (all || section.equalsIgnoreCase("events")) {
            int count = ru.liko.pjmbasemod.common.serverevent.DroneRaidRegistry.get().reload();
            int zones = ru.liko.pjmbasemod.common.serverevent.SignalHuntRegistry.get().reload();
            report.append("события: точек налёта ").append(count)
                    .append(", зон радиоразведки ").append(zones).append("; ");
            sections++;
        }
        if (all || section.equalsIgnoreCase("missiles")) {
            int count = ru.liko.pjmbasemod.common.missile.MissileRegistry.get().reload();
            report.append("ракеты: профилей ").append(count).append("; ");
            sections++;
        }

        if (sections == 0) {
            source.sendFailure(Component.literal("Неизвестная секция '" + section
                    + "'. Используй all, general, vehicles, warehouse, ranks, roles, inventory, skins, events или missiles."));
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

    // ---------------------------------------------------------------- invsee (просмотр инвентаря)

    /** {@code /pjm invsee <цель>} — открыть админу живой инвентарь игрока ванильным меню сундука. */
    private static LiteralArgumentBuilder<CommandSourceStack> invseeCommand() {
        return Commands.literal("invsee")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> openInvsee(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))));
    }

    private static int openInvsee(CommandSourceStack source, ServerPlayer target)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer admin = source.getPlayerOrException();
        Component title = Component.literal(target.getGameProfile().getName() + " — инвентарь");
        admin.openMenu(new SimpleMenuProvider(
                (id, playerInv, p) -> new ChestMenu(MenuType.GENERIC_9x5, id, playerInv,
                        new ru.liko.pjmbasemod.common.moderation.InventoryPeekContainer(target), 5),
                title));
        return 1;
    }

    // ---------------------------------------------------------------- vanish (невидимость админа)

    /** {@code /pjm vanish [цель]} — переключить ваниш: игрок пропадает из TAB и из мира для остальных. */
    private static LiteralArgumentBuilder<CommandSourceStack> vanishCommand() {
        return Commands.literal("vanish")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> toggleVanish(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> toggleVanish(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))));
    }

    private static int toggleVanish(CommandSourceStack source, ServerPlayer target) {
        boolean vanished = ru.liko.pjmbasemod.common.vanish.VanishService.toggle(target);
        Component state = Component.literal(vanished ? "включён" : "выключен")
                .withStyle(vanished ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        target.sendSystemMessage(Component.literal("Ваниш ").append(state).append(Component.literal(".")));
        if (source.getEntity() != target) {
            source.sendSuccess(() -> Component.literal("Ваниш для " + target.getGameProfile().getName() + " ")
                    .append(state).append(Component.literal(".")), true);
        }
        return 1;
    }

    // ---------------------------------------------------------------- event (серверные события)

    private static LiteralArgumentBuilder<CommandSourceStack> eventCommand() {
        return Commands.literal("event")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(ctx -> startServerEvent(ctx.getSource(), null, null))
                        // Опциональный тип: drone_raid / signal_hunt.
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestEventTypes(ctx.getSource(), builder))
                                .executes(ctx -> startServerEvent(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "type"), null))
                                // greedyString: имена точек/зон могут содержать пробелы и кириллицу.
                                .then(Commands.argument("point", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> suggestEventPoints(builder))
                                        .executes(ctx -> startServerEvent(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "type"),
                                                StringArgumentType.getString(ctx, "point"))))))
                .then(Commands.literal("stop")
                        .executes(ctx -> stopServerEvent(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> serverEventStatus(ctx.getSource())))
                .then(Commands.literal("reload")
                        .executes(ctx -> reloadConfig(ctx.getSource(), "events")));
    }

    private static int startServerEvent(CommandSourceStack source, @Nullable String typeId, @Nullable String pointName) {
        String error = ru.liko.pjmbasemod.common.serverevent.ServerEventManager
                .startEvent(source.getServer(), typeId, pointName);
        if (error != null) {
            source.sendFailure(Component.literal("Событие не запущено: " + error));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Событие запущено — "
                + ru.liko.pjmbasemod.common.serverevent.ServerEventManager.status()), true);
        return 1;
    }

    private static int stopServerEvent(CommandSourceStack source) {
        if (!ru.liko.pjmbasemod.common.serverevent.ServerEventManager.stopEvent(source.getServer())) {
            source.sendFailure(Component.literal("Активного события нет."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Событие остановлено."), true);
        return 1;
    }

    private static int serverEventStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("События: "
                + ru.liko.pjmbasemod.common.serverevent.ServerEventManager.status()), false);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestEventTypes(
            CommandSourceStack source, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (var type : ru.liko.pjmbasemod.common.serverevent.ServerEventManager.availableTypes(source.getServer())) {
            builder.suggest(type.typeId());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestEventPoints(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (var point : ru.liko.pjmbasemod.common.serverevent.DroneRaidRegistry.get().points()) {
            builder.suggest(point.name);
        }
        for (var zone : ru.liko.pjmbasemod.common.serverevent.SignalHuntRegistry.get().zones()) {
            builder.suggest(zone.name);
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
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> openGarage(ctx.getSource()))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestGarageTypes(builder))
                                .executes(ctx -> openGarage(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "type")))))
                .then(Commands.literal("info")
                        .requires(source -> source.hasPermission(2))
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

    // ---------------------------------------------------------------- alliance (союзы фракций)

    /**
     * {@code /pjm alliance offer <фракция> [часы]} — предложить союз (0 часов = навсегда),
     * {@code accept|decline} — ответ на предложение, {@code break} — разрыв, {@code list} — состояние.
     * Распоряжается союзом командир фракции; OP действует от имени своей команды.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> allianceCommand() {
        return Commands.literal("alliance")
                .then(Commands.literal("offer")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestCombatTeams(builder))
                                .executes(ctx -> allianceOffer(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "team"), 0))
                                .then(Commands.argument("hours", IntegerArgumentType.integer(0, 720))
                                        .executes(ctx -> allianceOffer(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team"),
                                                IntegerArgumentType.getInteger(ctx, "hours"))))))
                .then(Commands.literal("accept")
                        .executes(ctx -> allianceRespond(ctx.getSource(), true)))
                .then(Commands.literal("decline")
                        .executes(ctx -> allianceRespond(ctx.getSource(), false)))
                .then(Commands.literal("break")
                        .executes(ctx -> allianceBreak(ctx.getSource())))
                .then(Commands.literal("list")
                        .executes(ctx -> allianceList(ctx.getSource())));
    }

    /** Фракция, от чьего имени игрок распоряжается союзом, либо {@code null} с сообщением об отказе. */
    @Nullable
    private static String requireAllianceAuthority(CommandSourceStack source, ServerPlayer actor) {
        String team = ru.liko.pjmbasemod.common.alliance.Alliances.authorityTeam(actor);
        if (team == null) {
            source.sendFailure(Component.literal("Союзами распоряжается только командир фракции."));
        }
        return team;
    }

    private static int allianceOffer(CommandSourceStack source, String targetTeam, int hours) {
        ServerPlayer actor = requirePlayer(source);
        if (actor == null) return 0;
        String team = requireAllianceAuthority(source, actor);
        if (team == null) return 0;
        return report(source, ru.liko.pjmbasemod.common.alliance.Alliances.offer(
                source.getServer(), team, targetTeam, hours * 60L));
    }

    private static int allianceRespond(CommandSourceStack source, boolean accept) {
        ServerPlayer actor = requirePlayer(source);
        if (actor == null) return 0;
        String team = requireAllianceAuthority(source, actor);
        if (team == null) return 0;
        return report(source, accept
                ? ru.liko.pjmbasemod.common.alliance.Alliances.accept(source.getServer(), team, null)
                : ru.liko.pjmbasemod.common.alliance.Alliances.decline(source.getServer(), team));
    }

    private static int allianceBreak(CommandSourceStack source) {
        ServerPlayer actor = requirePlayer(source);
        if (actor == null) return 0;
        String team = requireAllianceAuthority(source, actor);
        if (team == null) return 0;
        return report(source, ru.liko.pjmbasemod.common.alliance.Alliances.dissolve(source.getServer(), team));
    }

    private static int allianceList(CommandSourceStack source) {
        var data = ru.liko.pjmbasemod.common.alliance.AllianceSavedData.get(source.getServer());
        var alliances = data.alliances();
        if (alliances.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Действующих союзов нет."), false);
            return 1;
        }
        for (var alliance : alliances) {
            long minutesLeft = alliance.expiresAt() == 0L ? 0L
                    : Math.max(1L, (alliance.expiresAt() - System.currentTimeMillis()) / 60_000L);
            String suffix = alliance.expiresAt() == 0L ? "навсегда" : "осталось " + minutesLeft + " мин";
            source.sendSuccess(() -> Component.literal("· "
                    + Teams.displayName(source.getServer(), alliance.teamA()) + " + "
                    + Teams.displayName(source.getServer(), alliance.teamB()) + " (" + suffix + ")"), false);
        }
        return 1;
    }

    private static int report(CommandSourceStack source, ru.liko.pjmbasemod.common.alliance.Alliances.Result result) {
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(result.message()), false);
            return 1;
        }
        source.sendFailure(Component.literal(result.message()));
        return 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> factionCommand() {
        return Commands.literal("faction")
                .then(Commands.literal("manage")
                        .requires(PjmCommands::canOpenFactionManagement)
                        .executes(ctx -> factionManage(ctx.getSource())))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        ctx.getSource().getOnlinePlayerNames(), builder))
                                .executes(ctx -> factionInvite(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"), null, true))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .requires(src -> src.hasPermission(2))
                                        .suggests((ctx, builder) -> suggestCombatTeams(builder))
                                        .executes(ctx -> factionInvite(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "team"), true)))))
                .then(Commands.literal("uninvite")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> factionInvite(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"), null, false))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .requires(src -> src.hasPermission(2))
                                        .suggests((ctx, builder) -> suggestCombatTeams(builder))
                                        .executes(ctx -> factionInvite(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "team"), false)))))
                .then(Commands.literal("invites")
                        .executes(ctx -> factionInvites(ctx.getSource(), null))
                        .then(Commands.argument("team", StringArgumentType.word())
                                .requires(src -> src.hasPermission(2))
                                .suggests((ctx, builder) -> suggestCombatTeams(builder))
                                .executes(ctx -> factionInvites(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "team")))))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        ctx.getSource().getOnlinePlayerNames(), builder))
                                .executes(ctx -> factionKick(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"), null))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .requires(src -> src.hasPermission(2))
                                        .suggests((ctx, builder) -> suggestCombatTeams(builder))
                                        .executes(ctx -> factionKick(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "team"))))))
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

    /**
     * Резолв фракции для команд приглашений: явный аргумент (только OP) или фракция игрока,
     * если у него есть право приглашать (командир / зам с правом INVITE / админ).
     */
    @Nullable
    private static String resolveInviteTeam(CommandSourceStack source, ServerPlayer actor, @Nullable String teamArg) {
        if (teamArg != null) {
            String team = Teams.resolveAlias(teamArg);
            if (team == null || !Teams.isCombatTeam(team)) {
                source.sendFailure(Component.translatable("gui.pjmbasemod.faction.selection.invalid_team"));
                return null;
            }
            return team;
        }
        FactionMenuService.Authority authority = FactionMenuService.authority(actor);
        if (!authority.valid() || !authority.canInvite()) {
            source.sendFailure(Component.translatable("gui.pjmbasemod.faction.manage.no_access"));
            return null;
        }
        return authority.teamId();
    }

    /**
     * Резолв фракции для кика: явный аргумент (только OP) или своя фракция, если у игрока есть
     * право кикать (командир / зам с правом KICK / админ).
     */
    @Nullable
    private static String resolveKickTeam(CommandSourceStack source, ServerPlayer actor, @Nullable String teamArg) {
        if (teamArg != null) {
            String team = Teams.resolveAlias(teamArg);
            if (team == null || !Teams.isCombatTeam(team)) {
                source.sendFailure(Component.translatable("gui.pjmbasemod.faction.selection.invalid_team"));
                return null;
            }
            return team;
        }
        FactionMenuService.Authority authority = FactionMenuService.authority(actor);
        if (!authority.valid() || !authority.canKick()) {
            source.sendFailure(Component.translatable("gui.pjmbasemod.faction.manage.no_access"));
            return null;
        }
        return authority.teamId();
    }

    private static int factionKick(CommandSourceStack source, String playerName, @Nullable String teamArg) {
        ServerPlayer actor = requirePlayer(source);
        if (actor == null) return 0;
        String team = resolveKickTeam(source, actor, teamArg);
        if (team == null) return 0;
        return FactionMenuService.kickByName(actor, team, playerName) ? 1 : 0;
    }

    private static int factionInvite(CommandSourceStack source, String playerName, @Nullable String teamArg, boolean invite) {
        ServerPlayer actor = requirePlayer(source);
        if (actor == null) return 0;
        String team = resolveInviteTeam(source, actor, teamArg);
        if (team == null) return 0;
        FactionMenuService.applyInvite(actor, team, playerName, invite);
        return 1;
    }

    private static int factionInvites(CommandSourceStack source, @Nullable String teamArg) {
        ServerPlayer actor = requirePlayer(source);
        if (actor == null) return 0;
        String team = resolveInviteTeam(source, actor, teamArg);
        if (team == null) return 0;
        var invites = ru.liko.pjmbasemod.common.faction.FactionInviteSavedData.get(source.getServer()).invites(team);
        if (invites.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("gui.pjmbasemod.faction.invite.list_empty"), false);
            return 1;
        }
        long now = System.currentTimeMillis();
        source.sendSuccess(() -> Component.translatable("gui.pjmbasemod.faction.invite.list_header",
                Teams.displayName(source.getServer(), team), invites.size()), false);
        for (var entry : invites.entrySet()) {
            long expiresAt = entry.getValue().expiresAt();
            String suffix = expiresAt == 0L
                    ? Component.translatable("gui.pjmbasemod.faction.invite.no_expiry").getString()
                    : Component.translatable("gui.pjmbasemod.faction.invite.expires_minutes",
                            Math.max(1, (expiresAt - now) / 60_000L)).getString();
            String from = entry.getValue().inviter().isBlank() ? "" : ", от " + entry.getValue().inviter();
            source.sendSuccess(() -> Component.literal(" - " + entry.getKey() + " (" + suffix + from + ")"), false);
        }
        return 1;
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
        String team = Teams.resolvePlayerTeamId(target);
        String teamLabel = team == null ? "нет фракции" : Teams.displayName(source.getServer(), team);
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
                : "Назначенные роли фракции " + Teams.displayName(source.getServer(), filter) + ":"), false);
        int count = 0;
        for (Map.Entry<UUID, RoleSavedData.RoleEntry> entry : data.entries().entrySet()) {
            RoleSavedData.RoleEntry roleEntry = entry.getValue();
            if (filter != null && !filter.equals(roleEntry.teamId())) continue;
            CombatRole role = CombatRole.byIdOrAlias(roleEntry.roleId());
            String roleName = role == null ? roleEntry.roleId() : role.displayName();
            String teamName = Teams.displayName(source.getServer(), roleEntry.teamId());
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

        String targetTeam = Teams.resolvePlayerTeamId(target);
        if (!team.equals(targetTeam)) {
            source.sendFailure(Component.literal("Игрок " + target.getName().getString()
                    + " не состоит во фракции " + Teams.displayName(source.getServer(), team) + "."));
            return 0;
        }

        FactionCommanderService.AssignmentResult result = FactionCommanderService.setCommander(source.getServer(), team, target);
        String teamName = Teams.displayName(source.getServer(), team);
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
        String teamName = Teams.displayName(source.getServer(), team);
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
                team = Teams.resolvePlayerTeamId(player);
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
        String teamName = Teams.displayName(source.getServer(), team);
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
        for (var team : Teams.all()) {
            FactionCommanderSavedData.CommanderEntry entry = data.commander(team.id());
            String teamName = Teams.displayName(source.getServer(), team.id());
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

    // ---------------------------------------------------------------- /pjm web — веб-панель

    private static LiteralArgumentBuilder<CommandSourceStack> webCommand() {
        return Commands.literal("web")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("login").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!Config.isWebEnabled()) {
                        ctx.getSource().sendFailure(Component.translatable("pjmbasemod.web.login.disabled"));
                        return 0;
                    }
                    String code = WebAuthService.issueCode(player);
                    Component codeComponent = Component.literal(code).withStyle(style -> style
                            .withColor(ChatFormatting.GOLD)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("pjmbasemod.web.login.copy"))));
                    ctx.getSource().sendSuccess(
                            () -> Component.translatable("pjmbasemod.web.login.header", codeComponent), false);
                    // Ссылка всегда: publicUrl из конфига или http://<IP сервера>:<порт>.
                    String url = ru.liko.pjmbasemod.common.web.WebPanelService
                            .panelBaseUrl(ctx.getSource().getServer()) + "/login?code=" + code;
                    ctx.getSource().sendSuccess(() -> Component.translatable("pjmbasemod.web.login.link")
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))), false);
                    return 1;
                }))
                .then(Commands.literal("logout").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    int revoked = WebAuthService.revokeAllFor(player.getUUID());
                    ctx.getSource().sendSuccess(
                            () -> Component.translatable("pjmbasemod.web.logout.done", revoked), false);
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> baseZoneCommand() {
        return Commands.literal("basezone")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> createBaseZone(ctx.getSource(), StringArgumentType.getString(ctx, "name"), null))
                                .then(Commands.argument("displayName", StringArgumentType.greedyString())
                                        .executes(ctx -> createBaseZone(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "displayName"))))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> deleteBaseZone(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listBaseZones(ctx.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> baseZoneInfo(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("displayname")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("displayName", StringArgumentType.greedyString())
                                        .executes(ctx -> setBaseZoneDisplayName(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "displayName"))))))
                .then(Commands.literal("owner")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("owner", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestCombatTeams(builder))
                                        .executes(ctx -> setBaseZoneOwner(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "owner"))))))
                .then(Commands.literal("pos1")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> setBaseZonePos(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true))))
                .then(Commands.literal("pos2")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> setBaseZonePos(ctx.getSource(), StringArgumentType.getString(ctx, "name"), false))));
    }

    private static int createBaseZone(CommandSourceStack source, String name, @Nullable String displayName) {
        BaseZoneSavedData data = BaseZoneSavedData.get(source.getServer());
        boolean existed = data.zone(name) != null;
        BaseZone zone = data.getOrCreateZone(name);
        if (displayName != null && !displayName.isBlank()) {
            zone.setDisplayName(displayName);
            data.setDirty();
        }
        source.sendSuccess(() -> Component.literal(existed
                ? "Зона базы '" + name + "' уже существует"
                : "Зона базы '" + name + "' создана. Задай границы pos1/pos2 и владельца: /pjm basezone owner " + name + " <team>"), true);
        return existed ? 0 : 1;
    }

    private static int deleteBaseZone(CommandSourceStack source, String name) {
        BaseZoneSavedData data = BaseZoneSavedData.get(source.getServer());
        if (!data.deleteZone(name)) {
            source.sendFailure(Component.literal("Зона базы '" + name + "' не найдена"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Зона базы '" + name + "' удалена"), true);
        return 1;
    }

    private static int listBaseZones(CommandSourceStack source) {
        BaseZoneSavedData data = BaseZoneSavedData.get(source.getServer());
        if (data.zones().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Зоны базы не созданы"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Зоны базы:"), false);
        for (BaseZone zone : data.zones()) {
            source.sendSuccess(() -> Component.literal(" - " + describeBaseZone(source, zone)), false);
        }
        return data.zones().size();
    }

    private static int baseZoneInfo(CommandSourceStack source, String name) {
        BaseZone zone = BaseZoneSavedData.get(source.getServer()).zone(name);
        if (zone == null) {
            source.sendFailure(Component.literal("Зона базы '" + name + "' не найдена"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(describeBaseZone(source, zone)), false);
        return 1;
    }

    private static int setBaseZoneDisplayName(CommandSourceStack source, String name, String displayName) {
        BaseZoneSavedData data = BaseZoneSavedData.get(source.getServer());
        BaseZone zone = data.zone(name);
        if (zone == null) {
            source.sendFailure(Component.literal("Зона базы '" + name + "' не найдена"));
            return 0;
        }
        zone.setDisplayName(displayName);
        data.setDirty();
        source.sendSuccess(() -> Component.literal("DisplayName зоны '" + zone.name() + "' = " + zone.displayName()), true);
        return 1;
    }

    private static int setBaseZoneOwner(CommandSourceStack source, String name, String ownerArg) {
        BaseZoneSavedData data = BaseZoneSavedData.get(source.getServer());
        BaseZone zone = data.zone(name);
        if (zone == null) {
            source.sendFailure(Component.literal("Зона базы '" + name + "' не найдена"));
            return 0;
        }
        String owner = parseCombatTeam(source, ownerArg);
        if (owner == null) return 0;
        zone.setOwner(owner);
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Владелец зоны '" + zone.name() + "' = " + Teams.displayName(source.getServer(), owner)), true);
        return 1;
    }

    private static int setBaseZonePos(CommandSourceStack source, String name, boolean first) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) return 0;

        BaseZoneSavedData data = BaseZoneSavedData.get(source.getServer());
        BaseZone zone = data.getOrCreateZone(name);
        String dimension = dimensionId(player);
        if (!zone.dimension().isBlank() && !zone.dimension().equals(dimension)) {
            source.sendFailure(Component.literal("Зона базы уже привязана к измерению " + zone.dimension()));
            return 0;
        }

        BlockPos pos = player.blockPosition();
        if (first) zone.setPos1(dimension, pos);
        else zone.setPos2(dimension, pos);
        data.setDirty();

        source.sendSuccess(() -> Component.literal((first ? "pos1" : "pos2") + " зоны '" + name + "' = "
                + pos.getX() + " " + pos.getY() + " " + pos.getZ()), true);
        if (zone.isComplete()) {
            source.sendSuccess(() -> Component.literal("Границы заданы: " + describeBaseZone(source, zone)), false);
        }
        return 1;
    }

    private static String describeBaseZone(CommandSourceStack source, BaseZone zone) {
        String ownerLabel = zone.owner().isBlank() ? "не задан" : Teams.displayName(source.getServer(), zone.owner());
        if (!zone.isComplete()) {
            return zone.name() + " (" + zone.displayName() + ") | границы не заданы | владелец: " + ownerLabel
                    + " | dimension: " + (zone.dimension().isBlank() ? "не задано" : zone.dimension());
        }
        return zone.name() + " (" + zone.displayName() + ") | " + zone.dimension()
                + " | X " + zone.minX() + ".." + zone.maxX()
                + ", Y " + zone.minY() + ".." + zone.maxY()
                + ", Z " + zone.minZ() + ".." + zone.maxZ()
                + " | владелец: " + ownerLabel;
    }

    private static CompletableFuture<Suggestions> suggestTeams(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        builder.suggest("neutral");
        builder.suggest("none");
        for (var team : Teams.all()) {
            builder.suggest(team.id());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCombatTeams(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (var team : Teams.all()) {
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
        String team = Teams.resolveAlias(raw);
        if (team == null || !Teams.isCombatTeam(team)) {
            source.sendFailure(Component.literal("Неизвестная боевая фракция '" + raw + "'. Используй id из teams.definitions."));
            return null;
        }
        return team;
    }

    @Nullable
    private static CombatRole parseRole(CommandSourceStack source, String raw) {
        CombatRole role = CombatRole.byIdOrAlias(raw);
        if (role == null) {
            source.sendFailure(Component.literal("Неизвестная роль '" + raw + "'. Используй assault, machine_gunner, sniper, pilot или crew."));
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

    /** Открыть экран управления фракцией вправе админ, командир и заместитель с правом OPEN_GUI. */
    private static boolean canOpenFactionManagement(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return FactionMenuService.authority(player).canOpen();
        }
        return source.hasPermission(2);
    }

    @Nullable
    private static String parseTeam(CommandSourceStack source, String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("neutral") || raw.equalsIgnoreCase("none")) {
            return Teams.NEUTRAL_ID;
        }
        String team = Teams.resolveAlias(raw);
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

    private static String dimensionId(ServerPlayer player) {
        return player.serverLevel().dimension().location().toString();
    }

    // --------------------------------------------------------------- capturepoint (точки захвата на полигонах)

    private static LiteralArgumentBuilder<CommandSourceStack> capturePointCommand() {
        return Commands.literal("capturepoint")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> capturePointAdd(ctx.getSource(), StringArgumentType.getString(ctx, "id"), null))
                                .then(Commands.argument("display_name", StringArgumentType.string())
                                        .executes(ctx -> capturePointAdd(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "display_name"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> capturePointRemove(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("list")
                        .executes(ctx -> capturePointList(ctx.getSource())))
                .then(Commands.literal("setowner")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("owner", StringArgumentType.word())
                                        .executes(ctx -> capturePointSetOwner(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "owner"))))))
                .then(Commands.literal("order")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("order", IntegerArgumentType.integer())
                                        .executes(ctx -> capturePointSetOrder(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                IntegerArgumentType.getInteger(ctx, "order"))))))
                .then(Commands.literal("sync")
                        .executes(ctx -> capturePointSync(ctx.getSource())))
                .then(Commands.literal("warehouse")
                        .executes(ctx -> capturePointWarehouseList(ctx.getSource()))
                        .then(Commands.argument("team", StringArgumentType.word())
                                .then(Commands.literal("clear")
                                        .executes(ctx -> capturePointWarehouseSet(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team"), null)))
                                .then(Commands.argument("warehouse", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (String id : ru.liko.pjmbasemod.common.warehouse.WarehouseSavedData
                                                    .get(ctx.getSource().getServer()).ids()) {
                                                builder.suggest(id);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> capturePointWarehouseSet(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team"),
                                                StringArgumentType.getString(ctx, "warehouse"))))))
                .then(Commands.literal("enable")
                        .executes(ctx -> capturePointSetEnabled(ctx.getSource(), true)))
                .then(Commands.literal("disable")
                        .executes(ctx -> capturePointSetEnabled(ctx.getSource(), false)))
                .then(Commands.literal("status")
                        .executes(ctx -> capturePointStatus(ctx.getSource())))
                .then(Commands.literal("minplayers")
                        .then(Commands.literal("ignore")
                                .then(Commands.literal("clear")
                                        .executes(ctx -> capturePointMinPlayersIgnore(ctx.getSource(), null)))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (Config.ConfiguredTeam team : Teams.all()) builder.suggest(team.id());
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> capturePointMinPlayersIgnore(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team")))))
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 1000))
                                .executes(ctx -> capturePointMinPlayers(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("schedule")
                        .then(Commands.literal("on")
                                .executes(ctx -> capturePointScheduleToggle(ctx.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(ctx -> capturePointScheduleToggle(ctx.getSource(), false)))
                        .then(Commands.literal("list")
                                .executes(ctx -> capturePointScheduleList(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> capturePointScheduleClear(ctx.getSource())))
                        .then(Commands.literal("add")
                                .then(Commands.argument("start_hour", IntegerArgumentType.integer(0, 23))
                                        .then(Commands.argument("start_minute", IntegerArgumentType.integer(0, 59))
                                                .then(Commands.argument("end_hour", IntegerArgumentType.integer(0, 23))
                                                        .then(Commands.argument("end_minute", IntegerArgumentType.integer(0, 59))
                                                                .executes(ctx -> capturePointScheduleAdd(ctx.getSource(),
                                                                        IntegerArgumentType.getInteger(ctx, "start_hour"),
                                                                        IntegerArgumentType.getInteger(ctx, "start_minute"),
                                                                        IntegerArgumentType.getInteger(ctx, "end_hour"),
                                                                        IntegerArgumentType.getInteger(ctx, "end_minute"))))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                        .executes(ctx -> capturePointScheduleRemove(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "index"))))));
    }

    private static int capturePointSetEnabled(CommandSourceStack source, boolean enabled) {
        CapturePointManager.setEnabled(source.getServer(), enabled);
        source.sendSuccess(() -> Component.literal("Захват точек " + (enabled ? "включён" : "выключен")), true);
        return 1;
    }

    private static int capturePointStatus(CommandSourceStack source) {
        boolean enabled = Config.isCapturePointsEnabled();
        boolean schedule = Config.isCapturePointScheduleEnabled();
        int windowCount = Config.getCapturePointScheduleWindows().size();
        int minPlayers = Config.getCapturePointAutoEnableMinPlayers();
        String ignore = Config.getCapturePointAutoEnableIgnoreTeam();
        int online = Teams.minCombatOnline(source.getServer(), ignore);
        source.sendSuccess(() -> Component.literal("Захват точек: " + (enabled ? "включён" : "выключен")
                + " | расписание: " + (schedule ? "вкл (окон: " + windowCount + ")" : "выкл")
                + " | порог онлайна во фракции: " + (minPlayers > 0 ? minPlayers + " (минимум по фракциям сейчас " + online + ")" : "выкл")
                + (ignore.isBlank() ? "" : " | не учитывается фракция: " + ignore)), false);
        return 1;
    }

    private static int capturePointMinPlayers(CommandSourceStack source, int count) {
        Config.setCapturePointAutoEnableMinPlayers(count);
        source.sendSuccess(() -> Component.literal(count > 0
                ? "Автовключение захвата при онлайне от " + count + " игроков в каждой фракции"
                : "Автовключение захвата по онлайну выключено"), true);
        return 1;
    }

    private static int capturePointMinPlayersIgnore(CommandSourceStack source, @Nullable String teamId) {
        if (teamId != null && !Teams.isCombatTeam(teamId)) {
            source.sendFailure(Component.literal("Неизвестная фракция: " + teamId));
            return 0;
        }
        Config.setCapturePointAutoEnableIgnoreTeam(teamId);
        source.sendSuccess(() -> Component.literal(teamId == null
                ? "Порог онлайна снова учитывает все фракции"
                : "Порог онлайна не учитывает фракцию " + Teams.normalize(teamId)), true);
        return 1;
    }

    private static int capturePointScheduleToggle(CommandSourceStack source, boolean on) {
        Config.setCapturePointScheduleEnabled(on);
        source.sendSuccess(() -> Component.literal("Расписание захвата " + (on ? "включено" : "выключено")), true);
        return 1;
    }

    private static int capturePointScheduleAdd(CommandSourceStack source, int startHour, int startMinute, int endHour, int endMinute) {
        Config.addCapturePointScheduleWindow(startHour, startMinute, endHour, endMinute);
        Config.setCapturePointScheduleEnabled(true);
        source.sendSuccess(() -> Component.literal("Добавлено окно расписания: " + formatHm(startHour, startMinute)
                + "–" + formatHm(endHour, endMinute)), true);
        return 1;
    }

    private static String formatHm(int hour, int minute) {
        return String.format("%02d:%02d", hour, minute);
    }

    private static int capturePointScheduleRemove(CommandSourceStack source, int index) {
        if (!Config.removeCapturePointScheduleWindow(index)) {
            source.sendFailure(Component.literal("Окно расписания с индексом " + index + " не найдено"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Удалено окно расписания #" + index), true);
        return 1;
    }

    private static int capturePointScheduleClear(CommandSourceStack source) {
        Config.clearCapturePointScheduleWindows();
        source.sendSuccess(() -> Component.literal("Расписание захвата очищено"), true);
        return 1;
    }

    private static int capturePointScheduleList(CommandSourceStack source) {
        List<Config.ScheduleWindow> windows = Config.getCapturePointScheduleWindows();
        boolean enabled = Config.isCapturePointScheduleEnabled();
        source.sendSuccess(() -> Component.literal("Расписание захвата: " + (enabled ? "включено" : "выключено")
                + ", окон: " + windows.size()), false);
        for (int i = 0; i < windows.size(); i++) {
            Config.ScheduleWindow w = windows.get(i);
            int idx = i;
            source.sendSuccess(() -> Component.literal(" [" + idx + "] " + formatHm(w.startHour(), w.startMinute())
                    + "–" + formatHm(w.endHour(), w.endMinute())), false);
        }
        return windows.size();
    }

    private static int capturePointAdd(CommandSourceStack source, String id, @Nullable String displayName) {
        ServerPlayer player = requirePlayer(source);
        String dimension = player == null ? "minecraft:overworld" : dimensionId(player);
        CapturePointSavedData data = CapturePointSavedData.get(source.getServer());
        if (!data.addPoint(id, displayName, dimension)) {
            source.sendFailure(Component.literal("Точка захвата '" + id + "' уже существует"));
            return 0;
        }
        CapturePointManager.broadcastMapSync(source.getServer(), data, "capturepoint_added");
        source.sendSuccess(() -> Component.literal("Создана точка захвата '" + id + "'"
                + (displayName != null ? " (" + displayName + ")" : "")
                + ". Открой карту (N), включи «Правка точек» и обведи зону."), true);
        return 1;
    }

    private static int capturePointRemove(CommandSourceStack source, String id) {
        CapturePointSavedData data = CapturePointSavedData.get(source.getServer());
        if (!data.removePoint(id)) {
            source.sendFailure(Component.literal("Точка захвата '" + id + "' не найдена"));
            return 0;
        }
        CapturePointManager.broadcastMapSync(source.getServer(), data, "capturepoint_removed");
        source.sendSuccess(() -> Component.literal("Удалена точка захвата '" + id + "'"), true);
        return 1;
    }

    private static int capturePointList(CommandSourceStack source) {
        CapturePointSavedData data = CapturePointSavedData.get(source.getServer());
        if (data.entries().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Точки захвата не настроены"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Точки захвата (" + data.entries().size() + "):"), false);
        int count = 0;
        for (CapturePointSavedData.Entry entry : data.entries()) {
            String owner = entry.ownerTeamId.isEmpty() ? "нейтрально" : entry.ownerTeamId;
            String status = entry.captureTeamId.isEmpty() ? "" : " [захват: " + entry.captureTeamId + "]";
            source.sendSuccess(() -> Component.literal(" - " + entry.id + " (" + entry.displayName + ")"
                    + " | order: " + entry.order + " | " + entry.dimension + " | вершин: " + entry.vertices.size()
                    + " | владелец: " + owner + status), false);
            count++;
        }
        return count;
    }

    private static int capturePointSetOwner(CommandSourceStack source, String id, String ownerArg) {
        String owner = parseTeam(source, ownerArg);
        if (owner == null) return 0;
        CapturePointSavedData data = CapturePointSavedData.get(source.getServer());
        if (!data.setOwner(id, owner)) {
            source.sendFailure(Component.literal("Точка захвата '" + id + "' не найдена"));
            return 0;
        }
        CapturePointManager.broadcastMapSync(source.getServer(), data, "capturepoint_owner_set");
        String ownerLabel = owner.isEmpty() ? "нейтрально" : owner;
        source.sendSuccess(() -> Component.literal("Владелец точки '" + id + "' = " + ownerLabel), true);
        return 1;
    }

    private static int capturePointSetOrder(CommandSourceStack source, String id, int order) {
        CapturePointSavedData data = CapturePointSavedData.get(source.getServer());
        if (!data.setOrder(id, order)) {
            source.sendFailure(Component.literal("Точка захвата '" + id + "' не найдена"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Порядок точки '" + id + "' = " + order
                + " (последовательный захват: базы концов задай через setowner)"), true);
        return 1;
    }

    private static int capturePointSync(CommandSourceStack source) {
        CapturePointSavedData data = CapturePointSavedData.get(source.getServer());
        CapturePointManager.broadcastMapSync(source.getServer(), data, "command_capturepoint_sync");
        source.sendSuccess(() -> Component.literal("Синхронизация точек захвата отправлена игрокам"), false);
        return 1;
    }

    private static int capturePointWarehouseList(CommandSourceStack source) {
        Map<String, String> map = Config.getCapturePointWarehouseByTeam();
        if (map.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Склады-получатели дохода не привязаны. "
                    + "Привязка: /pjm capturepoint warehouse <фракция> <склад>"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Доход с точек: " + Config.getCapturePointIncomePerPoint()
                + " очков SUPPLY за точку каждые " + Config.getCapturePointIncomeIntervalMinutes() + " мин."), false);
        map.forEach((team, warehouse) -> source.sendSuccess(
                () -> Component.literal(" - " + team + " → склад '" + warehouse + "'"), false));
        return map.size();
    }

    private static int capturePointWarehouseSet(CommandSourceStack source, String teamArg, @Nullable String warehouseId) {
        String team = parseTeam(source, teamArg);
        if (team == null) return 0;
        if (team.isEmpty()) {
            source.sendFailure(Component.literal("Укажи боевую фракцию, а не neutral"));
            return 0;
        }
        Config.setCapturePointWarehouseForTeam(team, warehouseId);
        source.sendSuccess(() -> Component.literal(warehouseId == null
                ? "Привязка склада для '" + team + "' снята"
                : "Доход фракции '" + team + "' идёт на склад '" + warehouseId + "'"), true);
        return 1;
    }

    // --------------------------------------------------------------- campaign (недельная кампания)

    private static LiteralArgumentBuilder<CommandSourceStack> campaignCommand() {
        return Commands.literal("campaign")
                .executes(ctx -> campaignStatus(ctx.getSource()))
                .then(Commands.literal("status")
                        .executes(ctx -> campaignStatus(ctx.getSource())))
                .then(Commands.literal("on")
                        .requires(source -> source.hasPermission(4))
                        .executes(ctx -> campaignToggle(ctx.getSource(), true)))
                .then(Commands.literal("off")
                        .requires(source -> source.hasPermission(4))
                        .executes(ctx -> campaignToggle(ctx.getSource(), false)))
                .then(Commands.literal("restart")
                        .requires(source -> source.hasPermission(4))
                        .executes(ctx -> campaignRestart(ctx.getSource())))
                .then(Commands.literal("finish")
                        .requires(source -> source.hasPermission(4))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Завершит кампанию СЕЙЧАС: победитель, полный вайп сезона, новый раунд. "
                                            + "Подтверждение: /pjm campaign finish confirm")
                                    .withStyle(net.minecraft.ChatFormatting.RED), false);
                            return 1;
                        })
                        .then(Commands.literal("confirm")
                                .executes(ctx -> campaignFinish(ctx.getSource()))));
    }

    private static int campaignStatus(CommandSourceStack source) {
        if (!Config.isCampaignEnabled()) {
            source.sendSuccess(() -> Component.literal("Кампания выключена (/pjm campaign on)"), false);
            return 0;
        }
        var data = ru.liko.pjmbasemod.common.campaign.CampaignSavedData.get(source.getServer());
        if (data.startEpochMs() <= 0) {
            source.sendSuccess(() -> Component.literal("Кампания стартует на следующем тике сервера"), false);
            return 1;
        }
        long endMs = data.startEpochMs() + Config.getCampaignDurationDays() * 86_400_000L;
        long left = Math.max(0, endMs - System.currentTimeMillis()) / 1000L;
        String time = String.format("%dд %dч %dм", left / 86400, (left % 86400) / 3600, (left % 3600) / 60);
        source.sendSuccess(() -> Component.literal("Кампания: до вайпа " + time), false);
        for (Config.ConfiguredTeam team : Teams.all()) {
            long vp = data.vp(team.id());
            source.sendSuccess(() -> Component.literal(" - " + Teams.displayName(source.getServer(), team.id())
                    + ": " + vp + " VP"), false);
        }
        return 1;
    }

    private static int campaignToggle(CommandSourceStack source, boolean on) {
        Config.setCampaignEnabled(on);
        ru.liko.pjmbasemod.common.campaign.CampaignManager.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Кампания " + (on ? "включена" : "выключена")), true);
        return 1;
    }

    private static int campaignRestart(CommandSourceStack source) {
        ru.liko.pjmbasemod.common.campaign.CampaignSavedData.get(source.getServer())
                .restart(System.currentTimeMillis());
        ru.liko.pjmbasemod.common.campaign.CampaignManager.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Кампания перезапущена: VP в ноль, отсчёт недели заново (без вайпа)"), true);
        return 1;
    }

    private static int campaignFinish(CommandSourceStack source) {
        if (!Config.isCampaignEnabled()) {
            source.sendFailure(Component.literal("Кампания выключена"));
            return 0;
        }
        ru.liko.pjmbasemod.common.campaign.CampaignManager.finish(source.getServer(), "принудительно");
        return 1;
    }

}
