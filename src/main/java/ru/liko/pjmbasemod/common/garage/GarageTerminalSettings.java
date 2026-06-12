package ru.liko.pjmbasemod.common.garage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Настройки конкретного терминала гаража, сохранённые в мире.
 */
public record GarageTerminalSettings(UUID terminalId, String dimension, BlockPos terminalPos, float terminalYaw,
                                     @Nullable BlockPos spawnPos, float spawnYaw,
                                     @Nullable BlockPos storagePos, int storageRadius) {

    public static final int DEFAULT_STORAGE_RADIUS = 5;

    public static GarageTerminalSettings create(UUID terminalId, ResourceKey<Level> dimension,
                                                BlockPos terminalPos, float terminalYaw) {
        return new GarageTerminalSettings(terminalId, dimension.location().toString(), terminalPos, terminalYaw,
                null, terminalYaw, null, DEFAULT_STORAGE_RADIUS);
    }

    public static GarageTerminalSettings temporary(ResourceKey<Level> dimension, BlockPos pos, float yaw) {
        return new GarageTerminalSettings(UUID.randomUUID(), dimension.location().toString(), pos, yaw,
                pos.above(), yaw, pos, DEFAULT_STORAGE_RADIUS);
    }

    public GarageTerminalSettings withTerminal(ResourceKey<Level> dimension, BlockPos terminalPos, float terminalYaw) {
        return new GarageTerminalSettings(terminalId, dimension.location().toString(), terminalPos, terminalYaw,
                spawnPos, spawnYaw, storagePos, storageRadius);
    }

    public GarageTerminalSettings withSpawn(BlockPos pos, float yaw) {
        return new GarageTerminalSettings(terminalId, dimension, terminalPos, terminalYaw,
                pos.immutable(), yaw, storagePos, storageRadius);
    }

    public GarageTerminalSettings withSpawnYaw(float yaw) {
        return new GarageTerminalSettings(terminalId, dimension, terminalPos, terminalYaw,
                spawnPos, yaw, storagePos, storageRadius);
    }

    public GarageTerminalSettings withStorage(BlockPos pos) {
        return new GarageTerminalSettings(terminalId, dimension, terminalPos, terminalYaw,
                spawnPos, spawnYaw, pos.immutable(), storageRadius);
    }

    public GarageTerminalSettings withStorageRadius(int radius) {
        return new GarageTerminalSettings(terminalId, dimension, terminalPos, terminalYaw,
                spawnPos, spawnYaw, storagePos, Math.max(1, radius));
    }

    public BlockPos resolvedSpawnPos() {
        return spawnPos == null ? terminalPos.above() : spawnPos;
    }

    public BlockPos resolvedStoragePos() {
        return storagePos == null ? terminalPos : storagePos;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("TerminalId", terminalId);
        tag.putString("Dimension", dimension);
        writePos(tag, "Terminal", terminalPos);
        tag.putFloat("TerminalYaw", terminalYaw);
        if (spawnPos != null) {
            writePos(tag, "Spawn", spawnPos);
            tag.putBoolean("HasSpawn", true);
        }
        tag.putFloat("SpawnYaw", spawnYaw);
        if (storagePos != null) {
            writePos(tag, "Storage", storagePos);
            tag.putBoolean("HasStorage", true);
        }
        tag.putInt("StorageRadius", storageRadius);
        return tag;
    }

    public static GarageTerminalSettings load(CompoundTag tag) {
        UUID terminalId = tag.hasUUID("TerminalId") ? tag.getUUID("TerminalId") : UUID.randomUUID();
        String dimension = tag.getString("Dimension");
        BlockPos terminalPos = readPos(tag, "Terminal", BlockPos.ZERO);
        float terminalYaw = tag.getFloat("TerminalYaw");
        BlockPos spawnPos = tag.getBoolean("HasSpawn") ? readPos(tag, "Spawn", terminalPos.above()) : null;
        float spawnYaw = tag.contains("SpawnYaw") ? tag.getFloat("SpawnYaw") : terminalYaw;
        BlockPos storagePos = tag.getBoolean("HasStorage") ? readPos(tag, "Storage", terminalPos) : null;
        int storageRadius = tag.contains("StorageRadius") ? tag.getInt("StorageRadius") : DEFAULT_STORAGE_RADIUS;
        return new GarageTerminalSettings(terminalId, dimension, terminalPos, terminalYaw,
                spawnPos, spawnYaw, storagePos, Math.max(1, storageRadius));
    }

    private static void writePos(CompoundTag tag, String prefix, BlockPos pos) {
        tag.putInt(prefix + "X", pos.getX());
        tag.putInt(prefix + "Y", pos.getY());
        tag.putInt(prefix + "Z", pos.getZ());
    }

    private static BlockPos readPos(CompoundTag tag, String prefix, BlockPos fallback) {
        String x = prefix + "X";
        String y = prefix + "Y";
        String z = prefix + "Z";
        if (!tag.contains(x) || !tag.contains(y) || !tag.contains(z)) {
            return fallback;
        }
        return new BlockPos(tag.getInt(x), tag.getInt(y), tag.getInt(z));
    }
}
