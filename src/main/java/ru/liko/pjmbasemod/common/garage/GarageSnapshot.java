package ru.liko.pjmbasemod.common.garage;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Снимок состояния гаража для отправки клиенту и отрисовки в GUI.
 * Содержит каталог определений, экземпляры в гараже игрока и его права.
 */
public record GarageSnapshot(List<DefEntry> definitions, List<InstanceEntry> instances,
                             boolean canCraft, boolean canSpawn, boolean canStore) {

    /** Определение каталога + рассчитанная для игрока доступность по ресурсам. */
    /**
     * @param pendingCount   сколько таких машин сейчас в сборке
     * @param pendingSeconds секунд до готовности ближайшей (0, если сборок нет)
     */
    public record DefEntry(String id, String displayName, String entityType, String iconItem, String category,
                           int assemblyTime, List<CostView> cost, boolean affordable,
                           boolean roleAllowed, List<String> allowedRoles,
                           boolean rankAllowed, String requiredRankName,
                           int pendingCount, int pendingSeconds, CompoundTag previewNbt) {}

    public record CostView(String item, int count, boolean enough) {}

    public record InstanceEntry(UUID instanceId, String defId, String displayName,
                                String entityType, CompoundTag entityNbt,
                                boolean roleAllowed, List<String> allowedRoles,
                                boolean rankAllowed, String requiredRankName) {}

    public static void write(FriendlyByteBuf buf, GarageSnapshot snapshot) {
        buf.writeBoolean(snapshot.canCraft());
        buf.writeBoolean(snapshot.canSpawn());
        buf.writeBoolean(snapshot.canStore());

        buf.writeVarInt(snapshot.definitions().size());
        for (DefEntry def : snapshot.definitions()) {
            buf.writeUtf(def.id());
            buf.writeUtf(def.displayName());
            buf.writeUtf(def.entityType());
            buf.writeUtf(def.iconItem());
            buf.writeUtf(def.category());
            buf.writeInt(def.assemblyTime());
            buf.writeBoolean(def.affordable());
            buf.writeBoolean(def.roleAllowed());
            buf.writeVarInt(def.allowedRoles().size());
            for (String role : def.allowedRoles()) {
                buf.writeUtf(role);
            }
            buf.writeBoolean(def.rankAllowed());
            buf.writeUtf(def.requiredRankName());
            buf.writeVarInt(def.pendingCount());
            buf.writeVarInt(def.pendingSeconds());
            buf.writeNbt(def.previewNbt());
            buf.writeVarInt(def.cost().size());
            for (CostView cost : def.cost()) {
                buf.writeUtf(cost.item());
                buf.writeVarInt(cost.count());
                buf.writeBoolean(cost.enough());
            }
        }

        buf.writeVarInt(snapshot.instances().size());
        for (InstanceEntry inst : snapshot.instances()) {
            buf.writeUUID(inst.instanceId());
            buf.writeUtf(inst.defId());
            buf.writeUtf(inst.displayName());
            buf.writeUtf(inst.entityType());
            buf.writeNbt(inst.entityNbt());
            buf.writeBoolean(inst.roleAllowed());
            buf.writeVarInt(inst.allowedRoles().size());
            for (String role : inst.allowedRoles()) {
                buf.writeUtf(role);
            }
            buf.writeBoolean(inst.rankAllowed());
            buf.writeUtf(inst.requiredRankName());
        }
    }

    public static GarageSnapshot read(FriendlyByteBuf buf) {
        boolean canCraft = buf.readBoolean();
        boolean canSpawn = buf.readBoolean();
        boolean canStore = buf.readBoolean();

        int defCount = buf.readVarInt();
        List<DefEntry> defs = new ArrayList<>(defCount);
        for (int i = 0; i < defCount; i++) {
            String id = buf.readUtf();
            String displayName = buf.readUtf();
            String entityType = buf.readUtf();
            String icon = buf.readUtf();
            String category = buf.readUtf();
            int assemblyTime = buf.readInt();
            boolean affordable = buf.readBoolean();
            boolean roleAllowed = buf.readBoolean();
            int roleCount = buf.readVarInt();
            List<String> allowedRoles = new ArrayList<>(roleCount);
            for (int j = 0; j < roleCount; j++) {
                allowedRoles.add(buf.readUtf());
            }
            boolean rankAllowed = buf.readBoolean();
            String requiredRankName = buf.readUtf();
            int pendingCount = buf.readVarInt();
            int pendingSeconds = buf.readVarInt();
            CompoundTag previewNbt = buf.readNbt();
            int costCount = buf.readVarInt();
            List<CostView> cost = new ArrayList<>(costCount);
            for (int j = 0; j < costCount; j++) {
                cost.add(new CostView(buf.readUtf(), buf.readVarInt(), buf.readBoolean()));
            }
            defs.add(new DefEntry(id, displayName, entityType, icon, category, assemblyTime,
                    List.copyOf(cost), affordable, roleAllowed, List.copyOf(allowedRoles),
                    rankAllowed, requiredRankName, pendingCount, pendingSeconds,
                    previewNbt == null ? new CompoundTag() : previewNbt));
        }

        int instCount = buf.readVarInt();
        List<InstanceEntry> instances = new ArrayList<>(instCount);
        for (int i = 0; i < instCount; i++) {
            UUID instanceId = buf.readUUID();
            String defId = buf.readUtf();
            String displayName = buf.readUtf();
            String entityType = buf.readUtf();
            CompoundTag entityNbt = buf.readNbt();
            boolean roleAllowed = buf.readBoolean();
            int roleCount = buf.readVarInt();
            List<String> allowedRoles = new ArrayList<>(roleCount);
            for (int j = 0; j < roleCount; j++) {
                allowedRoles.add(buf.readUtf());
            }
            boolean rankAllowed = buf.readBoolean();
            String requiredRankName = buf.readUtf();
            instances.add(new InstanceEntry(instanceId, defId, displayName,
                    entityType, entityNbt == null ? new CompoundTag() : entityNbt,
                    roleAllowed, List.copyOf(allowedRoles), rankAllowed, requiredRankName));
        }

        return new GarageSnapshot(List.copyOf(defs), List.copyOf(instances), canCraft, canSpawn, canStore);
    }
}
