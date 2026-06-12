package ru.liko.pjmbasemod.client.renderer.entity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.entity.QuartermasterEntity;

/**
 * Рендер NPC-кладовщика ванильной player-моделью. Скин берётся из
 * {@code assets/pjmbasemod/textures/skins/<skinId>.png}.
 */
public class QuartermasterRenderer extends MobRenderer<QuartermasterEntity, PlayerModel<QuartermasterEntity>> {

    public QuartermasterRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(QuartermasterEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID,
                "textures/skins/" + entity.getSkinId() + ".png");
    }
}
