package net.vibey.vpl.client;

import net.vibey.vpl.VPL;
import net.vibey.vpl.entity.ModEntityTypes;
import net.vibey.vpl.lib.AbstractProjectileRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = VPL.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModEntityTypes.BULLET.get(),
                ctx -> new AbstractProjectileRenderer<>(
                        ctx,
                        new BulletModel(ctx.bakeLayer(BulletModel.LAYER_LOCATION)),
                        ResourceLocation.fromNamespaceAndPath("vpl", "textures/entity/bullet.png")
                )
        );
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BulletModel.LAYER_LOCATION, BulletModel::createBodyLayer);
    }
}