package ru.liko.pjmbasemod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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
 * <p>Гамма (в т.ч. gamma-hack &gt; 1.0 в options.txt) зануляется пропорционально глубине
 * ночи прямо в расчёте lightmap — выкрутить яркость не поможет. Ночное зрение
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
        float night = pjm_nightFactor();
        if (night <= 0.0F) return;

        // Две ступени, т.к. при intensity 1.0 небесный свет уже вычтен целиком:
        // 0…1 — стягиваем к чистому блочному свету, 1…2 — дожимаем к чёрному ambient-пол
        // ванили (block=0), из-за которого тени остаются серыми.
        float toDark = Math.min(night, 1.0F);
        float toBlack = Math.max(0.0F, night - 1.0F);

        for (int block = 0; block < 16; block++) {
            // Вес по блочному свету: block=15 (вплотную к факелу) не трогаем вовсе,
            // block=0 (света нет) гасим полностью — факелы светят, темнеет только тень.
            float unlit = toBlack * (1.0F - block / 15.0F);
            int dark = this.lightPixels.getPixelRGBA(block, 0);
            int target = unlit > 0.0F ? pjm_lerpColor(dark, 0xFF000000, unlit) : dark;
            for (int sky = 1; sky < 16; sky++) {
                int lit = this.lightPixels.getPixelRGBA(block, sky);
                this.lightPixels.setPixelRGBA(block, sky, pjm_lerpColor(lit, target, toDark));
            }
            // Колонку sky=0 правим последней: она — источник `dark` для строк выше.
            this.lightPixels.setPixelRGBA(block, 0, target);
        }
    }

    /**
     * Гасит гамму пропорционально глубине ночи в самом расчёте lightmap, иначе она
     * осветляет базовый пиксель sky=0, к которому стягивает {@code pjm_darkenNight}.
     * В {@code updateLightTexture} два вызова {@code OptionInstance.get()}:
     * ordinal 0 — darknessEffectScale, ordinal 1 — gamma (нужный).
     */
    @ModifyExpressionValue(
            method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 1))
    private Object pjm_neutralizeGammaAtNight(Object original) {
        if (!(original instanceof Double gamma)) return original;
        return gamma * Math.max(0.0F, 1.0F - pjm_nightFactor());
    }

    /** 0 днём … 1 (и выше при intensity &gt; 1) глухой ночью; 0 — если темнота неприменима. */
    @Unique
    private float pjm_nightFactor() {
        if (!Config.isNightDarknessEnabled()) return 0.0F;
        ClientLevel level = this.minecraft.level;
        if (level == null || !level.dimensionType().hasSkyLight() || level.effects().forceBrightLightmap()) return 0.0F;
        if (level.getSkyFlashTime() > 0) return 0.0F;

        // getSkyDarken: 1.0 днём … 0.2 глухой ночью → night: 0 днём … 1 ночью
        return Mth.clamp((1.0F - level.getSkyDarken(1.0F)) / 0.8F, 0.0F, 1.0F)
                * (float) Config.getNightDarknessIntensity();
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
