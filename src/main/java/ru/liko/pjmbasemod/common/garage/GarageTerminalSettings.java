package ru.liko.pjmbasemod.common.garage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Настройки конкретного терминала гаража, сохранённые в мире.
 *
 * <p>Терминал хранит упорядоченный список точек спавна техники ({@link GarageSpawnPoint}).
 * Пустой список означает дефолтную точку над терминалом.</p>
 */
public record GarageTerminalSettings(UUID terminalId, String dimension, BlockPos terminalPos, float terminalYaw,
                                     List<GarageSpawnPoint> spawnPoints,
                                     @Nullable BlockPos storagePos, int storageRadius) {

    public static final int DEFAULT_STORAGE_RADIUS = 5;

    public GarageTerminalSettings {
        spawnPoints = spawnPoints == null ? List.of() : List.copyOf(spawnPoints);
    }

    public static GarageTerminalSettings create(UUID terminalId, ResourceKey<Level> dimension,
                                                BlockPos terminalPos, float terminalYaw) {
        return new GarageTerminalSettings(terminalId, dimension.location().toString(), terminalPos, terminalYaw,
                List.of(), null, DEFAULT_STORAGE_RADIUS);
    }

    public static GarageTerminalSettings temporary(ResourceKey<Level> dimension, BlockPos pos, float yaw) {
        return new GarageTerminalSettings(UUID.randomUUID(), dimension.location().toString(), pos, yaw,
                List.of(new GarageSpawnPoint(pos.above(), yaw)), pos, DEFAULT_STORAGE_RADIUS);
    }

    public GarageTerminalSettings withTerminal(ResourceKey<Level> dimension, BlockPos terminalPos, float terminalYaw) {
        return new GarageTerminalSettings(terminalId, dimension.location().toString(), terminalPos, terminalYaw,
                spawnPoints, storagePos, storageRadius);
    }

    /** Заменяет весь список единственной точкой (команда «set spawn»). */
    public GarageTerminalSettings withPrimarySpawn(BlockPos pos, float yaw) {
        return new GarageTerminalSettings(terminalId, dimension, terminalPos, terminalYaw,
                List.of(new GarageSpawnPoint(pos, yaw)), storagePos, storageRadius);
    }

    /** Меняет направление первой точки; если точек нет — создаёт одну над терминалом (команда «set facing»). */
    public GarageTerminalSettings withFirstSpawnYaw(float yaw) {
        List<GarageSpawnPoint> next = new ArrayList<>(spawnPoints);
        if (next.isEmpty()) {
            next.add(new GarageSpawnPoint(terminalPos.above(), yaw));
        } else {
            next.set(0, new GarageSpawnPoint(next.get(0).pos(), yaw));
        }
        return new GarageTerminalSettings(terminalId, dimension, terminalPos, terminalYaw,
                next, storagePos, storageRadius);
    }

    /** Добавляет точку спавна в конец списка. */
    public GarageTerminalSettings withAddedSpawn(BlockPos pos, float yaw) {
        List<GarageSpawnPoint> next = new ArrayList<>(spawnPoints);
        next.add(new GarageSpawnPoint(pos, yaw));
        return new GarageTerminalSettings(terminalId, dimension, terminalPos, terminalYaw,
                next, storagePos, storageRadius);
    }

    /** Удаляет точку по индексу (0-based). Вне диапазона — возвращает себя без изменений. */
    public GarageTerminalSettings withoutSpawn(int index) {
        if (index < 0 || index >= spawnPoints.size()) return this;
        List<GarageSpawnPoint> next = new ArrayList<>(spawnPoints);
        next.remove(index);
        return new GarageTerminalSettings(terminalId, dimension, terminalPos, terminalYaw,
                next, storagePos, storageRadius);
    }

    public GarageTerminalSettings withClearedSpawns() {
        return new GarageTerminalSettings(terminalId, dimension, terminalPos, terminalYaw,
                List.of(), storagePos, storageRadius);
    }

    public GarageTerminalSettings withStorage(BlockPos pos) {
        return new GarageTerminalSettings(terminalId, dimension, terminalPos, terminalYaw,
                spawnPoints, pos.immutable(), storageRadius);
    }

    public GarageTerminalSettings withStorageRadius(int radius) {
        return new GarageTerminalSettings(terminalId, dimension, terminalPos, terminalYaw,
                spawnPoints, storagePos, Math.max(1, radius));
    }

    /** Точки спавна с подстановкой дефолтной (над терминалом), если список пуст. Минимум одна. */
    public List<GarageSpawnPoint> resolvedSpawnPoints() {
        if (spawnPoints.isEmpty()) {
            return List.of(new GarageSpawnPoint(terminalPos.above(), terminalYaw));
        }
        return spawnPoints;
    }

    /** Позиция первой (основной) точки спавна. */
    public BlockPos resolvedSpawnPos() {
        return resolvedSpawnPoints().get(0).pos();
    }

    /** Направление первой (основной) точки спавна. */
    public float spawnYaw() {
        return resolvedSpawnPoints().get(0).yaw();
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
        ListTag points = new ListTag();
        for (GarageSpawnPoint point : spawnPoints) {
            points.add(point.save());
        }
        tag.put("SpawnPoints", points);
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

        List<GarageSpawnPoint> spawnPoints = new ArrayList<>();
        if (tag.contains("SpawnPoints", Tag.TAG_LIST)) {
            ListTag list = tag.getList("SpawnPoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                spawnPoints.add(GarageSpawnPoint.load(list.getCompound(i)));
            }
        } else if (tag.getBoolean("HasSpawn")) {
            // Обратная совместимость со старым форматом одиночной точки.
            BlockPos spawnPos = readPos(tag, "Spawn", terminalPos.above());
            float spawnYaw = tag.contains("SpawnYaw") ? tag.getFloat("SpawnYaw") : terminalYaw;
            spawnPoints.add(new GarageSpawnPoint(spawnPos, spawnYaw));
        }

        BlockPos storagePos = tag.getBoolean("HasStorage") ? readPos(tag, "Storage", terminalPos) : null;
        int storageRadius = tag.contains("StorageRadius") ? tag.getInt("StorageRadius") : DEFAULT_STORAGE_RADIUS;
        return new GarageTerminalSettings(terminalId, dimension, terminalPos, terminalYaw,
                spawnPoints, storagePos, Math.max(1, storageRadius));
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
