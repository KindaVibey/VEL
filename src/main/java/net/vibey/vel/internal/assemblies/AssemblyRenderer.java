package net.vibey.vel.internal.assemblies;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.vibey.vel.internal.assemblies.entity.AssemblyEntity;
import net.vibey.vel.internal.assemblies.render.AssemblyBakedMesh;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class AssemblyRenderer<T extends AssemblyEntity> extends EntityRenderer<T> {

    public AssemblyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        AssemblyBakedMesh mesh = entity.getOrBuildMesh();
        if (mesh.isBuilt()) {
            Quaternionf interpolated = new Quaternionf(entity.prevRotation)
                    .slerp(entity.getRotation(), partialTick);

            Vector3f pivot = entity.getPivot();

            poseStack.pushPose();
            poseStack.translate(pivot.x, pivot.y, pivot.z);   // move to pivot
            poseStack.mulPose(interpolated);                    // rotate around pivot
            poseStack.translate(-pivot.x, -pivot.y, -pivot.z); // move back

            Matrix3f normalMat = new Matrix3f().rotate(interpolated);
            mesh.draw(poseStack, RenderSystem.getProjectionMatrix(), normalMat);

            poseStack.popPose();
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}