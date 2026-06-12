package ru.liko.pjmbasemod.common.warehouse;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Настройки зоны приёма поставок одного именованного склада.
 */
public record WarehouseSettings(String warehouseId, String dimension,
                                @Nullable BlockPos receptionPos, int receptionRadius) {

    public static final int DEFAULT_RECEPTION_RADIUS = 4;

    public static WarehouseSettings empty(String warehouseId) {
        return new WarehouseSettings(warehouseId, "", null, DEFAULT_RECEPTION_RADIUS);
    }

    public WarehouseSettings withReception(ResourceKey<Level> dim, BlockPos pos) {
        return new WarehouseSettings(warehouseId, dim.location().toString(), pos.immutable(), receptionRadius);
    }

    public WarehouseSettings withRadius(int radius) {
        return new WarehouseSettings(warehouseId, dimension, receptionPos, Math.max(1, radius));
    }

    public boolean hasReception() {
        return receptionPos != null && !dimension.isBlank();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", warehouseId);
        tag.putString("Dimension", dimension);
        if (receptionPos != null) {
            tag.putInt("X", receptionPos.getX());
            tag.putInt("Y", receptionPos.getY());
            tag.putInt("Z", receptionPos.getZ());
            tag.putBoolean("HasReception", true);
        }
        tag.putInt("Radius", receptionRadius);
        return tag;
    }

    public static WarehouseSettings load(CompoundTag tag) {
        String id = tag.getString("Id");
        String dimension = tag.getString("Dimension");
        BlockPos pos = tag.getBoolean("HasReception")
                ? new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"))
                : null;
        int radius = tag.contains("Radius") ? tag.getInt("Radius") : DEFAULT_RECEPTION_RADIUS;
        return new WarehouseSettings(id, dimension, pos, Math.max(1, radius));
    }
}
