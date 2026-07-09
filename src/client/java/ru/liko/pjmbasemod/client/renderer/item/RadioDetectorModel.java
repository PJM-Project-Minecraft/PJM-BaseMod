package ru.liko.pjmbasemod.client.renderer.item;

import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.item.RadioDetectorItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;

/**
 * GeckoLib-модель Радио-детектора. Авто-резолвит ресурсы по пути {@code pjmbasemod:radio_detector}:
 * geo/item/radio_detector.geo.json, textures/item/radio_detector.png, animations/item/radio_detector.animation.json.
 */
public class RadioDetectorModel extends DefaultedItemGeoModel<RadioDetectorItem> {

    public RadioDetectorModel() {
        super(ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "radio_detector"));
    }
}
