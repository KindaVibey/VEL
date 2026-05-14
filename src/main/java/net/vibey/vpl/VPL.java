package net.vibey.vpl;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.vibey.vpl.block.ModBlocks;
import net.vibey.vpl.entity.ModEntityTypes;
import net.vibey.vpl.item.ModItems;

@Mod(VPL.MOD_ID)
public class VPL {
    public static final String MOD_ID = "vpl";

    // NeoForge 1.21: IEventBus is injected directly into the constructor
    public VPL(IEventBus modEventBus) {
        ModEntityTypes.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
    }
}
