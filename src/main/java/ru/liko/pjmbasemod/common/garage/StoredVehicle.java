package ru.liko.pjmbasemod.common.garage;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Сохранённый в гараже экземпляр техники: ссылка на определение каталога +
 * полный NBT-снимок сущности (с состоянием/HP). Сущность в мире при этом не существует.
 */
public record StoredVehicle(UUID instanceId, String defId, String customName, CompoundTag entityNbt) {

    public static StoredVehicle create(String defId, String displayName, CompoundTag entityNbt) {
        return new StoredVehicle(UUID.randomUUID(), defId, displayName, entityNbt);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("InstanceId", instanceId);
        tag.putString("DefId", defId);
        tag.putString("CustomName", customName == null ? "" : customName);
        tag.put("Entity", entityNbt);
        return tag;
    }

    public static StoredVehicle load(CompoundTag tag) {
        return new StoredVehicle(
                tag.hasUUID("InstanceId") ? tag.getUUID("InstanceId") : UUID.randomUUID(),
                tag.getString("DefId"),
                tag.getString("CustomName"),
                tag.getCompound("Entity"));
    }
}
