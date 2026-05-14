package net.vibey.vpl.lib;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Drop-in renderer for any {@link AbstractProjectileEntity}.
 *
 * <ul>
 *   <li>Uses {@link AbstractProjectileEntity#getRenderPosition} for sub-tick lerp smoothing.</li>
 *   <li>Orients the model to match velocity direction (yaw + pitch) every frame.</li>
 *   <li>Delegates geometry to the {@link EntityModel} you supply at construction.</li>
 * </ul>
 *
 * <h2>Registration example (in your ClientSetup class)</h2>
 * <pre>{@code
 * @SubscribeEvent
 * public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
 *     event.registerEntityRenderer(
 *         ModEntityTypes.ROCKET.get(),
 *         ctx -> new AbstractProjectileRenderer<>(
 *             ctx,
 *             new RocketModel(ctx.bakeLayer(RocketModel.LAYER_LOCATION)),
 *             ResourceLocation.fromNamespaceAndPath("mymod", "textures/entity/rocket.png")
 *         )
 *     );
 * }
 * }</pre>
 *
 * @param <T> the projectile entity type being rendered
 */
public class AbstractProjectileRenderer<T extends AbstractProjectileEntity>
        extends EntityRenderer<T> {

    private final EntityModel<T> model;
    private final ResourceLocation texture;

    public AbstractProjectileRenderer(EntityRendererProvider.Context context,
                                      EntityModel<T> model,
                                      ResourceLocation texture) {
        super(context);
        this.model        = model;
        this.texture      = texture;
        this.shadowRadius = 0f;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        // Sub-tick smooth position: offset the render origin from the server-snapped
        // entity position to the lerped render position for smooth motion.
        Vec3 renderPos = entity.getRenderPosition(partialTicks);
        Vec3 entityPos = entity.position();
        Vec3 offset    = renderPos.subtract(entityPos);
        poseStack.translate(offset.x, offset.y, offset.z);

        // Orient model to velocity
        Vec3 vel = entity.getDeltaMovement();
        poseStack.mulPose(Axis.YP.rotationDegrees(ProjectilePhysics.yawFromVelocity(vel)));
        poseStack.mulPose(Axis.XP.rotationDegrees(ProjectilePhysics.pitchFromVelocity(vel)));

        // Render geometry
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutout(texture));
        model.setupAnim(entity, 0f, 0f, entity.tickCount, 0f, 0f);
        model.renderToBuffer(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY,
                1f, 1f, 1f, 1f);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }
}