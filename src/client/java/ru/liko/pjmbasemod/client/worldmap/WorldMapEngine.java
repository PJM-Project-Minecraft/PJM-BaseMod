package ru.liko.pjmbasemod.client.worldmap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import ru.liko.pjmbasemod.client.worldmap.color.ColorPipeline;
import ru.liko.pjmbasemod.client.worldmap.data.MapConstants;
import ru.liko.pjmbasemod.client.worldmap.data.Region;
import ru.liko.pjmbasemod.client.worldmap.data.RegionKey;
import ru.liko.pjmbasemod.client.worldmap.gpu.RegionTexture;
import ru.liko.pjmbasemod.client.worldmap.io.RegionStore;
import ru.liko.pjmbasemod.client.worldmap.io.WorldId;

/**
 * Фасад мировой карты (клиентский синглтон). Держит LRU-кэш регионов, сканирует загруженные
 * чанки вокруг игрока (главный поток — доступ к ClientLevel), пересобирает GPU-текстуры,
 * пишет/читает регионы с диска (исследованное переживает разгрузку/релог).
 *
 * <p>Инвариант потоков: всё на клиентском/рендер-потоке (тик и рендер идут последовательно в
 * одном потоке); кодирование региона — тоже здесь (консистентный снимок), запись — асинхронно
 * в {@link RegionStore}. Фоновый воркер цвета не нужен: сам Xaero считает цвет на рендер-потоке
 * с кэшем по BlockState.
 *
 * <p>Инвариант LinkedHashMap(accessOrder): {@code get()} переупорядочивает и потому НЕ вызывается
 * во время итерации {@code regions.values()}. Все итерации {@code values()} — read-only без get().
 */
public final class WorldMapEngine {

    private static final WorldMapEngine INSTANCE = new WorldMapEngine();

    public static WorldMapEngine get() {
        return INSTANCE;
    }

    private final RegionStore store = new RegionStore();

