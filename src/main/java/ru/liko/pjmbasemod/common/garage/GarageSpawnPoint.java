package ru.liko.pjmbasemod.common.garage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Одна точка спавна техники терминала гаража: позиция блока + направление (yaw). */
public record GarageSpawnPoint(BlockPos pos, float yaw) {

    public GarageSpawnPoint {
        pos = pos.immutable();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        tag.putFloat("Yaw", yaw);
        return tag;
    }

    public static GarageSpawnPoint load(CompoundTag tag) {
        return new GarageSpawnPoint(
                new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")),
                tag.getFloat("Yaw"));
    }
}
