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
import net.minecraft.world.phys.Vec3;
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
 */
@OnlyIn(Dist.CLIENT)
public class AssemblyRenderRegion implements BlockAndTintGetter {

    // All block states in the assembly, keyed by relative BlockPos
    private final Map<BlockPos, BlockState> blockMap;

    // Packed light values (block << 4 | sky << 20 style — actually we store
    // block and sky separately and pack on demand via LightTexture format)
    // We store them as the raw integer getBrightness returns (0-15).
    private final Map<BlockPos, Integer> blockLight;
    private final Map<BlockPos, Integer> skyLight;

    // Block entities collected during construction, stored by relative pos.
    // These are FAKE block entities pointing at relative positions —
    // the renderer uses these for BlockEntityRenderer calls.
    private final Map<BlockPos, BlockEntity> blockEntities;

    // The world position of the assembly origin, used for light sampling
    // and biome tint lookups.
    private final BlockPos worldOrigin;
    private final ClientLevel level;

    /**
     * Build a render region from a list of assembly blocks.
     *
     * @param blocks      the assembly blocks (relative positions + states)
     * @param worldOrigin the block position in the real world corresponding
     *                    to relative (0,0,0) — i.e. the assembly entity's
     *                    block position
     */
    public AssemblyRenderRegion(List<AssemblyBlock> blocks, BlockPos worldOrigin) {
        this.worldOrigin = worldOrigin;
        this.level = Minecraft.getInstance().level;

        // Size hints — blocks + neighbor padding (roughly 6 neighbors each,
        // but many shared, so 3x is a reasonable over-estimate)
        int capacity = blocks.size() * 4;
        this.blockMap = new HashMap<>(capacity);
        this.blockLight = new HashMap<>(capacity);
        this.skyLight = new HashMap<>(capacity);
        this.blockEntities = new HashMap<>();

        if (this.level == null) return;

        // First pass: populate the block map
        for (AssemblyBlock ab : blocks) {
            blockMap.put(ab.relativePos(), ab.state());
        }

        // Second pass: sample light for every block AND its 6 neighbors.
        // We must include neighbors because the block renderer queries
        // neighbor light for AO and smooth lighting on face edges.
        for (AssemblyBlock ab : blocks) {
            BlockPos rel = ab.relativePos();

            // Sample the block itself
            sampleLightAt(rel);

            // Sample all 6 face neighbors (the border padding)
            for (Direction dir : Direction.values()) {
                sampleLightAt(rel.relative(dir));
            }

            // Collect block entity if this block has one.
            // We ask the real world at the corresponding world position.
            // If a block entity exists there (it shouldn't since we removed
            // blocks, but in case of creative/command shenanigans), or if
            // the block state simply has a block entity type, we create a
            // dummy one. In practice for assemblies we construct a fresh
            // block entity from the state so chests/beds render correctly.
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
        if (blockLight.containsKey(rel)) return; // already sampled

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
            // BlockEntity.create wants an absolute position — we use relative
            // here because the renderer will translate to it. The level
            // reference is this fake region... but BlockEntityRenderer only
            // needs the pos and state for most renderers.
            // We pass a real world position for the level so things like
            // chests can check if they are blocked.
            BlockPos worldPos = worldOrigin.offset(relPos);
            return BlockEntity.loadStatic(worldPos, state, new net.minecraft.nbt.CompoundTag(),
                    level.registryAccess());
        } catch (Exception e) {
            // Some block entities don't like being created without NBT.
            // That's fine — we just skip them; they won't render but that
            // is better than crashing.
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

    /**
     * Directional shading — vanilla values, consistent with how the block
     * renderer expects them.
     */
    @Override
    public float getShade(Direction direction, boolean shade) {
        if (!shade) return 1.0f;
        return switch (direction) {
            case DOWN        -> 0.5f;
            case UP          -> 1.0f;
            case NORTH, SOUTH -> 0.8f;
            case WEST, EAST  -> 0.6f;
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

    /**
     * Biome tint (grass colour, foliage colour, water colour).
     * We delegate to the real world at the world-space position so tinted
     * blocks like grass and leaves show the correct biome colour.
     */
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

    /**
     * Return our baked block entity for this relative position, if any.
     * The block entity renderer uses this to draw chests, beds, etc.
     */
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

    /**
     * All block entities in this assembly (relative positions).
     */
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