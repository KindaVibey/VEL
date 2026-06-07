package net.vibey.vel.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.vibey.vel.VEL;
import net.vibey.vel.internal.assemblies.entity.AssemblyEntity;
import net.vibey.vel.network.AssemblySyncPayload;

@EventBusSubscriber(modid = VEL.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ServerEvents {

    @SubscribeEvent
    public static void onPlayerStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof AssemblyEntity assemblyEntity)) return;
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;

        PacketDistributor.sendToPlayer(
                serverPlayer,
                AssemblySyncPayload.of(
                        assemblyEntity.getId(),
                        assemblyEntity.getAssembly().getBlocks()
                )
        );
    }
}