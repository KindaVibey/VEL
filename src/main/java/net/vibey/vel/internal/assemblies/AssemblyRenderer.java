package net.vibey.vel.internal.assemblies;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
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
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                Quaternionf rotation = new Quaternionf();
                rotation.rotateY((float) Math.toRadians(-entity.getYRot()));
                Matrix3f normalMat = new Matrix3f().rotate(rotation);

                var enableUniform = shader.getUniform("AssemblyEnableNormalLighting");
                var normalMatUniform = shader.getUniform("AssemblyNormalMat");

                if (enableUniform != null) enableUniform.set(1.0f);
                if (normalMatUniform != null) normalMatUniform.set(normalMat);

                mesh.draw(poseStack, RenderSystem.getProjectionMatrix());

                if (enableUniform != null) enableUniform.set(0.0f);
            } else {
                mesh.draw(poseStack, RenderSystem.getProjectionMatrix());
            }
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private float getYRot(T entity, float partialTick) {
        return entity.getYRot();
    }
}