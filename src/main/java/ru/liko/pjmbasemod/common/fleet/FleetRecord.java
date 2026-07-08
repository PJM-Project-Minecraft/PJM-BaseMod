package ru.liko.pjmbasemod.common.fleet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import ru.liko.pjmbasemod.common.garage.GarageType;

import java.util.UUID;

/**
 * Запись реестра активной техники: сущность в мире + её владелец/команда/тип и тайминги.
 * {@code lastOccupiedGameTime} и {@code warned} мутируются при реконсиляции.
 */
public final class FleetRecord {

    public final UUID entityId;
    public final UUID ownerId;
    public final String teamId;   // "" если игрок был без команды
    public final String defId;
    public final GarageType type;
    public final ResourceKey<Level> dimension;
    public final long spawnGameTime;
    public long lastOccupiedGameTime;
    public boolean warned;
    public BlockPos lastPos;

    public FleetRecord(UUID entityId, UUID ownerId, String teamId, String defId, GarageType type,
                       ResourceKey<Level> dimension, long spawnGameTime, long lastOccupiedGameTime, boolean warned,
                       BlockPos lastPos) {
        this.entityId = entityId;
        this.ownerId = ownerId;
        this.teamId = teamId == null ? "" : teamId;
        this.defId = defId;
        this.type = type;
        this.dimension = dimension;
        this.spawnGameTime = spawnGameTime;
        this.lastOccupiedGameTime = lastOccupiedGameTime;
        this.warned = warned;
        this.lastPos = lastPos != null ? lastPos : BlockPos.ZERO;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Entity", entityId);
        tag.putUUID("Owner", ownerId);
        tag.putString("Team", teamId);
        tag.putString("Def", defId);
        tag.putString("Type", type.name());
        tag.putString("Dim", dimension.location().toString());
        tag.putLong("Spawn", spawnGameTime);
        tag.putLong("LastOccupied", lastOccupiedGameTime);
        tag.putBoolean("Warned", warned);
        tag.putLong("Pos", lastPos.asLong());
        return tag;
    }

    public static FleetRecord load(CompoundTag tag) {
        GarageType type;
        try {
            type = GarageType.valueOf(tag.getString("Type"));
        } catch (IllegalArgumentException e) {
            type = GarageType.GROUND;
        }
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(tag.getString("Dim")));
        BlockPos pos = BlockPos.of(tag.getLong("Pos"));
        return new FleetRecord(
                tag.getUUID("Entity"),
                tag.getUUID("Owner"),
                tag.getString("Team"),
                tag.getString("Def"),
                type,
                dim,
                tag.getLong("Spawn"),
                tag.getLong("LastOccupied"),
                tag.getBoolean("Warned"),
                pos);
    }
}
