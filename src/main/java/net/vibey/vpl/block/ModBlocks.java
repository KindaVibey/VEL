package net.vibey.vpl.block;

import net.vibey.vpl.VPL;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(VPL.MOD_ID);

    public static final DeferredBlock<GunTurretBlock> GUN_TURRET =
            BLOCKS.register("gun_turret",
                    () -> new GunTurretBlock(BlockBehaviour.Properties.of()
                            .strength(3.0f)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}