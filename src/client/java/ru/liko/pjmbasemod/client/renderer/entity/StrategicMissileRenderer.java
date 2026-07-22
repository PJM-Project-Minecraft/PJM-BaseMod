package ru.liko.pjmbasemod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import ru.liko.pjmbasemod.common.entity.StrategicMissileEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Ориентирует продольную ось GeoJSON по фактическому вектору полёта. */
public final class StrategicMissileRenderer extends GeoEntityRenderer<StrategicMissileEntity> {

    public StrategicMissileRenderer(EntityRendererProvider.Context context) {
        super(context, new StrategicMissileModel());
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(StrategicMissileEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }
}
