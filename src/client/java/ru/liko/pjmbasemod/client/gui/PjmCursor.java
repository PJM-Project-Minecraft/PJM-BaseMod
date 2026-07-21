package ru.liko.pjmbasemod.client.gui;

import java.io.InputStream;
import java.nio.ByteBuffer;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Свой пиксельный курсор на экранах мода (в т.ч. на карте). Из текстур мода создаётся GLFW-курсор
 * ({@code glfwCreateCursor}) и ставится на окно — ОС сама рисует его в GUI. В геймплее (курсор
 * захвачен) он скрыт как обычно. Ленивое создание на рендер-потоке; при ошибке — системный курсор.
 *
 * <p>Курсоры — из ресурс-пака «Pixel Cursor ++» (значительная часть — Astropulse). Хотспоты:
 * default (0,0), grabbing/pointing_hand (7,0). См. textures/gui/cursor/CREDITS.txt.
 */
public final class PjmCursor {

    private static long defaultCursor;
    private static long grabCursor;
    private static long handCursor;
    private static boolean loaded;

    private PjmCursor() {}

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        defaultCursor = create("textures/gui/cursor/default.png", 0, 0);
        grabCursor = create("textures/gui/cursor/grabbing.png", 7, 0);
        handCursor = create("textures/gui/cursor/pointing_hand.png", 7, 0);
    }

    private static long create(String path, int xhot, int yhot) {
        try {
            Minecraft mc = Minecraft.getInstance();
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("pjmbasemod", path);
            Resource res = mc.getResourceManager().getResource(rl).orElse(null);
            if (res == null) return 0L;
            NativeImage img;
            try (InputStream in = res.open()) {
                img = NativeImage.read(in);
            }
            int w = img.getWidth();
            int h = img.getHeight();
            ByteBuffer pixels = MemoryUtil.memAlloc(w * h * 4);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int p = img.getPixelRGBA(x, y); // NativeImage RGBA little-endian → 0xAABBGGRR
                    pixels.put((byte) (p & 0xFF));          // R
                    pixels.put((byte) ((p >> 8) & 0xFF));   // G
                    pixels.put((byte) ((p >> 16) & 0xFF));  // B
                    pixels.put((byte) ((p >> 24) & 0xFF));  // A
                }
            }
            pixels.flip();
            GLFWImage gi = GLFWImage.malloc();
            gi.width(w);
            gi.height(h);
            gi.pixels(pixels);
            long cursor = GLFW.glfwCreateCursor(gi, xhot, yhot); // копирует пиксели
            gi.free();
            MemoryUtil.memFree(pixels);
            img.close();
            return cursor;
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static long window() {
        return Minecraft.getInstance().getWindow().getWindow();
    }

    public static void applyDefault() {
        ensureLoaded();
        if (defaultCursor != 0L) GLFW.glfwSetCursor(window(), defaultCursor);
    }

    public static void applyGrab() {
        ensureLoaded();
        if (grabCursor != 0L) GLFW.glfwSetCursor(window(), grabCursor);
    }

    public static void applyHand() {
        ensureLoaded();
        if (handCursor != 0L) GLFW.glfwSetCursor(window(), handCursor);
    }

    /** Вернуть системный курсор. */
    public static void restore() {
        GLFW.glfwSetCursor(window(), 0L);
    }
}
