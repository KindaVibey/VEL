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

        // True floating point center of the selection
        double centerX = min.getX() + (max.getX() - min.getX()) / 2.0;
        double centerY = min.getY() + (max.getY() - min.getY() + 1) / 2.0;
        double centerZ = min.getZ() + (max.getZ() - min.getZ()) / 2.0;

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                // Round to nearest integer offset from the float center
                int rx = (int) Math.floor(pos.getX() - centerX);
                int ry = (int) Math.floor(pos.getY() - centerY + 0.5);
                int rz = (int) Math.floor(pos.getZ() - centerZ);
                blocks.add(new AssemblyBlock(new BlockPos(rx, ry, rz), state));
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