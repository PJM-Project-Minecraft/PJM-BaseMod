package ru.liko.pjmbasemod.client.renderer.item;

import ru.liko.pjmbasemod.common.item.RadioDetectorItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Рендерер Радио-детектора (BEWLR) — для отображения в руке, инвентаре и мире.
 */
public class RadioDetectorRenderer extends GeoItemRenderer<RadioDetectorItem> {

    public RadioDetectorRenderer() {
        super(new RadioDetectorModel());
    }
}
