package ru.liko.pjmbasemod.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.customization.PlayerSkinClientCache;

/**
 * Подменяет скин игрока на командный из {@link PlayerSkinClientCache}: и текстуру, и тип модели.
 * Заменяет {@code getSkin()} целиком, выставляя {@link PlayerSkin.Model#WIDE} (классическая
 * default-модель Steve, 64×64), поскольку все скины мода нарисованы под неё. Так у любого игрока
 * — даже со slim-скином аккаунта — отображается одинаковая default-модель.
 *
 * <p>Клиент-only миксин (объявлен в массиве {@code "client"} в {@code pjmbasemod.mixins.json}),
 * на dedicated-сервере не применяется. Cape/elytra и прочие поля исходного скина сохраняются.</p>
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void pjm_overrideSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        String skin = PlayerSkinClientCache.get(self.getUUID());
        if (skin == null || skin.isBlank()) return;

        ResourceLocation texture = ResourceLocation.tryBuild(Pjmbasemod.MODID, "textures/skins/" + skin + ".png");
        if (texture == null) return;

        PlayerSkin original = cir.getReturnValue();
        if (original == null) return;

        cir.setReturnValue(new PlayerSkin(
                texture,
                original.textureUrl(),
                original.capeTexture(),
                original.elytraTexture(),
                PlayerSkin.Model.WIDE,
                original.secure()));
    }
}
