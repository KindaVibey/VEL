package net.vibey.vel.internal.assemblies;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class Assembly {

    protected final List<AssemblyBlock> blocks;

    public Assembly(List<AssemblyBlock> blocks) {
        this.blocks = blocks;
    }

    public List<AssemblyBlock> getBlocks() {
        return blocks;
    }

    public static Assembly capture(Level level, BlockPos min, BlockPos max) {
        List<AssemblyBlock> blocks = new ArrayList<>();

        int originX = (int) Math.floor(min.getX() + (max.getX() - min.getX() + 1) / 2.0);
        int originY = (int) Math.floor(min.getY() + (max.getY() - min.getY()) / 2.0);
        int originZ = (int) Math.floor(min.getZ() + (max.getZ() - min.getZ() + 1) / 2.0);
        BlockPos origin = new BlockPos(originX, originY, originZ);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                blocks.add(new AssemblyBlock(pos.subtract(origin), state));
            }
        }

        return new Assembly(blocks);
    }

    public void removeBlocks(Level level, BlockPos min, BlockPos max) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}

//test