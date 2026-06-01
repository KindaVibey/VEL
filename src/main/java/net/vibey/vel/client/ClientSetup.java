package net.vibey.vel.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.vibey.vel.VEL;
import net.vibey.vel.entity.ModEntityTypes;

@EventBusSubscriber(modid = VEL.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.BULLET.get(), BulletRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BulletModel.LAYER_LOCATION, BulletModel::createBodyLayer);
    }
}