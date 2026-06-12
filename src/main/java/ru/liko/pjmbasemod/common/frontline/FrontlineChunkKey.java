package ru.liko.pjmbasemod.common.frontline;

import net.minecraft.world.level.ChunkPos;

public record FrontlineChunkKey(String dimension, int x, int z) {

    public FrontlineChunkKey {
        dimension = dimension == null ? "" : dimension;
    }

    public static FrontlineChunkKey of(String dimension, ChunkPos pos) {
        return new FrontlineChunkKey(dimension, pos.x, pos.z);
    }

    public long chunkLong() {
        return ChunkPos.asLong(x, z);
    }
}
