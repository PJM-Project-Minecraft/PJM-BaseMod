package ru.liko.pjmbasemod.client.worldmap.color;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Усреднённый цвет текстуры блока — «палитра» Xaero. Порт xaerolib BlockTextureColorUtils:
 * берётся спрайт самой большой верхней грани (иначе particle-icon), из исходного PNG усредняются
 * пиксели (с alpha-порогом). Кэш по BlockState и по имени текстуры. Фолбэк — ванильный MapColor.
 *
 * <p>Всё на главном/рендер-потоке (как и у самого Xaero): доступ к атласу/моделям + ImageIO.
 * ponytail: cache-miss делает синхронный ImageIO.read; при заметных фризах — вынести в воркер.
 */
public final class BlockColorSampler {

    private static final Map<BlockState, Integer> BLOCK_COLOR = new HashMap<>();
    private static final Map<BlockState, Integer> BLOCK_TINT = new HashMap<>();
    private static final Map<String, Integer> TEXTURE_COLOR = new HashMap<>();

    private BlockColorSampler() {}

    /** Базовый цвет блока 0xFFrrggbb (усреднённая текстура или MapColor-фолбэк). */
    public static int baseColor(BlockState state) {
        ensure(state);
        return BLOCK_COLOR.get(state);
    }

    /** Индекс тинта верхней грани (0 = может тинтоваться биомом, -1 = без тинта). */
    public static int tintIndex(BlockState state) {
        ensure(state);
        return BLOCK_TINT.get(state);
    }

    public static void clearCache() {
        BLOCK_COLOR.clear();
        BLOCK_TINT.clear();
        TEXTURE_COLOR.clear();
    }

    private static void ensure(BlockState state) {
        if (BLOCK_COLOR.containsKey(state)) return;
        int tintIndex = -1;
        int color;
        try {
            Minecraft mc = Minecraft.getInstance();
            BlockModelShaper bms = mc.getBlockRenderer().getBlockModelShaper();
            BakedModel model = bms.getBlockModel(state);
            RandomSource rnd = RandomSource.create(42L);
            BakedQuad up = biggestUpQuad(model, state, rnd);
            TextureAtlasSprite missing = mc.getModelManager()
                    .getAtlas(TextureAtlas.LOCATION_BLOCKS)
                    .getSprite(MissingTextureAtlasSprite.getLocation());
            TextureAtlasSprite sprite;
            if (up != null && up.getSprite() != missing) {
                sprite = up.getSprite();
                tintIndex = up.getTintIndex();
            } else {
                sprite = model.getParticleIcon(ModelData.EMPTY);
                tintIndex = 0;
            }
            String texName = sprite.contents().name() + ".png";
            Integer texCached = TEXTURE_COLOR.get(texName);
            if (texCached == null) {
                texCached = averageTexture(mc, texName);
                TEXTURE_COLOR.put(texName, texCached);
            }
            color = texCached;
        } catch (Throwable t) {
            color = mapColorFallback(state);
            tintIndex = -1;
        }
        BLOCK_COLOR.put(state, color);
        BLOCK_TINT.put(state, tintIndex);
    }

    private static int averageTexture(Minecraft mc, String texNameWithPng) throws IOException {
        ResourceLocation id = ResourceLocation.parse(texNameWithPng); // напр. minecraft:block/grass_block_top.png
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath());
        Resource res = mc.getResourceManager().getResource(loc).orElse(null);
        if (res == null) throw new IOException("нет текстуры " + loc);
        BufferedImage img;
        try (InputStream in = res.open()) {
            img = ImageIO.read(in);
        }
        if (img == null) throw new IOException("не читается " + loc);
        int ts = Math.min(img.getWidth(), img.getHeight()); // квадрат = первый кадр анимации
        if (ts <= 0) throw new IOException("пустая " + loc);
        int diff = Math.max(1, Math.min(4, ts / 8));
        int parts = ts / diff;
        boolean gray = img.getColorModel().getNumComponents() < 3;
        Raster raster = gray ? img.getData() : null;
        int[] hold = null;
        long r = 0, g = 0, b = 0, n = 0;
        for (int i = 0; i < parts; i++) {
            for (int j = 0; j < parts; j++) {
                int rgb;
                if (gray) {
                    hold = raster.getPixel(i * diff, j * diff, hold);
                    int s = hold[0] & 0xFF;
                    int a = hold.length > 1 ? hold[1] : 255;
                    rgb = (a << 24) | (s << 16) | (s << 8) | s;
                } else {
                    rgb = img.getRGB(i * diff, j * diff);
                }
                int a = (rgb >> 24) & 0xFF;
                if (rgb != 0 && a > 10) {
                    r += (rgb >> 16) & 0xFF;
                    g += (rgb >> 8) & 0xFF;
                    b += rgb & 0xFF;
                    n++;
                }
            }
        }
        if (n == 0) n = 1;
        return 0xFF000000 | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
    }

    /** Верхняя грань с наибольшей площадью в плоскости XZ (или null). */
    private static BakedQuad biggestUpQuad(BakedModel model, BlockState state, RandomSource rnd) {
        List<BakedQuad> quads = model.getQuads(state, Direction.UP, rnd);
        BakedQuad best = null;
        float bestArea = -1f;
        for (BakedQuad q : quads) {
            float area = xzArea(q);
            if (area > bestArea) {
                bestArea = area;
                best = q;
            }
        }
        return best;
    }

    private static float xzArea(BakedQuad quad) {
        int[] v = quad.getVertices();
        int stride = v.length / 4; // формат BLOCK: 8 int/вершину
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float x = Float.intBitsToFloat(v[i * stride]);
            float z = Float.intBitsToFloat(v[i * stride + 2]);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        return (maxX - minX) * (maxZ - minZ);
    }

    private static int mapColorFallback(BlockState state) {
        try {
            MapColor c = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return (c == null || c.col == 0) ? 0xFF7F7F7F : (0xFF000000 | (c.col & 0xFFFFFF));
        } catch (Throwable t) {
            return 0xFF7F7F7F;
        }
    }
}
