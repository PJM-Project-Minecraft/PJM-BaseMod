package ru.liko.pjmbasemod.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import ru.liko.pjmbasemod.common.customization.SkinRegistry;
import ru.liko.pjmbasemod.common.entity.QuartermasterEntity;
import ru.liko.pjmbasemod.common.teams.Teams;
import ru.liko.pjmbasemod.common.init.PjmEntities;
import ru.liko.pjmbasemod.common.warehouse.WarehouseBudgetLimits;
import ru.liko.pjmbasemod.common.warehouse.WarehouseItemRegistry;
import ru.liko.pjmbasemod.common.warehouse.WarehousePersonalBudgetSavedData;
import ru.liko.pjmbasemod.common.warehouse.WarehousePoolCategory;
import ru.liko.pjmbasemod.common.warehouse.WarehouseSavedData;
import ru.liko.pjmbasemod.common.warehouse.WarehouseSettings;
import ru.liko.pjmbasemod.common.warehouse.WarehouseSettingsSavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Дерево команд {@code /pjm warehouse ...}: управление именованными складами,
 * зонами приёма, очками и NPC-кладовщиками.
 */
public final class WarehouseCommands {

    private static final int NPC_SEARCH_RADIUS = 12;
    // Единый источник истины — имена валидны для ResourceLocation (без '+'), совпадают с текстурами.
    private static final List<String> KNOWN_SKINS = SkinRegistry.KNOWN_SKINS;

