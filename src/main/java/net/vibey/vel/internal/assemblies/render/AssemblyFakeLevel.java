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
import net.vibey.vel.internal.assemblies.AssemblyBlock;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class AssemblyFakeLevel implements BlockAndTintGetter {

    private final Map<BlockPos, BlockState> blockMap;
    private final Map<BlockPos, Integer> blockLightMap;
    private final Map<BlockPos, Integer> skyLightMap;
    private final BlockPos entityPos;
    private final net.minecraft.client.multiplayer.ClientLevel level;

    public AssemblyFakeLevel(List<AssemblyBlock> blocks, BlockPos entityPos) {
        this.entityPos = entityPos;
        this.level = Minecraft.getInstance().level;

        this.blockMap = new HashMap<>(blocks.size());
        this.blockLightMap = new HashMap<>(blocks.size() * 2);
        this.skyLightMap = new HashMap<>(blocks.size() * 2);

        if (this.level == null) return;

        for (AssemblyBlock block : blocks) {
            BlockPos rel = block.relativePos();
            this.blockMap.put(rel, block.state());

            // Sample neighbors too for face culling and AO
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = rel.relative(dir);
                if (!blockLightMap.containsKey(neighbor)) {
                    BlockPos world = entityPos.offset(neighbor);
                    blockLightMap.put(neighbor, level.getBrightness(LightLayer.BLOCK, world));
                    skyLightMap.put(neighbor, level.getBrightness(LightLayer.SKY, world));
                }
            }

            BlockPos world = entityPos.offset(rel);
            blockLightMap.put(rel, level.getBrightness(LightLayer.BLOCK, world));
            skyLightMap.put(rel, level.getBrightness(LightLayer.SKY, world));
        }
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
        return switch (lightLayer) {
            case BLOCK -> blockLightMap.getOrDefault(pos, 0);
            case SKY -> skyLightMap.getOrDefault(pos, 0);
        };
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        int block = blockLightMap.getOrDefault(pos, 0);
        int sky = Math.max(0, skyLightMap.getOrDefault(pos, 0) - amount);
        return Math.max(block, sky);
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        if (level == null) return -1;
        return level.getBlockTint(entityPos.offset(pos), colorResolver);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return level.getLightEngine();
    }

    @Override
    public int getHeight() { return level != null ? level.getHeight() : 256; }

    @Override
    public int getMinBuildHeight() { return level != null ? level.getMinBuildHeight() : 0; }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) { return null; }
}