package ru.liko.pjmbasemod.client.worldmap.color;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Финальный базовый цвет пикселя (до затенения): усреднённая текстура × биом-тинт × glow.
 * Порт цветовой части Xaero MapPixel. Затенение уклона/высоты — отдельно в {@link PixelShader}.
 * Вызывается на главном потоке (есть живой уровень для биом-тинта).
 */
public final class ColorPipeline {

    private ColorPipeline() {}

    public static int compute(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        int avg = BlockColorSampler.baseColor(state);
        int r = (avg >> 16) & 0xFF;
        int g = (avg >> 8) & 0xFF;
        int b = avg & 0xFF;

        // Биом-тинт (трава/листва/вода): поканальное умножение на цвет биома.
        int tintIndex = BlockColorSampler.tintIndex(state);
        if (tintIndex != -1) {
            int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, tintIndex);
            if (tint != -1) {
                r = r * ((tint >> 16) & 0xFF) / 255;
                g = g * ((tint >> 8) & 0xFF) / 255;
                b = b * (tint & 0xFF) / 255;
            }
        }

        // Свечение эмиссивных блоков (лава, глоустоун и т.п.).
        if (state.getLightEmission() > 0) {
            int total = r + g + b;
            double k = Math.max(1.0, 407.0 / Math.max(1, total));
            r = Math.min(255, (int) (r * k));
            g = Math.min(255, (int) (g * k));
            b = Math.min(255, (int) (b * k));
        }

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
