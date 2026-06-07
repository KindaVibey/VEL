package net.vibey.vel.internal.assemblies.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.vibey.vel.internal.assemblies.AssemblyBlock;

public class AssemblyFakeLevel implements BlockAndTintGetter {

    private final Map<BlockPos, BlockState> blockMap;
    // Store real-world position each relative pos maps to, for light + tint sampling
    private final Map<BlockPos, BlockPos> relativeToWorld;
    private final BlockPos entityPos;

    public AssemblyFakeLevel(List<AssemblyBlock> blocks, BlockPos entityPos) {
        this.entityPos = entityPos;
        this.blockMap = blocks.stream()
                .collect(Collectors.toMap(AssemblyBlock::relativePos, AssemblyBlock::state));
        // Pre-compute world pos for each relative pos once
        this.relativeToWorld = blocks.stream()
                .collect(Collectors.toMap(
                        AssemblyBlock::relativePos,
                        b -> entityPos.offset(b.relativePos())
                ));
    }

    private BlockPos toWorld(BlockPos relativePos) {
        // For positions outside the assembly (neighbor lookups for AO), offset directly
        return relativeToWorld.getOrDefault(relativePos, entityPos.offset(relativePos));
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return blockMap.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        // Exactly match vanilla's directional shading values
        if (!shade) return 1.0f;
        return switch (direction) {
            case DOWN -> 0.5f;
            case UP -> 1.0f;
            case NORTH, SOUTH -> 0.8f;
            case WEST, EAST -> 0.6f;
        };
    }

    @Override
    public int getBrightness(LightLayer lightLayer, BlockPos pos) {
        var level = Minecraft.getInstance().level;
        if (level == null) return 0;
        // Sample real light at the actual world position this block came from
        return level.getBrightness(lightLayer, toWorld(pos));
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        var level = Minecraft.getInstance().level;
        if (level == null) return 0;
        return level.getRawBrightness(toWorld(pos), amount);
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        var level = Minecraft.getInstance().level;
        if (level == null) return -1;
        // Sample biome tint from real world position
        return level.getBlockTint(toWorld(pos), colorResolver);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return Minecraft.getInstance().level.getLightEngine();
    }

    @Override
    public int getHeight() { return 256; }

    @Override
    public int getMinBuildHeight() { return 0; }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) { return null; }
}