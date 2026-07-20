package ru.liko.pjmbasemod.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import ru.liko.pjmbasemod.common.block.RemkaBlock;
import ru.liko.pjmbasemod.common.blockentity.RemkaBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Рендер ремонтной станции. Поворот по {@code HORIZONTAL_FACING} {@link GeoBlockRenderer}
 * делает сам, поэтому здесь остаётся только табличка «Ремонтная Станция»: у BlockEntity,
 * в отличие от сущности, своего nametag нет — рисуем вручную.
 */
public class RemkaBlockRenderer extends GeoBlockRenderer<RemkaBlockEntity> {

    /** Высота таблички над основанием станции, в блоках (модель ~2.8 блока). */
    private static final float LABEL_HEIGHT = 3.2F;

    /** Дальше этого расстояния (в блоках) подпись не рисуем, чтобы не засорять горизонт. */
    private static final double LABEL_MAX_DISTANCE_SQR = 32.0 * 32.0;

    public RemkaBlockRenderer() {
        super(new RemkaGeoModel());
    }

    /**
     * Дефолтный бокс BER — куб 1x1x1 у ядра: модель на {@link RemkaBlock#HEIGHT} блоков и подпись
     * над ней отсекались фрустумом, стоило посмотреть на верх станции. Расширяем на весь
     * мультиблок плюс запас под табличку.
     */
    @Override
    public AABB getRenderBoundingBox(RemkaBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos())
                .inflate(RemkaBlock.RADIUS)
                .expandTowards(0.0, LABEL_HEIGHT + 1.0, 0.0);
    }

    @Override
    public void render(RemkaBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        super.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        renderLabel(blockEntity, poseStack, bufferSource, packedLight);
    }

    private void renderLabel(RemkaBlockEntity blockEntity, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (minecraft.player.blockPosition().distSqr(blockEntity.getBlockPos()) > LABEL_MAX_DISTANCE_SQR) return;

        Component label = Component.translatable("block.pjmbasemod.remka");
        Font font = minecraft.font;

        poseStack.pushPose();
        // Центр блока по X/Z: BER рисует от угла блока, а станция симметрична относительно ядра.
        poseStack.translate(0.5F, LABEL_HEIGHT, 0.5F);
        // Разворот к камере + переход в экранный масштаб шрифта (Y вниз, отсюда минусы).
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        float x = -font.width(label) / 2.0F;
        int background = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;

        font.drawInBatch(label, x, 0.0F, 0xFFFFFF, false, poseStack.last().pose(), bufferSource,
                Font.DisplayMode.NORMAL, background, packedLight);

        poseStack.popPose();
    }
}
