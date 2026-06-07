package net.vibey.vel;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.vibey.vel.block.ModBlocks;
import net.vibey.vel.entity.ModEntityTypes;
import net.vibey.vel.item.ModItems;
import net.vibey.vel.api.particle.ModParticles;
import net.vibey.vel.network.ModNetwork;

@Mod(VEL.MOD_ID)
public class VEL {
    public static final String MOD_ID = "vel";

    public VEL(IEventBus modEventBus) {
        ModEntityTypes.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModParticles.register(modEventBus);
        ModNetwork.register(modEventBus);
    }
}