package ru.liko.pjmbasemod.common.frontline;

import net.minecraft.world.level.ChunkPos;
import ru.liko.pjmbasemod.common.region.Region;

import java.util.Locale;

public record FrontlineSectorKey(String dimension, String regionName, int x, int z) {

    public static final int SIZE_CHUNKS = 3;

    public FrontlineSectorKey {
        dimension = normalize(dimension);
        regionName = normalize(regionName);
    }

    public static FrontlineSectorKey of(Region region, ChunkPos pos) {
        return of(region, pos.x, pos.z);
    }

    public static FrontlineSectorKey of(Region region, int chunkX, int chunkZ) {
        return new FrontlineSectorKey(
                region.dimension(),
                region.name(),
                Math.floorDiv(chunkX, SIZE_CHUNKS),
                Math.floorDiv(chunkZ, SIZE_CHUNKS)
        );
    }

    public int minChunkX() {
        return x * SIZE_CHUNKS;
    }

    public int maxChunkX() {
        return minChunkX() + SIZE_CHUNKS - 1;
    }

    public int minChunkZ() {
        return z * SIZE_CHUNKS;
    }

    public int maxChunkZ() {
        return minChunkZ() + SIZE_CHUNKS - 1;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
