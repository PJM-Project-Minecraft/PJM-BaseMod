package ru.liko.pjmbasemod.client.renderer.entity;

import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.entity.NotebookEntity;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * GeckoLib-модель терминала. Авто-резолвит ресурсы по пути {@code pjmbasemod:notebook}:
 * geo/entity/notebook.geo.json, textures/entity/notebook.png, animations/entity/notebook.animation.json.
 */
public class NotebookModel extends DefaultedEntityGeoModel<NotebookEntity> {

    public NotebookModel() {
        super(ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "notebook"));
    }
}
