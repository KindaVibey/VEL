// AssemblyRenderRegion.java
package net.vibey.vel.internal.assemblies.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.vibey.vel.internal.assemblies.AssemblyBlock;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A fake BlockAndTintGetter for assembly rendering.
 *
 * Key improvements over the old AssemblyFakeLevel:
 * - 1-block border padding on all sides so AO and face culling work correctly
 *   for blocks at the edge of the assembly
 * - Light is sampled once at construction time from the real world at the
 *   assembly's current world position, then baked in — no per-tick sampling
 * - Block entities are collected here so the renderer can draw them
 * - getBlockTint delegates to the real world at the assembly's world position
 *   so biome-tinted blocks (grass, leaves, water) tint correctly
 *
 * Block map keys are floored BlockPos values derived from each AssemblyBlock's
 * double relative coordinates via relativeBlockPos().
 */
@OnlyIn(Dist.CLIENT)
public class AssemblyRenderRegion implements BlockAndTintGetter {

    // All block states in the assembly, keyed by floored relative BlockPos
    private final Map<BlockPos, BlockState> blockMap;

    // Packed light values sampled from the real world
    private final Map<BlockPos, Integer> blockLight;
    private final Map<BlockPos, Integer> skyLight;

    // Block entities collected during construction, stored by relative pos.
    private final Map<BlockPos, BlockEntity> blockEntities;

    // The world position of the assembly origin, used for light sampling
    // and biome tint lookups.
    private final BlockPos worldOrigin;
    private final ClientLevel level;

    /**
     * Build a render region from a list of assembly blocks.
     *
     * @param blocks      the assembly blocks (double relative positions + states)
     * @param worldOrigin the block position in the real world corresponding
     *                    to relative (0,0,0) — i.e. the assembly entity's
     *                    block position
     */
    public AssemblyRenderRegion(List<AssemblyBlock> blocks, BlockPos worldOrigin) {
        this.worldOrigin = worldOrigin;
        this.level = Minecraft.getInstance().level;

        int capacity = blocks.size() * 4;
        this.blockMap = new HashMap<>(capacity);
        this.blockLight = new HashMap<>(capacity);
        this.skyLight = new HashMap<>(capacity);
        this.blockEntities = new HashMap<>();

        if (this.level == null) return;

        // First pass: populate the block map using floored relative BlockPos
        for (AssemblyBlock ab : blocks) {
            blockMap.put(ab.relativeBlockPos(), ab.state());
        }

        // Second pass: sample light for every block AND its 6 neighbors.
        for (AssemblyBlock ab : blocks) {
            BlockPos rel = ab.relativeBlockPos();

            sampleLightAt(rel);

            for (Direction dir : Direction.values()) {
                sampleLightAt(rel.relative(dir));
            }

            // Collect block entity if this block has one.
            if (ab.state().hasBlockEntity()) {
                BlockEntity be = createBlockEntity(rel, ab.state());
                if (be != null) {
                    blockEntities.put(rel, be);
                }
            }
        }
    }

    /**
     * Sample block and sky light at a relative position by mapping it to
     * the real world and asking the real light engine.
     */
    private void sampleLightAt(BlockPos rel) {
        if (blockLight.containsKey(rel)) return;

        BlockPos world = worldOrigin.offset(rel);
        blockLight.put(rel, level.getBrightness(LightLayer.BLOCK, world));
        skyLight.put(rel,   level.getBrightness(LightLayer.SKY,   world));
    }

    /**
     * Attempt to create a block entity for a given relative position and state.
     * This is what makes chests, beds, banners, etc. render correctly.
     */
    @Nullable
    private BlockEntity createBlockEntity(BlockPos relPos, BlockState state) {
        try {
            BlockPos worldPos = worldOrigin.offset(relPos);
            return BlockEntity.loadStatic(worldPos, state, new net.minecraft.nbt.CompoundTag(),
                    level.registryAccess());
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // BlockAndTintGetter implementation
    // -------------------------------------------------------------------------

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
            case DOWN          -> 0.5f;
            case UP            -> 1.0f;
            case NORTH, SOUTH  -> 0.8f;
            case WEST, EAST    -> 0.6f;
        };
    }

    @Override
    public int getBrightness(LightLayer lightLayer, BlockPos pos) {
        return switch (lightLayer) {
            case BLOCK -> blockLight.getOrDefault(pos, 0);
            case SKY   -> skyLight.getOrDefault(pos,   0);
        };
    }

    @Override
    public int getRawBrightness(BlockPos pos, int skyDarken) {
        int block = blockLight.getOrDefault(pos, 0);
        int sky   = Math.max(0, skyLight.getOrDefault(pos, 0) - skyDarken);
        return Math.max(block, sky);
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        if (level == null) return 0xFFFFFF;
        return level.getBlockTint(worldOrigin.offset(pos), colorResolver);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return level != null ? level.getLightEngine() : null;
    }

    @Override
    public int getHeight() {
        return level != null ? level.getHeight() : 256;
    }

    @Override
    public int getMinBuildHeight() {
        return level != null ? level.getMinBuildHeight() : -64;
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return blockEntities.get(pos);
    }

    // -------------------------------------------------------------------------
    // Accessors for the compiler / renderer
    // -------------------------------------------------------------------------

    public Map<BlockPos, BlockState> getBlockMap() {
        return blockMap;
    }

    public Map<BlockPos, BlockEntity> getBlockEntities() {
        return blockEntities;
    }

    /**
     * Refresh light data from the real world. Called when the assembly moves
     * far enough that the sampled light is stale.
     */
    public void refreshLight(BlockPos newWorldOrigin) {
        blockLight.clear();
        skyLight.clear();
        for (BlockPos rel : blockMap.keySet()) {
            sampleLightAtNewOrigin(rel, newWorldOrigin);
            for (Direction dir : Direction.values()) {
                sampleLightAtNewOrigin(rel.relative(dir), newWorldOrigin);
            }
        }
    }

    private void sampleLightAtNewOrigin(BlockPos rel, BlockPos origin) {
        if (blockLight.containsKey(rel)) return;
        if (level == null) return;
        BlockPos world = origin.offset(rel);
        blockLight.put(rel, level.getBrightness(LightLayer.BLOCK, world));
        skyLight.put(rel,   level.getBrightness(LightLayer.SKY,   world));
    }
}