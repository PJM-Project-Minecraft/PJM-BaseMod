package ru.liko.pjmbasemod.client.worldmap;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import ru.liko.pjmbasemod.client.worldmap.data.MapConstants;
import ru.liko.pjmbasemod.client.worldmap.data.Region;
import ru.liko.pjmbasemod.client.worldmap.data.RegionKey;
import ru.liko.pjmbasemod.client.worldmap.gpu.RegionTexture;

/**
 * Фасад мировой карты (клиентский синглтон). Держит кэш регионов, сканирует загруженные чанки
 * вокруг игрока, пересобирает GPU-текстуры. Фаза 1: всё синхронно на клиентском тике, без диска
 * и фонового потока; цвет — ванильный MapColor (текстурный цвет и биом-тинт — Фаза 3).
 *
 * ponytail: кэш регионов без эвикции — при дальней прогулке память растёт (Фаза 2 добавит LRU).
 */
public final class WorldMapEngine {

    private static final WorldMapEngine INSTANCE = new WorldMapEngine();

    public static WorldMapEngine get() {
        return INSTANCE;
    }

    private final Map<RegionKey, Region> regions = new HashMap<>();
    private final BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
    private String dimKey;

    private WorldMapEngine() {}

    /** Вызывается каждый клиентский тик (рендер-поток). */
    public void onClientTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        String dk = mc.level.dimension().location().toString();
        if (!dk.equals(dimKey)) {
            reset();               // смена измерения — сбрасываем (без персистентности в Фазе 1)
            dimKey = dk;
        }
        scanAround(mc);
        refreshDirty();
    }

    public Collection<Region> renderRegions() {
        return regions.values();
    }

    /** Высота поверхности в мировых координатах, или HEIGHT_UNSET. Для затенения на кромках регионов. */
    public int heightAt(int worldX, int worldZ) {
        Region r = regions.get(RegionKey.ofBlock(dimKey, worldX, worldZ));
        if (r == null) return MapConstants.HEIGHT_UNSET;
        int lx = worldX - r.worldMinX();
        int lz = worldZ - r.worldMinZ();
        return r.height[lz * MapConstants.REGION_BLOCKS + lx];
    }

    public void reset() {
        for (Region region : regions.values()) {
            if (region.gpu != null) region.gpu.free();
        }
        regions.clear();
        dimKey = null;
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
                if (++scanned >= MapConstants.SCAN_PER_TICK) return;
            }
        }
    }

    private Region regionForChunk(int chunkX, int chunkZ) {
        RegionKey key = RegionKey.ofChunk(dimKey, chunkX, chunkZ);
        return regions.computeIfAbsent(key, Region::new);
    }

    private void scanChunk(Level level, Region region, LevelChunk chunk, int chunkX, int chunkZ) {
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
                    int col;
                    try {
                        MapColor mc0 = eff.getMapColor(level, scanPos);
                        col = (mc0 == null) ? 0 : mc0.col;
                    } catch (Throwable t) {
                        continue;
                    }
                    if (col == 0) continue;
                    base = 0xFF000000 | (col & 0xFFFFFF);
                    surfY = h;
                    break;
                }

                int lx = wx - regMinX;
                int lz = wz - regMinZ;
                int idx = lz * MapConstants.REGION_BLOCKS + lx;
                region.baseColor[idx] = base;
                region.height[idx] = (short) surfY;
            }
        }
    }

    private void refreshDirty() {
        int uploads = 0;
        for (Region region : regions.values()) {
            if (!region.gpuDirty) continue;
            if (region.gpu == null) region.gpu = new RegionTexture(region.key);
            region.gpu.reshade(region, this);
            region.gpu.upload();
            region.gpuDirty = false;
            if (++uploads >= MapConstants.UPLOADS_PER_TICK) return;
        }
    }
}
