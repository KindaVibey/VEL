package net.vibey.vel.internal.assemblies;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record AssemblyBlock(
        double relX,
        double relY,
        double relZ,
        BlockState state
) {
    /** Convenience: get the floored BlockPos for light sampling, block maps, etc. */
    public BlockPos relativeBlockPos() {
        return new BlockPos(
                (int) Math.floor(relX),
                (int) Math.floor(relY),
                (int) Math.floor(relZ)
        );
    }
}