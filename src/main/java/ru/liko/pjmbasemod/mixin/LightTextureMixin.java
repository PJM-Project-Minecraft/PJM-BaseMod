package ru.liko.pjmbasemod.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.pjmbasemod.Config;

/**
 * «True Darkness»: ночью небесный свет не освещает мир — видно только блочный свет
 * (факелы, фонари) и ночное зрение. Постобработка готовой lightmap-текстуры 16×16
 * перед загрузкой в GPU: каждый пиксель (block, sky) стягивается к пикселю той же
 * колонки с sky=0 (чистый блочный свет) пропорционально глубине ночи.
 *
 * <p>Работает после гаммы — выкрутить яркость в настройках не поможет. Ночное зрение
 * и Darkness-эффект вшиты в базовый пиксель (sky=0), поэтому работают как обычно.
 * Вспышка молнии временно возвращает ванильную яркость. Клиент-only миксин
 * (массив {@code "client"} в {@code pjmbasemod.mixins.json}).</p>
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {

    @Shadow @Final private NativeImage lightPixels;
    @Shadow @Final private Minecraft minecraft;

    @Inject(
            method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
    private void pjm_darkenNight(float partialTicks, CallbackInfo ci) {
        if (!Config.isNightDarknessEnabled()) return;
        ClientLevel level = this.minecraft.level;
        if (level == null || !level.dimensionType().hasSkyLight() || level.effects().forceBrightLightmap()) return;
        if (level.getSkyFlashTime() > 0) return;

        // getSkyDarken: 1.0 днём … 0.2 глухой ночью → night: 0 днём … 1 ночью
        float night = Mth.clamp((1.0F - level.getSkyDarken(1.0F)) / 0.8F, 0.0F, 1.0F)
                * (float) Config.getNightDarknessIntensity();
        if (night <= 0.0F) return;

        for (int sky = 1; sky < 16; sky++) {
            for (int block = 0; block < 16; block++) {
                int lit = this.lightPixels.getPixelRGBA(block, sky);
                int dark = this.lightPixels.getPixelRGBA(block, 0);
                this.lightPixels.setPixelRGBA(block, sky, pjm_lerpColor(lit, dark, night));
            }
        }
    }

    /** Покомпонентный lerp упакованного цвета (альфа остаётся непрозрачной). */
    @Unique
    private static int pjm_lerpColor(int from, int to, float t) {
        int c0 = Mth.lerpInt(t, from & 0xFF, to & 0xFF);
        int c1 = Mth.lerpInt(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int c2 = Mth.lerpInt(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
        return 0xFF000000 | c2 << 16 | c1 << 8 | c0;
    }
}
