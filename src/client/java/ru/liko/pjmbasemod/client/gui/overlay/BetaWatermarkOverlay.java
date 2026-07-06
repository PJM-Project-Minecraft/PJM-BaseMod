package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * Полупрозрачный водяной знак «BETA TEST — Project: Minecraft» в левом верхнем углу.
 * Метка стадии беты: видна, но приглушена, чтобы не мешать HUD.
 */
public final class BetaWatermarkOverlay {

    /** Обрезанная под баннер текстура 1008×238 (только текст, без прозрачных полей). */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/gui/betatest_watermark.png");

    private static final int TEX_WIDTH = 1008;
    private static final int TEX_HEIGHT = 238;

    /** Ширина отрисовки на экране; высота считается по исходной пропорции. */
    private static final int DRAW_WIDTH = 100;
    private static final int DRAW_HEIGHT = Math.round(DRAW_WIDTH * (float) TEX_HEIGHT / TEX_WIDTH);

    private static final int MARGIN_X = 4;
    private static final int MARGIN_Y = 4;

    /** Прозрачность: слабо видно, но видно. */
    private static final float ALPHA = 0.28f;

    public static final LayeredDraw.Layer OVERLAY = (graphics, deltaTracker) -> render(graphics);

    private BetaWatermarkOverlay() {
    }

    private static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, ALPHA);
        try {
            graphics.blit(TEXTURE, MARGIN_X, MARGIN_Y, DRAW_WIDTH, DRAW_HEIGHT,
                    0.0f, 0.0f, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
        } finally {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        }
    }
}
