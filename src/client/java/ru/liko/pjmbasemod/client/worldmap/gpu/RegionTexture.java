package ru.liko.pjmbasemod.client.worldmap.gpu;

import java.util.Locale;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.client.worldmap.WorldMapEngine;
import ru.liko.pjmbasemod.client.worldmap.color.PixelShader;
import ru.liko.pjmbasemod.client.worldmap.data.MapConstants;
import ru.liko.pjmbasemod.client.worldmap.data.Region;
import ru.liko.pjmbasemod.client.worldmap.data.RegionKey;

/**
 * GPU-текстура одного региона: 512×512 {@link DynamicTexture}. Вместо PBO/LOD-дерева Xaero —
 * одна текстура на регион, перезаливаемая целиком при изменении. NEAREST-фильтр для чёткости.
 * Все методы — на рендер-потоке (создание/заливка GL).
 */
public final class RegionTexture {

    private final ResourceLocation location;
    private final DynamicTexture texture;

    public RegionTexture(RegionKey key) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, MapConstants.REGION_BLOCKS, MapConstants.REGION_BLOCKS, true);
        this.texture = new DynamicTexture(img);
        this.location = ResourceLocation.fromNamespaceAndPath("pjmbasemod", path(key));
        Minecraft.getInstance().getTextureManager().register(location, texture);
        this.texture.setFilter(false, false); // NEAREST, без mipmap
    }

    public ResourceLocation location() {
        return location;
    }

    /** Пересчитать затенение из массивов региона и записать в NativeImage (без заливки на GPU). */
    public void reshade(Region region, WorldMapEngine engine) {
        NativeImage img = texture.getPixels();
        if (img == null) return;
        final int minX = region.worldMinX();
        final int minZ = region.worldMinZ();
        final short[] hgt = region.height;
        final int[] base = region.baseColor;
        final int side = MapConstants.REGION_BLOCKS;
        for (int lz = 0; lz < side; lz++) {
            int row = lz * side;
            for (int lx = 0; lx < side; lx++) {
                int idx = row + lx;
                int h = hgt[idx];
                if (h == MapConstants.HEIGHT_UNSET) {
                    img.setPixelRGBA(lx, lz, 0);
                    continue;
                }
                // Внутри региона читаем соседей напрямую; на кромке — через движок (соседний регион).
                int hN = (lz > 0) ? hgt[idx - side] : engine.heightAt(minX + lx, minZ + lz - 1);
                int hNW = (lx > 0 && lz > 0) ? hgt[idx - side - 1] : engine.heightAt(minX + lx - 1, minZ + lz - 1);
                int shaded = PixelShader.shade(base[idx], h, hN, hNW);
                img.setPixelRGBA(lx, lz, toAbgr(shaded));
            }
        }
    }

    /** Залить NativeImage на GPU. */
    public void upload() {
        texture.upload();
    }

    public void free() {
        texture.close();
        Minecraft.getInstance().getTextureManager().release(location);
    }

    /** 0xAARRGGBB → 0xAABBGGRR (порядок каналов NativeImage RGBA little-endian). */
    private static int toAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    private static String path(RegionKey key) {
        String dim = key.dimKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return "worldmap/" + dim + "/r" + key.rx() + "_" + key.rz();
    }
}
