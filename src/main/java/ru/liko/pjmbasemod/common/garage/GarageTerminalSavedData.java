package ru.liko.pjmbasemod.common.garage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сохранённые точки всех терминалов гаража в мире.
 */
public final class GarageTerminalSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_garage_terminals";
    private static final SavedData.Factory<GarageTerminalSavedData> FACTORY = new SavedData.Factory<>(
            GarageTerminalSavedData::new,
            GarageTerminalSavedData::load);

    private final Map<UUID, GarageTerminalSettings> terminals = new LinkedHashMap<>();

    public static GarageTerminalSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static GarageTerminalSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        GarageTerminalSavedData data = new GarageTerminalSavedData();
        ListTag list = tag.getList("Terminals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            GarageTerminalSettings settings = GarageTerminalSettings.load(list.getCompound(i));
            data.terminals.put(settings.terminalId(), settings);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (GarageTerminalSettings settings : terminals.values()) {
            list.add(settings.save());
        }
        tag.put("Terminals", list);
        return tag;
    }

    public Collection<GarageTerminalSettings> all() {
        return List.copyOf(terminals.values());
    }

    @Nullable
    public GarageTerminalSettings get(UUID terminalId) {
        return terminals.get(terminalId);
    }

    public GarageTerminalSettings remember(UUID terminalId, ServerLevel level, BlockPos terminalPos, float terminalYaw) {
        GarageTerminalSettings old = terminals.get(terminalId);
        GarageTerminalSettings updated = old == null
                ? GarageTerminalSettings.create(terminalId, level.dimension(), terminalPos.immutable(), terminalYaw)
                : old.withTerminal(level.dimension(), terminalPos.immutable(), terminalYaw);
        terminals.put(terminalId, updated);
        if (!updated.equals(old)) {
            setDirty();
        }
        return updated;
    }

    /** Заменяет список точек спавна единственной (команда «set spawn»). */
    public GarageTerminalSettings setSpawn(UUID terminalId, ServerLevel level, BlockPos terminalPos, float terminalYaw,
                                           BlockPos spawnPos, float spawnYaw) {
        GarageTerminalSettings settings = remember(terminalId, level, terminalPos, terminalYaw)
                .withPrimarySpawn(spawnPos, spawnYaw);
        terminals.put(terminalId, settings);
        setDirty();
        return settings;
    }

    /** Меняет направление основной (первой) точки спавна (команда «set facing»). */
    public GarageTerminalSettings setSpawnYaw(UUID terminalId, ServerLevel level, BlockPos terminalPos, float terminalYaw,
                                              float spawnYaw) {
        GarageTerminalSettings settings = remember(terminalId, level, terminalPos, terminalYaw)
                .withFirstSpawnYaw(spawnYaw);
        terminals.put(terminalId, settings);
        setDirty();
        return settings;
    }

    /** Добавляет точку спавна в конец списка. */
    public GarageTerminalSettings addSpawnPoint(UUID terminalId, ServerLevel level, BlockPos terminalPos,
                                                float terminalYaw, BlockPos spawnPos, float spawnYaw) {
        GarageTerminalSettings settings = remember(terminalId, level, terminalPos, terminalYaw)
                .withAddedSpawn(spawnPos, spawnYaw);
        terminals.put(terminalId, settings);
        setDirty();
        return settings;
    }

    /** Удаляет точку спавна по индексу (0-based). */
    public GarageTerminalSettings removeSpawnPoint(UUID terminalId, ServerLevel level, BlockPos terminalPos,
                                                   float terminalYaw, int index) {
        GarageTerminalSettings settings = remember(terminalId, level, terminalPos, terminalYaw)
                .withoutSpawn(index);
        terminals.put(terminalId, settings);
        setDirty();
        return settings;
    }

    /** Очищает все точки спавна (остаётся дефолтная над терминалом). */
    public GarageTerminalSettings clearSpawnPoints(UUID terminalId, ServerLevel level, BlockPos terminalPos,
                                                   float terminalYaw) {
        GarageTerminalSettings settings = remember(terminalId, level, terminalPos, terminalYaw)
                .withClearedSpawns();
        terminals.put(terminalId, settings);
        setDirty();
        return settings;
    }

    public GarageTerminalSettings setStorage(UUID terminalId, ServerLevel level, BlockPos terminalPos, float terminalYaw,
                                             BlockPos storagePos) {
        GarageTerminalSettings settings = remember(terminalId, level, terminalPos, terminalYaw)
                .withStorage(storagePos);
        terminals.put(terminalId, settings);
        setDirty();
        return settings;
    }

    public GarageTerminalSettings setStorageRadius(UUID terminalId, ServerLevel level, BlockPos terminalPos,
                                                   float terminalYaw, int radius) {
        GarageTerminalSettings settings = remember(terminalId, level, terminalPos, terminalYaw)
                .withStorageRadius(radius);
        terminals.put(terminalId, settings);
        setDirty();
        return settings;
    }

    @Nullable
    public GarageTerminalSettings findStorage(ServerLevel level, Entity entity) {
        String dimension = level.dimension().location().toString();
        Vec3 entityPos = entity.position();
        GarageTerminalSettings best = null;
        double bestDistance = Double.MAX_VALUE;
        for (GarageTerminalSettings settings : terminals.values()) {
            if (!dimension.equals(settings.dimension())) continue;
            BlockPos storage = settings.resolvedStoragePos();
            Vec3 center = Vec3.atCenterOf(storage);
            double radius = settings.storageRadius();
            if (entity.getBoundingBox().distanceToSqr(center) > radius * radius) continue;

            double distance = center.distanceToSqr(entityPos);
            if (distance < bestDistance) {
                best = settings;
                bestDistance = distance;
            }
        }
        return best;
    }
}