    private WarehouseCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("warehouse")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> suggestWarehouses(ctx.getSource(), b))
                                .executes(ctx -> delete(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("reception")
                        .then(Commands.literal("set")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, b) -> suggestWarehouses(ctx.getSource(), b))
                                        .executes(ctx -> setReception(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("radius")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, b) -> suggestWarehouses(ctx.getSource(), b))
                                        .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 128))
                                                .executes(ctx -> setRadius(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        IntegerArgumentType.getInteger(ctx, "blocks")))))))
                .then(Commands.literal("points")
                        .then(Commands.literal("add")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, b) -> suggestWarehouses(ctx.getSource(), b))
                                        .then(Commands.argument("category", StringArgumentType.word())
                                                .suggests((ctx, b) -> suggestPools(b))
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
                                                        .executes(ctx -> addPoints(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "id"),
                                                                StringArgumentType.getString(ctx, "category"),
                                                                IntegerArgumentType.getInteger(ctx, "amount")))))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, b) -> suggestWarehouses(ctx.getSource(), b))
                                        .then(Commands.argument("category", StringArgumentType.word())
                                                .suggests((ctx, b) -> suggestPoolsWithAll(b))
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100000))
                                                        .executes(ctx -> setPoints(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "id"),
                                                                StringArgumentType.getString(ctx, "category"),
                                                                IntegerArgumentType.getInteger(ctx, "amount")))))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, b) -> suggestWarehouses(ctx.getSource(), b))
                                        .executes(ctx -> clearPoints(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"), "all"))
                                        .then(Commands.argument("category", StringArgumentType.word())
                                                .suggests((ctx, b) -> suggestPoolsWithAll(b))
                                                .executes(ctx -> clearPoints(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        StringArgumentType.getString(ctx, "category"))))))
                        .then(Commands.literal("get")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, b) -> suggestWarehouses(ctx.getSource(), b))
                                        .executes(ctx -> getPoints(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))))
                .then(Commands.literal("additem")
                        .then(Commands.argument("pool", StringArgumentType.word())
                                .suggests((ctx, b) -> suggestPools(b))
                                .then(Commands.argument("cost", IntegerArgumentType.integer(1, 100000))
                                        .executes(ctx -> addItem(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "pool"),
                                                IntegerArgumentType.getInteger(ctx, "cost"), 1, null, null))
                                        // quantity (int) регистрируем ПЕРВЫМ — иначе greedyString-хвост перехватит число.
                                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 6400))
                                                .executes(ctx -> addItem(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "pool"),
                                                        IntegerArgumentType.getInteger(ctx, "cost"),
                                                        IntegerArgumentType.getInteger(ctx, "quantity"), null, null))
                                                .then(Commands.argument("category", StringArgumentType.word())
                                                        .executes(ctx -> addItem(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "pool"),
                                                                IntegerArgumentType.getInteger(ctx, "cost"),
                                                                IntegerArgumentType.getInteger(ctx, "quantity"),
                                                                StringArgumentType.getString(ctx, "category"), null))
                                                        // полный позиционный набор + key=value хвост
                                                        .then(Commands.argument("modifiers", StringArgumentType.greedyString())
                                                                .suggests((ctx, b) -> suggestModifierKeys(b))
                                                                .executes(ctx -> addItem(ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "pool"),
                                                                        IntegerArgumentType.getInteger(ctx, "cost"),
                                                                        IntegerArgumentType.getInteger(ctx, "quantity"),
                                                                        StringArgumentType.getString(ctx, "category"),
                                                                        StringArgumentType.getString(ctx, "modifiers"))))))
                                        // key=value хвост сразу после cost (без позиционных quantity/category).
                                        // Регистрируется ПОСЛЕ quantity, чтобы число ушло в quantity, а не сюда.
                                        .then(Commands.argument("modifiers", StringArgumentType.greedyString())
                                                .suggests((ctx, b) -> suggestModifierKeys(b))
                                                .executes(ctx -> addItem(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "pool"),
                                                        IntegerArgumentType.getInteger(ctx, "cost"), 1, null,
                                                        StringArgumentType.getString(ctx, "modifiers")))))))
                .then(Commands.literal("removeitem")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> suggestItems(b))
                                .executes(ctx -> removeItem(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("budget")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("get")
                                        .executes(ctx -> budgetGet(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
                                .then(Commands.literal("full")
                                        .executes(ctx -> budgetFull(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 1_000_000))
                                                .executes(ctx -> budgetSet(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                                                .executes(ctx -> budgetAdd(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")))))))
                .then(Commands.literal("npc")
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("warehouseId", StringArgumentType.word())
                                        .suggests((ctx, b) -> suggestWarehouses(ctx.getSource(), b))
                                        .executes(ctx -> spawnNpc(ctx.getSource(), StringArgumentType.getString(ctx, "warehouseId")))))
                        .then(Commands.literal("skin")
                                .then(Commands.argument("skinId", StringArgumentType.word())
                                        .suggests((ctx, b) -> suggestSkins(b))
                                        .executes(ctx -> npcSkin(ctx.getSource(), StringArgumentType.getString(ctx, "skinId")))))
                        .then(Commands.literal("team")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests((ctx, b) -> suggestTeams(b))
                                        .executes(ctx -> npcTeam(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
                        .then(Commands.literal("warehouse")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, b) -> suggestWarehouses(ctx.getSource(), b))
                                        .executes(ctx -> npcWarehouse(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("limit")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 256))
                                        .executes(ctx -> npcLimit(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(Commands.literal("cooldown")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 72000))
                                        .executes(ctx -> npcCooldown(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "ticks")))))
                        .then(Commands.literal("categories")
                                .then(Commands.argument("list", StringArgumentType.greedyString())
                                        .executes(ctx -> npcCategories(ctx.getSource(), StringArgumentType.getString(ctx, "list"))))));
    }

    // ---------------------------------------------------------------- склады

    private static int create(CommandSourceStack source, String id) {
        String wid = WarehouseItemRegistry.sanitizeId(id);
        if (wid.isBlank()) {
            source.sendFailure(Component.literal("Некорректный id склада."));
            return 0;
        }
        WarehouseSavedData.get(source.getServer()).createWarehouse(wid);
        source.sendSuccess(() -> Component.literal("Склад '" + wid + "' создан."), true);
        return 1;
    }

    private static int delete(CommandSourceStack source, String id) {
        String wid = WarehouseItemRegistry.sanitizeId(id);
        boolean removed = WarehouseSavedData.get(source.getServer()).delete(wid);
        WarehouseSettingsSavedData.get(source.getServer()).remove(wid);
        if (!removed) {
            source.sendFailure(Component.literal("Склад '" + wid + "' не найден."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Склад '" + wid + "' удалён."), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        var ids = WarehouseSavedData.get(source.getServer()).ids();
        if (ids.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Складов нет."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Склады: " + String.join(", ", ids)), false);
        return 1;
    }

    // ---------------------------------------------------------------- зона приёма

    private static int setReception(CommandSourceStack source, String id) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) return 0;
        String wid = WarehouseItemRegistry.sanitizeId(id);
        WarehouseSavedData.get(source.getServer()).createWarehouse(wid);
        WarehouseSettings settings = WarehouseSettingsSavedData.get(source.getServer())
                .setReception(wid, player.serverLevel(), player.blockPosition());
        source.sendSuccess(() -> Component.literal("Зона приёма склада '" + wid + "' установлена в "
                + formatPos(settings) + " (радиус " + settings.receptionRadius() + ")."), true);
        return 1;
    }

    private static int setRadius(CommandSourceStack source, String id, int radius) {
        String wid = WarehouseItemRegistry.sanitizeId(id);
        WarehouseSettings settings = WarehouseSettingsSavedData.get(source.getServer()).setRadius(wid, radius);
        source.sendSuccess(() -> Component.literal("Радиус зоны приёма склада '" + wid + "' = "
                + settings.receptionRadius() + "."), true);
        return 1;
    }

    // ---------------------------------------------------------------- очки

    private static int addPoints(CommandSourceStack source, String id, String category, int amount) {
        WarehousePoolCategory pool = WarehousePoolCategory.byId(category);
        if (pool == null) {
            source.sendFailure(Component.literal("Неизвестный пул: " + category + ". Используй weapon, supply, equipment, raw или special."));
            return 0;
        }
        String wid = WarehouseItemRegistry.sanitizeId(id);
        WarehouseSavedData data = WarehouseSavedData.get(source.getServer());
        data.createWarehouse(wid);
        data.addPoints(wid, pool, amount);
        source.sendSuccess(() -> Component.literal("Складу '" + wid + "' добавлено " + amount
                + " очков пула " + pool.id() + " (всего " + data.getPoints(wid, pool) + ")."), true);
        return 1;
    }

    private static int setPoints(CommandSourceStack source, String id, String category, int amount) {
        String wid = WarehouseItemRegistry.sanitizeId(id);
        WarehouseSavedData data = WarehouseSavedData.get(source.getServer());
        data.createWarehouse(wid);
        if ("all".equalsIgnoreCase(category)) {
            for (WarehousePoolCategory pool : WarehousePoolCategory.values()) {
                data.setPoints(wid, pool, amount);
            }
            source.sendSuccess(() -> Component.literal("Складу '" + wid + "' выставлено "
                    + amount + " очков во всех пулах."), true);
            return 1;
        }
        WarehousePoolCategory pool = WarehousePoolCategory.byId(category);
        if (pool == null) {
            source.sendFailure(Component.literal("Неизвестный пул: " + category
                    + ". Используй weapon, supply, equipment, raw, special или all."));
            return 0;
        }
        data.setPoints(wid, pool, amount);
        source.sendSuccess(() -> Component.literal("Складу '" + wid + "' выставлено "
                + amount + " очков пула " + pool.id() + "."), true);
        return 1;
    }

    private static int clearPoints(CommandSourceStack source, String id, String category) {
        String wid = WarehouseItemRegistry.sanitizeId(id);
        WarehouseSavedData data = WarehouseSavedData.get(source.getServer());
        data.createWarehouse(wid);
        if ("all".equalsIgnoreCase(category)) {
            for (WarehousePoolCategory pool : WarehousePoolCategory.values()) {
                data.setPoints(wid, pool, 0);
            }
            source.sendSuccess(() -> Component.literal("Очки склада '" + wid + "' обнулены (все пулы)."), true);
            return 1;
        }
        WarehousePoolCategory pool = WarehousePoolCategory.byId(category);
        if (pool == null) {
            source.sendFailure(Component.literal("Неизвестный пул: " + category
                    + ". Используй weapon, supply, equipment, raw, special или all."));
            return 0;
        }
        data.setPoints(wid, pool, 0);
        source.sendSuccess(() -> Component.literal("Очки пула " + pool.id()
                + " склада '" + wid + "' обнулены."), true);
        return 1;
    }

    private static int getPoints(CommandSourceStack source, String id) {
        String wid = WarehouseItemRegistry.sanitizeId(id);
        WarehouseSavedData data = WarehouseSavedData.get(source.getServer());
        StringBuilder sb = new StringBuilder("Очки склада '" + wid + "': ");
        for (WarehousePoolCategory pool : WarehousePoolCategory.values()) {
            sb.append(pool.id()).append('=').append(data.getPoints(wid, pool)).append("  ");
        }
        source.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
        return 1;
    }

    // ---------------------------------------------------------------- каталог предметов

    /** Известные ключи key=value-хвоста команды additem (для подсказок и валидации). */
    private static final List<String> MODIFIER_KEYS = List.of(
            "name", "quantity", "category", "maxwithdraw", "refund",
            "roles", "teams", "rank", "rolelock", "lockmode", "id");

    /**
     * Захватывает предмет из руки игрока (полный NBT/TACZ) и добавляет его в каталог склада.
     * Позиционные {@code quantity}/{@code category} задают значения по умолчанию, а необязательный
     * {@code key=value}-хвост ({@code rawTail}) переопределяет их и добавляет остальные модификаторы.
     */
    private static int addItem(CommandSourceStack source, String poolId, int cost, int quantity,
                               @Nullable String category, @Nullable String rawTail) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) return 0;
        WarehousePoolCategory pool = WarehousePoolCategory.byId(poolId);
        if (pool == null) {
            source.sendFailure(Component.literal("Неизвестный пул: " + poolId
                    + ". Используй weapon, supply, equipment, raw или special."));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("Возьми предмет в основную руку, прежде чем добавлять его."));
            return 0;
        }

        // Разбор key=value-хвоста; null означает ошибку парсинга (сообщение уже отправлено).
        WarehouseItemRegistry.ItemModifiers mods = parseModifiers(source, quantity, category, rawTail);
        if (mods == null) return 0;

        WarehouseItemRegistry registry = WarehouseItemRegistry.get();
        String id = registry.captureAndAdd(source.getServer(), held, pool, cost, mods);
        if (id == null) {
            source.sendFailure(Component.literal("Не удалось добавить предмет в склад (см. лог)."));
            return 0;
        }
        int qty = mods.quantity();
        source.sendSuccess(() -> Component.literal("Предмет '" + id + "' добавлен (pool=" + pool.id()
                + ", cost=" + cost + ", quantity=" + qty + "). Всего предметов: " + registry.size()), true);
        source.sendSuccess(() -> Component.literal("Файл: " + registry.configPath()), false);
        return 1;
    }

    /**
     * Собирает {@link WarehouseItemRegistry.ItemModifiers} из позиционных значений и key=value-хвоста.
     * Ключи имеют приоритет над позиционными аргументами. При ошибке отправляет sendFailure и возвращает {@code null}.
     */
    @Nullable
    private static WarehouseItemRegistry.ItemModifiers parseModifiers(CommandSourceStack source,
                                                                      int positionalQuantity,
                                                                      @Nullable String positionalCategory,
                                                                      @Nullable String rawTail) {
        // Защита от опечатки: модификатор, поставленный на место позиционной категории
        // (additem supply 1 3 refund=1), Brigadier поглотит как category — подсказываем.
        if (positionalCategory != null && positionalCategory.contains("=")) {
            source.sendFailure(Component.literal("'" + positionalCategory
                    + "' выглядит как модификатор, а не категория. Укажи категорию словом либо задай category=…"));
            return null;
        }
        // Стартуем с позиционных значений; key=value их переопределит.
        String id = null;
        String name = null;
        int quantity = Math.max(1, positionalQuantity);
        String category = positionalCategory;
        int maxWithdraw = 0; // 0 → дефолт (16) на стороне реестра
        Integer refund = null;
        List<String> roles = List.of();
        List<String> teams = List.of();
        String rank = null;
        Boolean roleLock = null;
        String lockMode = null;

        if (rawTail != null && !rawTail.isBlank()) {
            for (String token : tokenize(rawTail)) {
                int eq = token.indexOf('=');
                if (eq <= 0) {
                    source.sendFailure(Component.literal("Модификатор '" + token
                            + "' должен быть в виде ключ=значение. Доступно: " + String.join(", ", MODIFIER_KEYS)));
                    return null;
                }
                String key = token.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String value = token.substring(eq + 1).trim();
                try {
                    switch (key) {
                        case "name", "displayname" -> name = value;
                        case "id" -> id = value;
                        case "quantity", "qty" -> quantity = Math.max(1, Integer.parseInt(value));
                        case "category", "cat" -> category = value;
                        case "maxwithdraw", "max", "maxperwithdraw" -> maxWithdraw = Math.max(1, Integer.parseInt(value));
                        case "refund", "refundvalue" -> refund = Math.max(0, Integer.parseInt(value));
                        case "roles", "role" -> roles = splitList(value);
                        case "teams", "team" -> teams = splitList(value);
                        case "rank", "minrank" -> rank = value;
                        case "rolelock", "rolelocked" -> roleLock = parseBool(value);
                        case "lockmode", "lock" -> lockMode = value.toLowerCase(Locale.ROOT);
                        default -> {
                            source.sendFailure(Component.literal("Неизвестный ключ '" + key
                                    + "'. Доступно: " + String.join(", ", MODIFIER_KEYS)));
                            return null;
                        }
                    }
                } catch (NumberFormatException e) {
                    source.sendFailure(Component.literal("Ключ '" + key + "' требует число, получено '" + value + "'."));
                    return null;
                }
            }
        }

        if (lockMode != null && !lockMode.equals("auto") && !lockMode.equals("use") && !lockMode.equals("hold")) {
            source.sendFailure(Component.literal("lockmode должен быть auto, use или hold (получено '" + lockMode + "')."));
            return null;
        }

        return new WarehouseItemRegistry.ItemModifiers(id, name, quantity, category, maxWithdraw,
                refund, roles, teams, rank, roleLock, lockMode);
    }

    /** Разбивает строку-хвост на токены по пробелам, сохраняя значения в двойных кавычках целиком. */
    private static List<String> tokenize(String raw) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        boolean started = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                started = true;
            } else if (c == ' ' && !inQuote) {
                if (started) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                    started = false;
                }
            } else {
                cur.append(c);
                started = true;
            }
        }
        if (started) tokens.add(cur.toString());
        return tokens;
    }

    /** Список через запятую → нормализованный список без пустых элементов. */
    private static List<String> splitList(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private static boolean parseBool(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("да");
    }

    /** Удаляет предмет из каталога склада по id (только записи из items.json). */
    private static int removeItem(CommandSourceStack source, String rawId) {
        WarehouseItemRegistry registry = WarehouseItemRegistry.get();
        String id = WarehouseItemRegistry.sanitizeId(rawId);
        boolean removed = registry.removeAndSave(id);
        if (!removed) {
            source.sendFailure(Component.literal("Предмет '" + id
                    + "' не найден в items.json (legacy-файлы items/ удаляются вручную)."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Предмет '" + id
                + "' удалён из каталога. Осталось предметов: " + registry.size()), true);
        source.sendSuccess(() -> Component.literal("Файл: " + registry.configPath()), false);
        return 1;
    }

    // ---------------------------------------------------------------- личная квота

    private static int budgetGet(CommandSourceStack source, ServerPlayer target) {
        WarehouseBudgetLimits.Limit limit = WarehouseBudgetLimits.forPlayer(target);
        String name = target.getGameProfile().getName();
        if (limit.unlimited()) {
            source.sendSuccess(() -> Component.literal("Квота игрока " + name + ": безлимит (OP или ранг)."), false);
            return 1;
        }
        int cur = WarehousePersonalBudgetSavedData.get(source.getServer())
                .getBudget(source.getServer(), target.getUUID(), limit.max(), limit.regenPerHour());
        source.sendSuccess(() -> Component.literal("Квота игрока " + name + ": " + cur + "/" + limit.max()
                + " (реген " + limit.regenPerHour() + "/ч)."), false);
        return 1;
    }

    private static int budgetFull(CommandSourceStack source, ServerPlayer target) {
        WarehouseBudgetLimits.Limit limit = WarehouseBudgetLimits.forPlayer(target);
        String name = target.getGameProfile().getName();
        if (limit.unlimited()) {
            source.sendFailure(Component.literal("У игрока " + name + " безлимитная квота — восполнять нечего."));
            return 0;
        }
        WarehousePersonalBudgetSavedData.get(source.getServer())
                .fill(source.getServer(), target.getUUID(), limit.max(), limit.regenPerHour());
        source.sendSuccess(() -> Component.literal("Квота игрока " + name + " восполнена до " + limit.max() + "."), true);
        return 1;
    }

    private static int budgetSet(CommandSourceStack source, ServerPlayer target, int amount) {
        WarehouseBudgetLimits.Limit limit = WarehouseBudgetLimits.forPlayer(target);
        String name = target.getGameProfile().getName();
        if (limit.unlimited()) {
            source.sendFailure(Component.literal("У игрока " + name + " безлимитная квота — значение не применяется."));
            return 0;
        }
        WarehousePersonalBudgetSavedData.get(source.getServer())
                .setBalance(source.getServer(), target.getUUID(), amount, limit.max(), limit.regenPerHour());
        int cur = WarehousePersonalBudgetSavedData.get(source.getServer())
                .getBudget(source.getServer(), target.getUUID(), limit.max(), limit.regenPerHour());
        source.sendSuccess(() -> Component.literal("Квота игрока " + name + " выставлена: " + cur + "/" + limit.max() + "."), true);
        return 1;
    }

    private static int budgetAdd(CommandSourceStack source, ServerPlayer target, int amount) {
        WarehouseBudgetLimits.Limit limit = WarehouseBudgetLimits.forPlayer(target);
        String name = target.getGameProfile().getName();
        if (limit.unlimited()) {
            source.sendFailure(Component.literal("У игрока " + name + " безлимитная квота — добавлять нечего."));
            return 0;
        }
        WarehousePersonalBudgetSavedData.get(source.getServer())
                .refund(source.getServer(), target.getUUID(), amount, limit.max(), limit.regenPerHour());
        int cur = WarehousePersonalBudgetSavedData.get(source.getServer())
                .getBudget(source.getServer(), target.getUUID(), limit.max(), limit.regenPerHour());
        source.sendSuccess(() -> Component.literal("Квота игрока " + name + " пополнена на " + amount
                + " → " + cur + "/" + limit.max() + "."), true);
        return 1;
    }

    // ---------------------------------------------------------------- NPC

    private static int spawnNpc(CommandSourceStack source, String warehouseId) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) return 0;
        String wid = WarehouseItemRegistry.sanitizeId(warehouseId);
        WarehouseSavedData.get(source.getServer()).createWarehouse(wid);

        ServerLevel level = player.serverLevel();
        QuartermasterEntity npc = PjmEntities.QUARTERMASTER.get().create(level);
        if (npc == null) {
            source.sendFailure(Component.literal("Не удалось создать NPC."));
            return 0;
        }
        float yaw = player.getYRot();
        npc.moveTo(player.getX(), player.getY(), player.getZ(), yaw, 0F);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setWarehouseId(wid);
        npc.setSkinId(QuartermasterEntity.DEFAULT_SKIN);
        level.addFreshEntity(npc);
        source.sendSuccess(() -> Component.literal("NPC-кладовщик склада '" + wid + "' заспавнен."), true);
        return 1;
    }

    private static int npcSkin(CommandSourceStack source, String skinId) {
        QuartermasterEntity npc = requireNearestNpc(source);
        if (npc == null) return 0;
        npc.setSkinId(skinId);
        source.sendSuccess(() -> Component.literal("Скин NPC изменён на '" + npc.getSkinId() + "'."), true);
        return 1;
    }

    private static int npcTeam(CommandSourceStack source, String team) {
        QuartermasterEntity npc = requireNearestNpc(source);
        if (npc == null) return 0;
        String value = team.equalsIgnoreCase("none") || team.equalsIgnoreCase("all") ? "" : team;
        npc.setTeamRestriction(value);
        source.sendSuccess(() -> Component.literal(value.isBlank()
                ? "NPC доступен всем командам."
                : "NPC ограничен командой '" + value + "'."), true);
        return 1;
    }

    private static int npcWarehouse(CommandSourceStack source, String id) {
        QuartermasterEntity npc = requireNearestNpc(source);
        if (npc == null) return 0;
        String wid = WarehouseItemRegistry.sanitizeId(id);
        WarehouseSavedData.get(source.getServer()).createWarehouse(wid);
        npc.setWarehouseId(wid);
        source.sendSuccess(() -> Component.literal("NPC привязан к складу '" + wid + "'."), true);
        return 1;
    }

    private static int npcLimit(CommandSourceStack source, int amount) {
        QuartermasterEntity npc = requireNearestNpc(source);
        if (npc == null) return 0;
        npc.setWithdrawLimit(amount);
        source.sendSuccess(() -> Component.literal(amount == 0
                ? "Лимит выдачи NPC снят (берётся из предмета)."
                : "Лимит выдачи NPC за раз: " + amount + "."), true);
        return 1;
    }

    private static int npcCooldown(CommandSourceStack source, int ticks) {
        QuartermasterEntity npc = requireNearestNpc(source);
        if (npc == null) return 0;
        npc.setCooldownTicks(ticks);
        source.sendSuccess(() -> Component.literal("Задержка выдачи NPC: " + ticks + " тиков."), true);
        return 1;
    }

    private static int npcCategories(CommandSourceStack source, String list) {
        QuartermasterEntity npc = requireNearestNpc(source);
        if (npc == null) return 0;
        if (list.equalsIgnoreCase("all") || list.isBlank()) {
            npc.setAllowedCategories(List.of());
            source.sendSuccess(() -> Component.literal("NPC выдаёт все категории."), true);
            return 1;
        }
        List<String> categories = Arrays.stream(list.split("[,\\s]+")).filter(s -> !s.isBlank()).toList();
        npc.setAllowedCategories(categories);
        source.sendSuccess(() -> Component.literal("NPC выдаёт категории: " + String.join(", ", npc.getAllowedCategories())), true);
        return 1;
    }

    // ---------------------------------------------------------------- утилиты

    @Nullable
    private static QuartermasterEntity requireNearestNpc(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) return null;
        QuartermasterEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (QuartermasterEntity npc : player.serverLevel().getEntitiesOfClass(QuartermasterEntity.class,
                player.getBoundingBox().inflate(NPC_SEARCH_RADIUS), e -> !e.isRemoved())) {
            double dist = npc.distanceToSqr(player);
            if (dist < bestDist) {
                best = npc;
                bestDist = dist;
            }
        }
        if (best == null) {
            source.sendFailure(Component.literal("Рядом нет NPC-кладовщика (в радиусе " + NPC_SEARCH_RADIUS + " блоков)."));
        }
        return best;
    }

    @Nullable
    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) return player;
        source.sendFailure(Component.literal("Команда доступна только игроку."));
        return null;
    }

    private static String formatPos(WarehouseSettings settings) {
        return settings.receptionPos() == null ? "—"
                : settings.receptionPos().getX() + " " + settings.receptionPos().getY() + " " + settings.receptionPos().getZ();
    }

    private static CompletableFuture<Suggestions> suggestWarehouses(CommandSourceStack source, SuggestionsBuilder builder) {
        for (String id : WarehouseSavedData.get(source.getServer()).ids()) {
            builder.suggest(id);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestItems(SuggestionsBuilder builder) {
        for (var def : WarehouseItemRegistry.get().all()) {
            builder.suggest(def.id());
        }
        return builder.buildFuture();
    }

    /** Подсказывает имена ключей модификаторов (в форме {@code ключ=}) для последнего слова хвоста. */
    private static CompletableFuture<Suggestions> suggestModifierKeys(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        String word = lastSpace < 0 ? remaining : remaining.substring(lastSpace + 1);
        // Подсказки прикрепляются к началу текущего (незавершённого) слова.
        SuggestionsBuilder scoped = builder.createOffset(builder.getStart() + (lastSpace + 1));
        for (String key : MODIFIER_KEYS) {
            if (key.startsWith(word.toLowerCase(Locale.ROOT))) {
                scoped.suggest(key + "=");
            }
        }
        return scoped.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPools(SuggestionsBuilder builder) {
        for (WarehousePoolCategory pool : WarehousePoolCategory.values()) {
            builder.suggest(pool.id());
        }
        return builder.buildFuture();
    }

    /** Подсказки пулов + спец-значение {@code all} (для set/clear по всем пулам сразу). */
    private static CompletableFuture<Suggestions> suggestPoolsWithAll(SuggestionsBuilder builder) {
        builder.suggest("all");
        for (WarehousePoolCategory pool : WarehousePoolCategory.values()) {
            builder.suggest(pool.id());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestSkins(SuggestionsBuilder builder) {
        for (String skin : KNOWN_SKINS) {
            builder.suggest(skin);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestTeams(SuggestionsBuilder builder) {
        builder.suggest("none");
        for (var team : Teams.all()) {
            builder.suggest(team.id());
        }
        return builder.buildFuture();
    }
}
