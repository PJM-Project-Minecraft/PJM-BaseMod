package ru.liko.pjmbasemod.common.warehouse;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Снимок состояния склада для отправки клиенту и отрисовки в GUI.
 * Содержит баланс очков по пулам и список доступных через NPC предметов.
 */
public record WarehouseSnapshot(String warehouseId, Map<WarehousePoolCategory, Integer> points,
                                List<ItemEntry> items, boolean canWithdraw) {

    /** Выдаваемый предмет + рассчитанная для текущего склада доступность. */
    public record ItemEntry(String defId, String displayName, String itemId, String displayCategory,
                            WarehousePoolCategory pool, int pointCost, int maxPerWithdraw,
                            int refundValue, int inventoryCount,
                            int availablePoints, boolean affordable,
                            boolean roleAllowed, List<String> allowedRoles) {

        /** Принимается ли предмет складом в обмен на очки. */
        public boolean depositable() { return refundValue > 0; }
    }

    public static void write(FriendlyByteBuf buf, WarehouseSnapshot snapshot) {
        buf.writeUtf(snapshot.warehouseId());
        buf.writeBoolean(snapshot.canWithdraw());

        for (WarehousePoolCategory pool : WarehousePoolCategory.values()) {
            buf.writeVarInt(snapshot.points().getOrDefault(pool, 0));
        }

        buf.writeVarInt(snapshot.items().size());
        for (ItemEntry item : snapshot.items()) {
            buf.writeUtf(item.defId());
            buf.writeUtf(item.displayName());
            buf.writeUtf(item.itemId());
            buf.writeUtf(item.displayCategory());
            buf.writeEnum(item.pool());
            buf.writeVarInt(item.pointCost());
            buf.writeVarInt(item.maxPerWithdraw());
            buf.writeVarInt(item.refundValue());
            buf.writeVarInt(item.inventoryCount());
            buf.writeVarInt(item.availablePoints());
            buf.writeBoolean(item.affordable());
            buf.writeBoolean(item.roleAllowed());
            buf.writeVarInt(item.allowedRoles().size());
            for (String role : item.allowedRoles()) {
                buf.writeUtf(role);
            }
        }
    }

    public static WarehouseSnapshot read(FriendlyByteBuf buf) {
        String warehouseId = buf.readUtf();
        boolean canWithdraw = buf.readBoolean();

        EnumMap<WarehousePoolCategory, Integer> points = new EnumMap<>(WarehousePoolCategory.class);
        for (WarehousePoolCategory pool : WarehousePoolCategory.values()) {
            points.put(pool, buf.readVarInt());
        }

        int count = buf.readVarInt();
        List<ItemEntry> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String defId = buf.readUtf();
            String displayName = buf.readUtf();
            String itemId = buf.readUtf();
            String displayCategory = buf.readUtf();
            WarehousePoolCategory pool = buf.readEnum(WarehousePoolCategory.class);
            int pointCost = buf.readVarInt();
            int maxPerWithdraw = buf.readVarInt();
            int refundValue = buf.readVarInt();
            int inventoryCount = buf.readVarInt();
            int availablePoints = buf.readVarInt();
            boolean affordable = buf.readBoolean();
            boolean roleAllowed = buf.readBoolean();
            int roleCount = buf.readVarInt();
            List<String> allowedRoles = new ArrayList<>(roleCount);
            for (int j = 0; j < roleCount; j++) {
                allowedRoles.add(buf.readUtf());
            }
            items.add(new ItemEntry(defId, displayName, itemId, displayCategory, pool,
                    pointCost, maxPerWithdraw, refundValue, inventoryCount, availablePoints, affordable,
                    roleAllowed, List.copyOf(allowedRoles)));
        }

        return new WarehouseSnapshot(warehouseId, points, List.copyOf(items), canWithdraw);
    }
}
