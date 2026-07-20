package ru.liko.pjmbasemod.client.renderer.block;

import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.blockentity.RemkaBlockEntity;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

/**
 * GeckoLib-модель ремонтной станции. Авто-резолвит ресурсы по пути {@code pjmbasemod:remka}:
 * geo/block/remka.geo.json, textures/block/remka.png, animations/block/remka.animation.json.
 */
public class RemkaGeoModel extends DefaultedBlockGeoModel<RemkaBlockEntity> {

    public RemkaGeoModel() {
        super(ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "remka"));
    }
}
