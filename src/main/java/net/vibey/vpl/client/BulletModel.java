package net.vibey.vpl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.vibey.vpl.entity.BulletEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

public class BulletModel extends EntityModel<BulletEntity> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("vpl", "bullet"), "main");

    private final ModelPart bb_main;

    public BulletModel(ModelPart root) {
        this.bb_main = root.getChild("bb_main");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("bb_main",
                CubeListBuilder.create()
                        .texOffs(-4, -4)
                        .addBox(-1f, -1f, -3f, 2f, 2f, 6f, new CubeDeformation(0f)),
                PartPose.offset(0f, 0f, 0f));
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void setupAnim(BulletEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {}

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vc,
                               int packedLight, int packedOverlay,
                               float r, float g, float b, float a) {
        bb_main.render(poseStack, vc, packedLight, packedOverlay, r, g, b, a);
    }
}