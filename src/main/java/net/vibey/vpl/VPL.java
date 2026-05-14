package net.vibey.vpl;

import net.vibey.vpl.block.ModBlocks;
import net.vibey.vpl.entity.ModEntityTypes;
import net.vibey.vpl.item.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(VPL.MOD_ID)
public class VPL {
    public static final String MOD_ID = "vpl";

    public VPL(IEventBus modEventBus) {
        ModEntityTypes.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
    }
}