package ru.liko.pjmbasemod.client.worldmap.data;

/**
 * Ключ региона карты: измерение + координаты региона (512-блочные).
 * Служит ключом в кэше регионов и (позже) именем файла на диске.
 */
public record RegionKey(String dimKey, int rx, int rz) {

    /** Регион, покрывающий данный чанк. */
    public static RegionKey ofChunk(String dimKey, int chunkX, int chunkZ) {
        return new RegionKey(dimKey, chunkX >> MapConstants.CHUNKS_PER_REGION_SHIFT,
                chunkZ >> MapConstants.CHUNKS_PER_REGION_SHIFT);
    }

    /** Регион, покрывающий данный мировой блок. */
    public static RegionKey ofBlock(String dimKey, int blockX, int blockZ) {
        return new RegionKey(dimKey, blockX >> MapConstants.REGION_SHIFT,
                blockZ >> MapConstants.REGION_SHIFT);
    }

    public int worldMinX() {
        return rx << MapConstants.REGION_SHIFT;
    }

    public int worldMinZ() {
        return rz << MapConstants.REGION_SHIFT;
    }
}
