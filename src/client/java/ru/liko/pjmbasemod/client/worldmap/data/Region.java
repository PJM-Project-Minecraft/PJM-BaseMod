package ru.liko.pjmbasemod.client.worldmap.data;

import java.util.Arrays;
import java.util.BitSet;

import ru.liko.pjmbasemod.client.worldmap.gpu.RegionTexture;

/**
 * Оперативный тайл региона (512×512 блоков). Хранит запечённый базовый цвет и высоту
 * поверхности на пиксель — 6 байт/пиксель. Затенение уклона НЕ хранится: пересчитывается
 * из соседних высот при обновлении GPU-текстуры (как load-time слоуп у Xaero).
 */
public final class Region {

    public final RegionKey key;
    /** 0xAARRGGBB, до затенения (в Фазе 1 — из ванильного MapColor). 0 = нет блока. */
    public final int[] baseColor = new int[MapConstants.REGION_PIXELS];
    /** Y поверхности; HEIGHT_UNSET = столбец ещё не отсканирован. */
    public final short[] height = new short[MapConstants.REGION_PIXELS];
    /** Битсет отсканированных чанков (32×32). */
    private final BitSet scannedChunks = new BitSet(MapConstants.CHUNKS_PER_REGION * MapConstants.CHUNKS_PER_REGION);

    /** Нужно ли пересобрать GPU-текстуру. */
    public boolean gpuDirty;
    /** Есть несохранённые на диск изменения. */
    public boolean diskDirty;
    /** Ленивая GPU-текстура (создаётся на рендер-потоке). */
    public RegionTexture gpu;

    public Region(RegionKey key) {
        this.key = key;
        Arrays.fill(height, MapConstants.HEIGHT_UNSET);
    }

    public int worldMinX() {
        return key.worldMinX();
    }

    public int worldMinZ() {
        return key.worldMinZ();
    }

    public boolean isChunkScanned(int localChunkX, int localChunkZ) {
        return scannedChunks.get(localChunkZ * MapConstants.CHUNKS_PER_REGION + localChunkX);
    }

    public void markChunkScanned(int localChunkX, int localChunkZ) {
        scannedChunks.set(localChunkZ * MapConstants.CHUNKS_PER_REGION + localChunkX);
    }

    public boolean hasAnyScanned() {
        return !scannedChunks.isEmpty();
    }
}
