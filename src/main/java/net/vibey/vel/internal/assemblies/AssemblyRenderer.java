// AssemblyRenderer.java  (full rewrite — replaces the existing file)
package net.vibey.vel.internal.assemblies;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.vibey.vel.internal.assemblies.entity.AssemblyEntity;
import net.vibey.vel.internal.assemblies.render.AssemblyRenderData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

@OnlyIn(Dist.CLIENT)
public class AssemblyRenderer<T extends AssemblyEntity> extends EntityRenderer<T> {

    public AssemblyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        AssemblyRenderData data = entity.getRenderData();
        if (data == null || !data.isBuilt()) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())
                .mul(poseStack.last().pose());

        Matrix4f projection = RenderSystem.getProjectionMatrix();

        Quaternionf rotation = entity.getAssemblyRotation();

        Assembly assembly = entity.getAssembly();
        data.draw(poseStack, RenderSystem.getProjectionMatrix(), entity.getAssemblyRotation());

        BlockEntityRenderDispatcher beDispatcher =
                Minecraft.getInstance().getBlockEntityRenderDispatcher();

        for (BlockEntity be : data.getBlockEntities()) {

            poseStack.pushPose();
            poseStack.translate(
                    be.getBlockPos().getX(),
                    be.getBlockPos().getY(),
                    be.getBlockPos().getZ()
            );

            poseStack.mulPose(rotation);

            try {
                beDispatcher.render(be, partialTick, poseStack, bufferSource);
            } catch (Exception e) {
            }

            poseStack.popPose();
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}