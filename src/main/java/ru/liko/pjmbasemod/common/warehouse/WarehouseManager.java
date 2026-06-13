package ru.liko.pjmbasemod.common.warehouse;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import ru.liko.pjmbasemod.common.entity.QuartermasterEntity;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.item.SupplyCrateItem;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.OpenWarehousePacket;
import ru.liko.pjmbasemod.common.network.packet.WarehouseSyncPacket;
import ru.liko.pjmbasemod.common.rank.RankService;
import ru.liko.pjmbasemod.common.role.RoleService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверная логика системы склада: открытие GUI, выдача предметов за очки, приём ящиков.
 */
public final class WarehouseManager {

    /** Активная сессия игрока у конкретного NPC-кладовщика. */
    private record Session(String warehouseId, List<String> allowedCategories, int withdrawLimit, int cooldownTicks) {}

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_WITHDRAW = new ConcurrentHashMap<>();

    private WarehouseManager() {}

    // ---------------------------------------------------------------- открытие GUI

    public static void openWarehouse(ServerPlayer player, QuartermasterEntity npc) {
        if (!hasTeamAccess(player, npc)) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.no_access"), true);
            return;
        }
        String warehouseId = npc.getWarehouseId();
        if (warehouseId.isBlank()) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.npc_unbound"), true);
            return;
        }
        SESSIONS.put(player.getUUID(), sessionFor(npc));
        PjmNetworking.sendToPlayer(player, new OpenWarehousePacket(buildSnapshot(player, npc)));
    }

    private static void resync(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        PjmNetworking.sendToPlayer(player, new WarehouseSyncPacket(buildSnapshot(player, session)));
    }

    // ---------------------------------------------------------------- выдача

    public static void handleWithdraw(ServerPlayer player, String defId, int count) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (!WarehousePermissions.can(player, WarehousePermissions.WITHDRAW)) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.no_permission"), true);
            return;
        }

        WarehouseItemDefinition def = WarehouseItemRegistry.get().get(defId);
        if (def == null) {
            resync(player);
            return;
        }
        if (!allowsCategory(session, def.displayCategory())) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.category_blocked"), true);
            return;
        }
        if (!RoleService.hasAllowedRole(player, def.allowedRoles())) {
            player.displayClientMessage(RoleService.requiredRoleMessage(def.allowedRoles()), true);
            return;
        }
        if (!RankService.meetsMinRank(player, def.minRank())) {
            player.displayClientMessage(RankService.requiredRankMessage(def.minRank()), true);
            return;
        }

        if (def.createStack(1).isEmpty()) {
            resync(player);
            return;
        }

        // Кулдаун
        long now = player.serverLevel().getGameTime();
        if (session.cooldownTicks() > 0) {
            Long last = LAST_WITHDRAW.get(player.getUUID());
            if (last != null && now - last < session.cooldownTicks()) {
                long left = (session.cooldownTicks() - (now - last)) / 20L + 1L;
                player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.cooldown", left), true);
                return;
            }
        }

        int limit = def.maxPerWithdraw();
        if (session.withdrawLimit() > 0) {
            limit = Math.min(limit, session.withdrawLimit());
        }
        int amount = Math.max(1, Math.min(count, limit));

        int cost = def.pointCost() * amount;
        WarehouseSavedData stock = WarehouseSavedData.get(player.server);
        if (!stock.trySpend(session.warehouseId(), def.pool(), cost)) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.not_enough_points"), true);
            resync(player);
            return;
        }

        giveItem(player, def, amount);
        LAST_WITHDRAW.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.withdrawn",
                amount, def.displayName()), true);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS, 0.6F, 1.0F);
        resync(player);
    }

    // ---------------------------------------------------------------- сдача предметов

    /**
     * Сдаёт на склад до {@code count} предметов по id определения и возвращает игроку очки в пул.
     * Для предметов с прочностью начисление масштабируется по остатку прочности
     * (изношенный предмет даёт меньше очков), многоразовые (без прочности) — полную ставку.
     */
    public static void handleDeposit(ServerPlayer player, String defId, int count) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;

        WarehouseItemDefinition def = WarehouseItemRegistry.get().get(defId);
        if (def == null) {
            resync(player);
            return;
        }
        if (!def.depositable()) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.not_depositable"), true);
            return;
        }
        if (!allowsCategory(session, def.displayCategory())) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.category_blocked"), true);
            return;
        }
        if (def.createStack(1).isEmpty()) {
            resync(player);
            return;
        }

        int want = Math.max(1, count);
        int deposited = 0;
        int pointsGained = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && deposited < want; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!def.matchesStack(stack)) continue;
            while (!stack.isEmpty() && deposited < want) {
                pointsGained += pointsForDeposit(stack, def.refundValue());
                stack.shrink(1);
                deposited++;
            }
        }

        if (deposited <= 0) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.nothing_to_deposit"), true);
            return;
        }

        WarehouseSavedData stock = WarehouseSavedData.get(player.server);
        stock.createWarehouse(session.warehouseId());
        stock.addPoints(session.warehouseId(), def.pool(), pointsGained);

        player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.deposited",
                deposited, def.displayName(), pointsGained), true);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS, 0.6F, 0.8F);
        resync(player);
    }

    /** Очки за сдачу одной штуки с учётом прочности: для повреждаемых — пропорционально остатку. */
    private static int pointsForDeposit(ItemStack stack, int baseRefund) {
        if (baseRefund <= 0) return 0;
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            int max = stack.getMaxDamage();
            int remaining = max - stack.getDamageValue();
            if (remaining <= 0) return 0;
            return Math.max(0, Math.round(baseRefund * (remaining / (float) max)));
        }
        return baseRefund;
    }

    /** Сколько таких предметов сейчас в инвентаре игрока. */
    private static int countInInventory(ServerPlayer player, WarehouseItemDefinition def) {
        int total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (def.matchesStack(stack)) total += stack.getCount();
        }
        return total;
    }

    // ---------------------------------------------------------------- приём ящиков

    /** ПКМ ящиком по NPC: сдаёт весь стек в руке на склад, привязанный к NPC. */
    public static void handleCrateInteract(ServerPlayer player, QuartermasterEntity npc, InteractionHand hand) {
        if (!hasTeamAccess(player, npc)) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.no_access"), true);
            return;
        }
        String warehouseId = npc.getWarehouseId();
        if (warehouseId.isBlank()) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.npc_unbound"), true);
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof SupplyCrateItem crate)) return;

        int count = stack.getCount();
        int added = depositCrate(player.server, warehouseId, crate.crateId(), count);
        if (added <= 0) {
            player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.crate_unknown"), true);
            return;
        }
        stack.shrink(count);
        notifyDelivery(player.serverLevel(), player, added);
    }

    /**
     * Начисляет очки складу за {@code count} ящиков типа {@code crateId}.
     * @return количество начисленных очков (0 — ящик не настроен).
     */
    public static int depositCrate(net.minecraft.server.MinecraftServer server, String warehouseId,
                                   String crateId, int count) {
        CrateDefinition crate = CrateRegistry.get().get(crateId);
        if (crate == null || count <= 0) return 0;
        int total = crate.points() * count;
        WarehouseSavedData stock = WarehouseSavedData.get(server);
        stock.createWarehouse(warehouseId);
        stock.addPoints(warehouseId, crate.pool(), total);
        return total;
    }

    /** Поглощает брошенные ящики в зонах приёма всех складов. Вызывается из тик-хаба. */
    public static void scanReceptionZones(ServerLevel level) {
        WarehouseSettingsSavedData settings = WarehouseSettingsSavedData.get(level.getServer());
        String dimension = level.dimension().location().toString();
        for (WarehouseSettings s : settings.all()) {
            if (!s.hasReception() || !dimension.equals(s.dimension())) continue;
            net.minecraft.core.BlockPos center = s.receptionPos();
            double r = s.receptionRadius();
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(center).inflate(r);
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box,
                    e -> !e.isRemoved() && e.getItem().getItem() instanceof SupplyCrateItem);
            for (ItemEntity entity : items) {
                ItemStack stack = entity.getItem();
                if (!(stack.getItem() instanceof SupplyCrateItem crate)) continue;
                int count = stack.getCount();
                int added = depositCrate(level.getServer(), s.warehouseId(), crate.crateId(), count);
                if (added <= 0) continue;
                entity.discard();
                level.playSound(null, center, SoundEvents.ITEM_PICKUP,
                        SoundSource.BLOCKS, 0.7F, 1.2F);
                ServerPlayer thrower = entity.getOwner() instanceof ServerPlayer sp ? sp : null;
                if (thrower != null) {
                    notifyDelivery(level, thrower, added);
                }
            }
        }
    }

    // ---------------------------------------------------------------- подсветка зон приёма

    /** Шаг между частицами вдоль ребра куба зоны, в блоках. */
    private static final double EDGE_STEP = 0.5;
    /** Предел частиц на одно ребро — защита от спама на больших радиусах. */
    private static final int MAX_EDGE_STEPS = 48;
    /** Радиус, в котором должен находиться игрок, чтобы зона подсвечивалась. */
    private static final double RENDER_VIEW_DISTANCE = 48.0;

    /**
     * Рисует частицами контур куба зоны приёма каждого склада в этом измерении.
     * Куб строится той же формулой, что и зона подбора в {@link #scanReceptionZones},
     * поэтому подсветка точно совпадает с областью, где ящики засчитываются.
     * Вызывается из тик-хаба; отрисовка идёт только если рядом есть игрок.
     */
    public static void renderReceptionZones(ServerLevel level) {
        WarehouseSettingsSavedData settings = WarehouseSettingsSavedData.get(level.getServer());
        String dimension = level.dimension().location().toString();
        for (WarehouseSettings s : settings.all()) {
            if (!s.hasReception() || !dimension.equals(s.dimension())) continue;
            net.minecraft.core.BlockPos center = s.receptionPos();
            double r = s.receptionRadius();
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(center).inflate(r);
            net.minecraft.world.phys.Vec3 mid = box.getCenter();
            if (level.getNearestPlayer(mid.x, mid.y, mid.z, RENDER_VIEW_DISTANCE, false) == null) continue;
            drawBoxOutline(level, box);
        }
    }

    /** Рисует частицы вдоль 12 рёбер куба. */
    private static void drawBoxOutline(ServerLevel level, net.minecraft.world.phys.AABB box) {
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        for (double y : ys) for (double z : zs) drawEdge(level, box.minX, y, z, box.maxX, y, z);
        for (double x : xs) for (double z : zs) drawEdge(level, x, box.minY, z, x, box.maxY, z);
        for (double x : xs) for (double y : ys) drawEdge(level, x, y, box.minZ, x, y, box.maxZ);
    }

    /** Раскладывает частицы по прямой между двумя точками. */
    private static void drawEdge(ServerLevel level, double x1, double y1, double z1,
                                 double x2, double y2, double z2) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.min(MAX_EDGE_STEPS, Math.max(1, (int) Math.round(length / EDGE_STEP)));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            level.sendParticles(ParticleTypes.END_ROD,
                    x1 + dx * t, y1 + dy * t, z1 + dz * t,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // ---------------------------------------------------------------- снимок для GUI

    public static WarehouseSnapshot buildSnapshot(ServerPlayer player, QuartermasterEntity npc) {
        return buildSnapshot(player, sessionFor(npc));
    }

    private static WarehouseSnapshot buildSnapshot(ServerPlayer player, Session session) {
        WarehouseSavedData stock = WarehouseSavedData.get(player.server);
        EnumMap<WarehousePoolCategory, Integer> points = new EnumMap<>(WarehousePoolCategory.class);
        for (WarehousePoolCategory pool : WarehousePoolCategory.values()) {
            points.put(pool, stock.getPoints(session.warehouseId(), pool));
        }

        List<WarehouseSnapshot.ItemEntry> items = new ArrayList<>();
        for (WarehouseItemDefinition def : WarehouseItemRegistry.get().all()) {
            if (!allowsCategory(session, def.displayCategory())) continue;
            int available = points.getOrDefault(def.pool(), 0);
            boolean affordable = available >= def.pointCost();
            int inInventory = countInInventory(player, def);
            boolean roleAllowed = RoleService.hasAllowedRole(player, def.allowedRoles());
            boolean rankAllowed = RankService.meetsMinRank(player, def.minRank());
            String requiredRankName = RankService.rankDisplayName(def.minRank());
            items.add(new WarehouseSnapshot.ItemEntry(def.id(), def.displayName(), def.itemIdString(),
                    def.displayCategory(), def.pool(), def.pointCost(), def.maxPerWithdraw(),
                    def.refundValue(), inInventory, available, affordable,
                    roleAllowed, def.allowedRoles(), rankAllowed, requiredRankName));
        }

        boolean canWithdraw = WarehousePermissions.can(player, WarehousePermissions.WITHDRAW);
        return new WarehouseSnapshot(session.warehouseId(), points, List.copyOf(items), canWithdraw);
    }

    // ---------------------------------------------------------------- утилиты

    private static Session sessionFor(QuartermasterEntity npc) {
        return new Session(npc.getWarehouseId(), npc.getAllowedCategories(),
                npc.getWithdrawLimit(), npc.getCooldownTicks());
    }

    private static boolean allowsCategory(Session session, String displayCategory) {
        return session.allowedCategories().isEmpty()
                || session.allowedCategories().contains(displayCategory.toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean hasTeamAccess(ServerPlayer player, QuartermasterEntity npc) {
        String restriction = npc.getTeamRestriction();
        if (restriction == null || restriction.isBlank()) return true;
        String playerTeam = FrontlineTeams.resolvePlayerTeamId(player);
        return restriction.equalsIgnoreCase(playerTeam);
    }

    private static void giveItem(ServerPlayer player, WarehouseItemDefinition def, int amount) {
        int remaining = amount;
        int maxStack = Math.max(1, def.createStack(1).getMaxStackSize());
        while (remaining > 0) {
            int take = Math.min(maxStack, remaining);
            ItemStack stack = def.createStack(take);
            if (stack.isEmpty()) return;
            player.getInventory().placeItemBackInInventory(stack);
            remaining -= take;
        }
    }

    private static void notifyDelivery(ServerLevel level, ServerPlayer player, int points) {
        player.displayClientMessage(Component.translatable("gui.pjmbasemod.warehouse.delivery_accepted", points), true);
        level.playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
    }
}
