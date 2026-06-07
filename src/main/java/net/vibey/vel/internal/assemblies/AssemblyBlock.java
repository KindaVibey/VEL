package net.vibey.vel.internal.assemblies;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record AssemblyBlock(
        BlockPos relativePos,
        BlockState state
) { }
