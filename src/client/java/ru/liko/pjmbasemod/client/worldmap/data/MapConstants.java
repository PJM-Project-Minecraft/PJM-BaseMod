package ru.liko.pjmbasemod.client.worldmap.data;

/** Все «магические числа» карты в одном месте. Геометрия и константы затенения — из Xaero. */
public final class MapConstants {

    private MapConstants() {}

    // --- Геометрия региона ---
    /** Регион = 512×512 блоков (как MapRegion Xaero: 8×8 tilechunk = 32×32 чанка). */
    public static final int REGION_BLOCKS = 512;
    public static final int REGION_SHIFT = 9;                 // 1<<9 = 512
    public static final int CHUNKS_PER_REGION = 32;
    public static final int CHUNKS_PER_REGION_SHIFT = 5;      // 1<<5 = 32
    public static final int REGION_PIXELS = REGION_BLOCKS * REGION_BLOCKS;

    /** Y ещё не отсканированного столбца. Не может совпасть с реальной высотой мира. */
    public static final short HEIGHT_UNSET = Short.MIN_VALUE;

    // --- Затенение (MapPixel Xaero, режим slopes>=2) ---
    public static final double AMBIENT_COLORED = 0.2;
    public static final double AMBIENT_WHITE = 0.5;
    public static final double MAX_DIRECT = 0.6666667;
    public static final double DIRECT_QUANT = 0.88388;

    // --- Троттлинг скана (Фаза 1: синхронно на клиентском тике) ---
    public static final int SCAN_RADIUS_CHUNKS = 12;
    public static final int SCAN_PER_TICK = 8;
    public static final int UPLOADS_PER_TICK = 2;

    // Фон карты там, где ещё не исследовано.
    public static final int BACKGROUND_ARGB = 0xFF0A0A17;
}
