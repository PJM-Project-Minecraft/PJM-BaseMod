package ru.liko.pjmbasemod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import ru.liko.pjmbasemod.common.entity.NotebookEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class NotebookRenderer extends GeoEntityRenderer<NotebookEntity> {

    public NotebookRenderer(EntityRendererProvider.Context context) {
        super(context, new NotebookModel());
    }

    @Override
    public void render(NotebookEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        poseStack.pushPose();

        // Применяем поворот сущности к модели (интерполяция для плавности)
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }
}
