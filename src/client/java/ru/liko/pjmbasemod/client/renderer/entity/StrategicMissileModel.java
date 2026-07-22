package ru.liko.pjmbasemod.client.renderer.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.entity.StrategicMissileEntity;
import software.bernie.geckolib.model.GeoModel;

/** Динамически выбирает модель/текстуру по синхронизированному id профиля ракеты. */
public final class StrategicMissileModel extends GeoModel<StrategicMissileEntity> {

    private static final String FALLBACK_ID = "kh_101";
    private static final ResourceLocation ANIMATION = id("animations/entity/missile.animation.json");

    @Override
    public ResourceLocation getModelResource(StrategicMissileEntity entity) {
        ResourceLocation desired = id("geo/entity/missiles/" + safeId(entity) + ".geo.json");
        return exists(desired) ? desired : id("geo/entity/missiles/" + FALLBACK_ID + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(StrategicMissileEntity entity) {
        ResourceLocation desired = id("textures/entity/missiles/" + safeId(entity) + ".png");
        return exists(desired) ? desired : id("textures/entity/missiles/" + FALLBACK_ID + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(StrategicMissileEntity entity) {
        return ANIMATION;
    }

    private static String safeId(StrategicMissileEntity entity) {
        String value = entity.getMissileId();
        return value != null && value.matches("[a-z0-9_-]{1,64}") ? value : FALLBACK_ID;
    }

    private static boolean exists(ResourceLocation resource) {
        return Minecraft.getInstance().getResourceManager().getResource(resource).isPresent();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, path);
    }
}
