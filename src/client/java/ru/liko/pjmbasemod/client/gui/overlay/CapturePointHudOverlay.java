package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import ru.liko.pjmbasemod.client.capturepoint.ClientCapturePointState;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.common.network.packet.CapturePointHudPacket;

/**
 * HUD точки захвата в стиле Squad: плашка плавно выезжает из правого края экрана,
 * когда игрок входит в точку, и уезжает обратно при выходе. Прогресс показывает
 * ромб, заполняющийся снизу вверх, с процентом внутри; слева от ромба — имя точки
 * и состояние (NEUTRALIZE / CAPTURE / SECURED / гейт цепного захвата).
 *
 * <p>Стиль мода сохранён: размытый blur-фон под плашкой (scissor + processBlurEffect),
 * полупрозрачная тёмная подложка без рамок, тонкая цветная полоска-акцент.</p>
 */
public final class CapturePointHudOverlay {

    private static final int PANEL_W = 172;
    private static final int PANEL_H = 46;
    private static final int MARGIN_RIGHT = 8;
    private static final int PANEL_Y = 58;

    private static final int DIAMOND_HALF_W = 17;
    private static final int DIAMOND_HALF_H = 19;

    private static final int NEUTRAL_ACCENT = 0xFF9AA0A6;
    private static final int TITLE_COLOR = 0xFFEDEDED;
    private static final int PANEL_BG = 0x0E1014;
    private static final int DIAMOND_CORE = 0x0B0D11;

    public static final LayeredDraw.Layer OVERLAY = CapturePointHudOverlay::render;

    /** 0 — плашка полностью за правым краем, 1 — на месте. */
    private static float animSlide;
    /** Сглаженный прогресс захвата (0..1): ромб наполняется плавно, а не рывками по пакетам. */
    private static float animProgress;
    /** Последний известный HUD — чтобы плашка доигрывала уход с осмысленным содержимым. */
    private static CapturePointHudPacket lastHud;
    private static long lastFrameTime = System.currentTimeMillis();

    private CapturePointHudOverlay() {}

