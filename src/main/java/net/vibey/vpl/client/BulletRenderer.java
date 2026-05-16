package net.vibey.vpl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.vibey.vpl.entity.BulletEntity;

@OnlyIn(Dist.CLIENT)
public class BulletRenderer extends EntityRenderer<BulletEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("vpl", "textures/entity/bullet.png");

    private final BulletModel model;

    public BulletRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
        this.model = new BulletModel(context.bakeLayer(BulletModel.LAYER_LOCATION));
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void render(BulletEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        // Because the bullet simulates on both sides, getDeltaMovement() is always
        // accurate on the client — no interpolation lag, perfectly smooth rendering.
        Vec3 velocity = entity.getDeltaMovement();

        float pitch;
        float yaw;

        double horizontalDist = velocity.horizontalDistance();
        if (horizontalDist < 0.001) {
            pitch = velocity.y > 0 ? -90.0f : 90.0f;
            yaw   = 0.0f;
        } else {
            pitch = -(float) (Mth.atan2(velocity.y, horizontalDist) * (180.0 / Math.PI));
            yaw   =  (float) (Mth.atan2(velocity.x, velocity.z)    * (180.0 / Math.PI));
        }

        poseStack.translate(0.0, 1.0 / 16.0, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        // NeoForge / MC 1.21: pass packed ARGB white (0xFFFFFFFF) instead of four floats
        model.renderToBuffer(poseStack, vertexConsumer, packedLight,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(BulletEntity entity) {
        return TEXTURE;
    }
}
