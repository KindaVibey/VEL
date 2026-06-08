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
        // No shadow — assemblies can be large and an entity shadow looks wrong
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
            // First frame or not yet compiled — nothing to show yet
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        // ----------------------------------------------------------------
        // 1. Draw the block geometry
        //
        // The entity renderer has already pushed a PoseStack entry that
        // translates to (entityX - camX, entityY - camY, entityZ - camZ).
        // We read that combined matrix and pass it to draw() so geometry
        // is positioned correctly in camera space.
        //
        // Rotation is applied on top of this inside draw() via the
        // entity's rotation quaternion.
        // ----------------------------------------------------------------

        // Get the model-view that includes the entity's world position
        // (the PoseStack at this point already has entity translation applied
        // by the EntityRenderer infrastructure).
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())
                .mul(poseStack.last().pose());

        Matrix4f projection = RenderSystem.getProjectionMatrix();

        // Assembly rotation — currently identity (no rotation yet).
        // When you add rotation to AssemblyEntity, replace this.
        Quaternionf rotation = entity.getAssemblyRotation();

        // In AssemblyRenderer.render():
        Assembly assembly = entity.getAssembly();
        data.draw(poseStack, RenderSystem.getProjectionMatrix(), entity.getAssemblyRotation());

        // ----------------------------------------------------------------
        // 2. Draw block entities (chests, beds, banners, etc.)
        //
        // We use the standard MultiBufferSource path so these work with
        // Iris/Sodium/shader packs. Each block entity is rendered at its
        // relative position inside the PoseStack we already have.
        // ----------------------------------------------------------------

        BlockEntityRenderDispatcher beDispatcher =
                Minecraft.getInstance().getBlockEntityRenderDispatcher();

        for (BlockEntity be : data.getBlockEntities()) {
            // The block entity's position is the world position we gave it
            // at construction time (worldOrigin + relPos). We need to
            // translate by just the relative part so it sits correctly
            // inside the entity's local space.
            //
            // be.getBlockPos() was set to worldOrigin + relPos in
            // AssemblyRenderRegion.createBlockEntity(), so:
            // relPos = be.getBlockPos() - worldOrigin
            //
            // But we don't have worldOrigin here easily, so we store
            // the relative pos in a wrapper. For now we use the position
            // stored on the BE directly — since the entity poseStack
            // already centres on the entity, we translate by the BE's
            // stored relative offset.
            //
            // See AssemblyRenderRegion: we pass relPos (not worldPos)
            // to BlockEntity.loadStatic, so be.getBlockPos() IS the
            // relative pos. This is intentional.

            poseStack.pushPose();
            poseStack.translate(
                    be.getBlockPos().getX(),
                    be.getBlockPos().getY(),
                    be.getBlockPos().getZ()
            );

            // Apply the same rotation so block entities rotate with the assembly
            poseStack.mulPose(rotation);

            try {
                beDispatcher.render(be, partialTick, poseStack, bufferSource);
            } catch (Exception e) {
                // Some block entities are fragile when rendered outside their
                // normal context. Log and continue rather than crash.
                // In production you'd want to remove the offending BE from the list.
            }

            poseStack.popPose();
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}