    private static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrameTime) / 1000f);
        lastFrameTime = now;

        CapturePointHudPacket hud = ClientCapturePointState.hud();
        boolean visible = hud != null && mc.player != null && !mc.options.hideGui && mc.screen == null;
        if (hud != null) lastHud = hud;

        animSlide += ((visible ? 1f : 0f) - animSlide) * Math.min(1f, 10f * dt);

        if (!visible && animSlide < 0.004f) {
            // Полностью уехала — сбрасываем состояние, чтобы следующий вход начался с нуля.
            animSlide = 0f;
            animProgress = 0f;
            lastHud = null;
            return;
        }
        if (animSlide < 0.004f) return;

        CapturePointHudPacket shown = hud != null ? hud : lastHud;
        if (shown == null) return;

        float targetProgress = Mth.clamp(shown.progressPercent() / 100f, 0f, 1f);
        animProgress += (targetProgress - animProgress) * Math.min(1f, 6f * dt);

        drawPanel(g, mc, mc.font, shown, deltaTracker);
    }

    private static void drawPanel(GuiGraphics g, Minecraft mc, Font font,
                                  CapturePointHudPacket hud, DeltaTracker deltaTracker) {
        int screenW = mc.getWindow().getGuiScaledWidth();

        float eased = easeOutCubic(animSlide);
        // Прячется целиком за правый край, поэтому смещение = ширина плашки + её отступ.
        int x = screenW - MARGIN_RIGHT - PANEL_W + Math.round((1f - eased) * (PANEL_W + MARGIN_RIGHT));
        int y = PANEL_Y;
        int right = x + PANEL_W;
        int bottom = y + PANEL_H;

        int a = (int) Mth.clamp(eased * 255f, 0f, 255f);
        if (a <= 2) return;
        int accent = accentColor(hud);

        // Масштаб от разрешения вокруг прав-верх угла (плашка прижата к правому краю).
        // Scissor ниже наследует pose (transformMaxBounds), поэтому blur совпадёт с плашкой.
        PjmGuiUtils.pushHudScale(g, g.guiWidth(), y);
        try {
            RenderSystem.disableDepthTest();

            // Blur только под плашкой, остальной HUD остаётся чётким.
            g.enableScissor(Math.max(0, x), y, Math.min(screenW, right), bottom);
            mc.gameRenderer.processBlurEffect(deltaTracker.getGameTimeDeltaPartialTick(false));
            mc.getMainRenderTarget().bindWrite(false);
            g.disableScissor();

            RenderSystem.enableBlend();

            g.fill(x, y, right, bottom, withAlpha(PANEL_BG, (int) (0xA8 * eased)));
            // Полоска-акцент на внутренней (левой) кромке — правая прижата к краю экрана.
            g.fill(x, y, x + 2, bottom, withAlpha(accent, a));

            int diamondCx = right - 8 - DIAMOND_HALF_W;
            int diamondCy = y + PANEL_H / 2;
            drawDiamond(g, font, diamondCx, diamondCy, accent, a);

            // Текст прижат вправо, к ромбу: плашка выезжает справа, взгляд идёт от края.
            int textRight = diamondCx - DIAMOND_HALF_W - 8;
            int textMaxW = textRight - (x + 8);

            String name = fit(font, hud.pointName(), textMaxW);
            g.drawString(font, name, textRight - font.width(name), y + 13,
                    withAlpha(TITLE_COLOR, a), false);

            String state = fit(font, stateText(hud), textMaxW);
            g.drawString(font, state, textRight - font.width(state), y + 25,
                    withAlpha(stateColor(hud), a), false);
        } finally {
            RenderSystem.disableBlend();
            g.pose().popPose();
        }
    }

    /** Ромб: контур акцентом, тёмная сердцевина, заполнение снизу вверх и процент внутри. */
    private static void drawDiamond(GuiGraphics g, Font font, int cx, int cy, int accent, int alpha) {
        fillDiamond(g, cx, cy, DIAMOND_HALF_W, DIAMOND_HALF_H,
                withAlpha(accent, (int) (alpha * 0.55f)), 0f, 1f);
        fillDiamond(g, cx, cy, DIAMOND_HALF_W - 2, DIAMOND_HALF_H - 2,
                withAlpha(DIAMOND_CORE, (int) (alpha * 0.95f)), 0f, 1f);
        fillDiamond(g, cx, cy, DIAMOND_HALF_W - 2, DIAMOND_HALF_H - 2,
                withAlpha(accent, alpha), 0f, animProgress);

        String pct = Math.round(animProgress * 100f) + "%";
        g.drawString(font, pct, cx - font.width(pct) / 2, cy - 4, withAlpha(0xFFFFFF, alpha), true);
    }

    /**
     * Заливка ромба построчно: {@link GuiGraphics} умеет только прямоугольники, поэтому ромб
     * набирается горизонтальными линиями переменной длины. Рисуются лишь строки, попадающие
     * в диапазон [from, to] по вертикали (0 — нижняя вершина, 1 — верхняя), что и даёт
     * наполнение снизу вверх.
     */
    private static void fillDiamond(GuiGraphics g, int cx, int cy, int halfW, int halfH,
                                    int color, float from, float to) {
        if (halfW <= 0 || halfH <= 0 || to <= from) return;
        for (int dy = -halfH; dy <= halfH; dy++) {
            float t = (halfH - dy) / (float) (halfH * 2);
            if (t < from || t > to) continue;
            int rowHalf = Math.round(halfW * (1f - Math.abs(dy) / (float) halfH));
            if (rowHalf <= 0) continue;
            g.fill(cx - rowHalf, cy + dy, cx + rowHalf, cy + dy + 1, color);
        }
    }

    private static String stateText(CapturePointHudPacket hud) {
        if (hud.locked() && !hud.neutralizing() && !hud.capturing()) return "Нужна соседняя точка";
        if (hud.neutralizing()) return "Нейтрализация: " + hud.captureTeamName();
        if (hud.capturing()) return "Захват: " + hud.captureTeamName();
        if (!hud.ownerTeamName().isEmpty()) return "Контроль: " + hud.ownerTeamName();
        return "Нейтрально";
    }

    private static int stateColor(CapturePointHudPacket hud) {
        if (hud.locked() && !hud.neutralizing() && !hud.capturing()) return NEUTRAL_ACCENT;
        if (hud.neutralizing() || hud.capturing()) {
            return hud.captureColor() == 0 ? NEUTRAL_ACCENT : hud.captureColor();
        }
        return hud.ownerColor() == 0 ? NEUTRAL_ACCENT : hud.ownerColor();
    }

    private static int accentColor(CapturePointHudPacket hud) {
        if (hud == null) return NEUTRAL_ACCENT;
        if (hud.neutralizing() || hud.capturing()) {
            return hud.captureColor() == 0 ? NEUTRAL_ACCENT : hud.captureColor();
        }
        if (!hud.ownerTeamName().isEmpty()) {
            return hud.ownerColor() == 0 ? NEUTRAL_ACCENT : hud.ownerColor();
        }
        return NEUTRAL_ACCENT;
    }

    /** Обрезает строку по ширине, добавляя многоточие: длинные имена точек не должны лезть под ромб. */
    private static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) return text;
        String cut = text;
        while (cut.length() > 1 && font.width(cut + "…") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((Mth.clamp(alpha, 0, 255)) << 24) | (rgb & 0x00FFFFFF);
    }
}