    /** LRU: eldest выселяется (с сохранением на диск и освобождением текстуры) при переполнении. */
    private final Map<RegionKey, Region> regions =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<RegionKey, Region> eldest) {
                    if (size() > MapConstants.REGION_CACHE_CAP) {
                        Region r = eldest.getValue();
                        if (r.diskDirty) store.saveAsync(r); // не потерять исследованное
                        if (r.gpu != null) r.gpu.free();
                        return true;
                    }
                    return false;
                }
            };

    private final BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
    /** Регионы, которых нет на диске — чтобы не пытаться читать их каждый кадр. */
    private final java.util.Set<RegionKey> missingOnDisk = new java.util.HashSet<>();
    private String dimKey;
    private long tickCounter;
    private int rescanCursor;

    /** Лимит подгрузок с диска за один кадр карты (регион ~1.5 МБ, читается быстро). */
    private static final int DISK_LOADS_PER_VIEW = 4;

    private WorldMapEngine() {}

    /** Вызывается каждый клиентский тик (рендер-поток). */
    public void onClientTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        String dk = mc.level.dimension().location().toString();
        if (!dk.equals(dimKey)) {
            reset();                                       // сохраняет старые регионы (текущий baseDir)
            dimKey = dk;
            store.setBaseDir(WorldId.resolveDir(mc, dk));  // папка нового измерения
        }
        scanAround(mc);
        rescanAround(mc);
        refreshDirty();
        if (++tickCounter % MapConstants.AUTOSAVE_INTERVAL_TICKS == 0) {
            autosave();
        }
    }

    /**
     * Регионы, попадающие в видимую область (с отсечением). {@code get()} по каждому ключу
     * «прогревает» LRU, чтобы видимые регионы не выселялись. Возвращает свежий список — итерировать
     * его в рендере безопасно.
     */
    public List<Region> regionsInView(double camX, double camZ, double scale, int width, int height) {
        List<Region> out = new ArrayList<>();
        if (dimKey == null) return out;
        double halfW = width / 2.0;
        double halfH = height / 2.0;
        int rMinX = Mth.floor(camX - halfW / scale) >> MapConstants.REGION_SHIFT;
        int rMaxX = Mth.floor(camX + halfW / scale) >> MapConstants.REGION_SHIFT;
        int rMinZ = Mth.floor(camZ - halfH / scale) >> MapConstants.REGION_SHIFT;
        int rMaxZ = Mth.floor(camZ + halfH / scale) >> MapConstants.REGION_SHIFT;
        if (missingOnDisk.size() > 8192) missingOnDisk.clear(); // страховка от бесконечного роста
        int diskLoads = 0;
        for (int rz = rMinZ; rz <= rMaxZ; rz++) {
            for (int rx = rMinX; rx <= rMaxX; rx++) {
                RegionKey key = new RegionKey(dimKey, rx, rz);
                Region r = regions.get(key); // прогрев LRU
                // Исследованное в прошлых сессиях лениво поднимается с диска: после релога
                // в памяти пусто, а скан прогревает только регионы вокруг игрока.
                if (r == null && diskLoads < DISK_LOADS_PER_VIEW && !missingOnDisk.contains(key)) {
                    r = store.load(key);
                    if (r == null) {
                        missingOnDisk.add(key);
                    } else {
                        r.gpuDirty = true; // построить текстуру из загруженных данных
                        regions.put(key, r);
                        diskLoads++;
                    }
                }
                if (r != null && r.gpu != null) out.add(r);
            }
        }
        return out;
    }

    /** Высота поверхности в мировых координатах, или HEIGHT_UNSET. Для затенения на кромках регионов. */
    public int heightAt(int worldX, int worldZ) {
        Region r = regions.get(RegionKey.ofBlock(dimKey, worldX, worldZ));
        if (r == null) return MapConstants.HEIGHT_UNSET;
        int lx = worldX - r.worldMinX();
        int lz = worldZ - r.worldMinZ();
        return r.height[lz * MapConstants.REGION_BLOCKS + lx];
    }

    /** Логаут/смена измерения: сохранить всё на диск, освободить текстуры, очистить. */
    public void reset() {
        persistAll();
        for (Region region : regions.values()) {
            if (region.gpu != null) region.gpu.free();
        }
        regions.clear();
        missingOnDisk.clear();
        dimKey = null;
    }

    // --- Персистентность ---

    private void autosave() {
        for (Region region : regions.values()) {
            if (region.diskDirty) {
                store.saveAsync(region);
                region.diskDirty = false;
            }
        }
    }

    private void persistAll() {
        boolean any = false;
        for (Region region : regions.values()) {
            if (region.diskDirty) {
                store.saveAsync(region);
                region.diskDirty = false;
                any = true;
            }
        }
        if (any) store.flush();
    }

    // --- Скан ---

    private void scanAround(Minecraft mc) {
        Level level = mc.level;
        BlockPos pp = mc.player.blockPosition();
        int pcx = pp.getX() >> 4;
        int pcz = pp.getZ() >> 4;
        int r = Math.min(MapConstants.SCAN_RADIUS_CHUNKS, mc.options.getEffectiveRenderDistance());
        int scanned = 0;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                Region region = regionForChunk(cx, cz);
                int lcx = cx - (region.key.rx() << MapConstants.CHUNKS_PER_REGION_SHIFT);
                int lcz = cz - (region.key.rz() << MapConstants.CHUNKS_PER_REGION_SHIFT);
                if (region.isChunkScanned(lcx, lcz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                if (chunk instanceof EmptyLevelChunk) continue;
                scanChunk(level, region, chunk, cx, cz);
                region.markChunkScanned(lcx, lcz);
                region.gpuDirty = true;
                region.diskDirty = true;
                markAdjacentDirty(region.key, lcx, lcz); // залечивание швов между регионами
                if (++scanned >= MapConstants.SCAN_PER_TICK) return;
            }
        }
    }

    /**
     * Фоновое обновление уже отсканированных чанков: карта отражает изменения мира
     * (постройки, взрывы). Round-robin по квадрату скана, {@code RESCAN_PER_TICK} чанков за
     * тик — полный круг при радиусе 12 занимает ~8 секунд. Регион дёргается только при
     * реальном изменении данных, поэтому GPU/диск не гоняются впустую.
     */
    private void rescanAround(Minecraft mc) {
        Level level = mc.level;
        BlockPos pp = mc.player.blockPosition();
        int pcx = pp.getX() >> 4;
        int pcz = pp.getZ() >> 4;
        int r = Math.min(MapConstants.SCAN_RADIUS_CHUNKS, mc.options.getEffectiveRenderDistance());
        int side = r * 2 + 1;
        int total = side * side;
        for (int i = 0; i < MapConstants.RESCAN_PER_TICK; i++) {
            rescanCursor = (rescanCursor + 1) % total;
            int cx = pcx + rescanCursor % side - r;
            int cz = pcz + rescanCursor / side - r;
            Region region = regions.get(RegionKey.ofChunk(dimKey, cx, cz));
            if (region == null) continue; // первичный скан ещё не добрался
            int lcx = cx - (region.key.rx() << MapConstants.CHUNKS_PER_REGION_SHIFT);
            int lcz = cz - (region.key.rz() << MapConstants.CHUNKS_PER_REGION_SHIFT);
            if (!region.isChunkScanned(lcx, lcz)) continue;
            LevelChunk chunk = level.getChunk(cx, cz);
            if (chunk instanceof EmptyLevelChunk) continue;
            if (scanChunk(level, region, chunk, cx, cz)) {
                region.gpuDirty = true;
                region.diskDirty = true;
                markAdjacentDirty(region.key, lcx, lcz);
            }
        }
    }

    private Region regionForChunk(int chunkX, int chunkZ) {
        RegionKey key = RegionKey.ofChunk(dimKey, chunkX, chunkZ);
        Region r = regions.get(key);
        if (r == null) {
            r = store.load(key);          // попытка загрузить исследованное с диска
            if (r == null) {
                r = new Region(key);
            } else {
                r.gpuDirty = true;        // построить текстуру из загруженных данных
            }
            regions.put(key, r);          // может выселить eldest (с сохранением)
        }
        return r;
    }

    /** Если отсканирован пограничный чанк — сосед-регион должен переотрисовать свою кромку. */
    private void markAdjacentDirty(RegionKey key, int localChunkX, int localChunkZ) {
        if (localChunkX == 0) markRegionDirty(key.rx() - 1, key.rz());
        if (localChunkX == MapConstants.CHUNKS_PER_REGION - 1) markRegionDirty(key.rx() + 1, key.rz());
        if (localChunkZ == 0) markRegionDirty(key.rx(), key.rz() - 1);
        if (localChunkZ == MapConstants.CHUNKS_PER_REGION - 1) markRegionDirty(key.rx(), key.rz() + 1);
    }

    private void markRegionDirty(int rx, int rz) {
        Region r = regions.get(new RegionKey(dimKey, rx, rz));
        if (r != null) r.gpuDirty = true;
    }

    /** Сканирует чанк в регион. Возвращает {@code true}, если данные реально изменились. */
    private boolean scanChunk(Level level, Region region, LevelChunk chunk, int chunkX, int chunkZ) {
        boolean changed = false;
        int baseWX = chunkX << 4;
        int baseWZ = chunkZ << 4;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int regMinX = region.worldMinX();
        int regMinZ = region.worldMinZ();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = baseWX + x;
                int wz = baseWZ + z;
                int start = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                if (start >= maxY) start = maxY - 1;

                int base = 0;
                int surfY = MapConstants.HEIGHT_UNSET;
                for (int h = start; h >= minY; h--) {
                    scanPos.set(wx, h, wz);
                    BlockState s = chunk.getBlockState(scanPos);
                    if (s.isAir()) continue;
                    BlockState eff = s;
                    FluidState f = s.getFluidState();
                    if (!f.isEmpty()) {
                        eff = f.createLegacyBlock();
                    } else if (s.getBlock() instanceof BushBlock || s.getRenderShape() == RenderShape.INVISIBLE) {
                        continue; // трава/цветы/невидимые — не поверхность
                    }
                    int mapCol;
                    try {
                        MapColor mc0 = eff.getMapColor(level, scanPos);
                        mapCol = (mc0 == null) ? 0 : mc0.col;
                    } catch (Throwable t) {
                        continue;
                    }
                    if (mapCol == 0) continue; // невидимые/бесцветные (стекло, воздух-подобные) — не поверхность
                    base = ColorPipeline.compute(eff, level, scanPos); // текстурный цвет + биом-тинт + glow
                    surfY = h;
                    break;
                }

                int lx = wx - regMinX;
                int lz = wz - regMinZ;
                int idx = lz * MapConstants.REGION_BLOCKS + lx;
                if (region.baseColor[idx] != base || region.height[idx] != (short) surfY) {
                    region.baseColor[idx] = base;
                    region.height[idx] = (short) surfY;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private void refreshDirty() {
        // Сначала собираем dirty-регионы БЕЗ get() (иначе CME при access-order итерации),
        // затем reshade (который дёргает heightAt→get()) уже по отдельному списку.
        List<Region> dirty = null;
        for (Region region : regions.values()) {
            if (region.gpuDirty) {
                if (dirty == null) dirty = new ArrayList<>();
                dirty.add(region);
            }
        }
        if (dirty == null) return;
        int uploads = 0;
        for (Region region : dirty) {
            if (region.gpu == null) region.gpu = new RegionTexture(region.key);
            region.gpu.reshade(region, this);
            region.gpu.upload();
            region.gpuDirty = false;
            if (++uploads >= MapConstants.UPLOADS_PER_TICK) break;
        }
    }
}
