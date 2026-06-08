// AssemblyRenderData.java
package net.vibey.vel.internal.assemblies.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.vibey.vel.internal.assemblies.AssemblyBlock;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the GPU-side mesh data for a single AssemblyEntity.
 *
 * Design goals:
 * - Geometry is baked in LOCAL space (relative to assembly origin).
 *   No world position is baked in. This means rotation works correctly
 *   at draw time by just changing the model-view matrix.
 * - Section-based: blocks are grouped into 16³ sections. Only dirty
 *   sections are rebuilt. For most assemblies this doesn't matter much
 *   (small structures), but for large ones it avoids full rebuilds on
 *   light changes.
 * - Block entities are collected and exposed to the renderer separately.
 * - Light is sampled once per rebuild, not every tick.
 * - The transform (position + rotation) is applied entirely at draw time
 *   via MODEL_VIEW_MATRIX, never baked into vertex data.
 */
@OnlyIn(Dist.CLIENT)
public class AssemblyRenderData {

    // Render types we care about, in draw order
    private static final List<RenderType> RENDER_TYPES = List.of(
            RenderType.solid(),
            RenderType.cutout(),
            RenderType.cutoutMipped(),
            RenderType.translucent()
    );

    // Shared thread pool across all assemblies — bounded to avoid
    // thrashing on worlds with many assemblies
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() / 2, 4)),
            r -> {
                Thread t = new Thread(r, "vel-assembly-builder");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 2);
                return t;
            }
    );

    // GPU buffers, one per render type, uploaded on the main thread
    private final Map<RenderType, VertexBuffer> buffers = new LinkedHashMap<>();

    // Pending mesh data from the async compile, consumed on the main thread
    // during tick()
    private final AtomicReference<CompileResult> pendingResult = new AtomicReference<>(null);

    // The current async compile future so we can cancel stale work
    private Future<?> pendingFuture = null;

    // Whether we have at least one valid uploaded buffer
    private boolean built = false;

    // Block entities extracted during the last compile, for the renderer
    // to pass to BlockEntityRenderDispatcher
    private List<BlockEntity> blockEntities = Collections.emptyList();

    // The world-space origin used for the last light sample, so we can
    // decide when light is stale enough to warrant a rebuild
    private BlockPos lastLightOrigin = null;

    // How many blocks the assembly must move before we resample light.
    // 4 blocks is a reasonable threshold — close enough to be accurate,
    // far enough to avoid constant rebuilds while moving.
    private static final double LIGHT_RESAMPLE_DISTANCE_SQ = 4.0 * 4.0;

    // -------------------------------------------------------------------------

    /**
     * Request an async rebuild of all geometry. Safe to call from any thread;
     * the actual compile runs on EXECUTOR and the result is consumed on the
     * main thread in tick().
     *
     * @param blocks      the current assembly block list (will be snapshot'd)
     * @param entityPos   the current world position of the assembly entity
     */
    public void requestRebuild(List<AssemblyBlock> blocks, Vec3 entityPos) {
        if (blocks.isEmpty()) {
            dispose();
            return;
        }

        // Cancel any in-progress compile — data would be stale anyway
        if (pendingFuture != null && !pendingFuture.isDone()) {
            pendingFuture.cancel(true);
        }

        // Snapshot everything we need before handing off to the thread
        List<AssemblyBlock> snapshot = List.copyOf(blocks);
        BlockPos worldOrigin = BlockPos.containing(entityPos);
        lastLightOrigin = worldOrigin;

        pendingFuture = EXECUTOR.submit(() -> {
            if (Thread.currentThread().isInterrupted()) return;

            AssemblyRenderRegion region = new AssemblyRenderRegion(snapshot, worldOrigin);
            CompileResult result = compile(snapshot, region);
            pendingResult.set(result);
        });
    }

    /**
     * Request a light-only refresh without rebuilding geometry. Much cheaper
     * than a full rebuild — just resamples light and re-uploads the same
     * vertex data with updated light values baked in.
     *
     * In practice, because light is baked into vertex data by the block
     * renderer, we need to do a full geometry rebuild to update light.
     * So this just triggers requestRebuild. If we were using a custom
     * shader with a uniform for light we could skip the geometry rebuild,
     * but that's out of scope.
     */
    public void requestLightRefresh(List<AssemblyBlock> blocks, Vec3 entityPos) {
        requestRebuild(blocks, entityPos);
    }

    /**
     * Must be called on the main (render) thread each frame. Consumes any
     * pending compile result and uploads it to the GPU.
     */
    public void tick() {
        CompileResult result = pendingResult.getAndSet(null);
        if (result != null) {
            uploadResult(result);
        }
    }

    /**
     * Check if the assembly has moved far enough from the last light sample
     * position that we should trigger a light refresh rebuild.
     */
    public boolean needsLightRefresh(Vec3 currentPos) {
        if (lastLightOrigin == null) return false;
        double dx = currentPos.x - lastLightOrigin.getX();
        double dy = currentPos.y - lastLightOrigin.getY();
        double dz = currentPos.z - lastLightOrigin.getZ();
        return (dx * dx + dy * dy + dz * dz) > LIGHT_RESAMPLE_DISTANCE_SQ;
    }

    // -------------------------------------------------------------------------
    // Compilation (runs on EXECUTOR thread)
    // -------------------------------------------------------------------------

    private static CompileResult compile(List<AssemblyBlock> blocks,
                                         AssemblyRenderRegion region) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create();

        Map<RenderType, MeshData> meshes = new LinkedHashMap<>();
        List<BlockEntity> bes = new ArrayList<>();

        for (RenderType renderType : RENDER_TYPES) {
            if (Thread.currentThread().isInterrupted()) return null;

            ByteBufferBuilder byteBuffer = new ByteBufferBuilder(renderType.bufferSize());
            BufferBuilder builder = new BufferBuilder(
                    byteBuffer,
                    VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.BLOCK
            );

            boolean hasAny = false;

            for (AssemblyBlock ab : blocks) {
                if (Thread.currentThread().isInterrupted()) {
                    byteBuffer.close();
                    return null;
                }

                BlockPos rel = ab.relativePos();
                BlockState state = ab.state();
                if (state.isAir()) continue;

                // Check this block renders in this render type
                RenderType blockRenderType = ItemBlockRenderTypes.getChunkRenderType(state);
                if (blockRenderType != renderType) continue;

                // Translate the PoseStack to the block's LOCAL position.
                // This is the key: we translate by relativePos, NOT by
                // relativePos + entityWorldPos. The world transform is
                // applied at draw time.
                PoseStack ps = new PoseStack();
                ps.translate(rel.getX(), rel.getY(), rel.getZ());

                dispatcher.renderBatched(
                        state,
                        rel,       // position used for light lookup in region
                        region,
                        ps,
                        builder,
                        true,      // check sides (enables face culling)
                        random,
                        ModelData.EMPTY,
                        renderType
                );
                hasAny = true;
            }

            if (hasAny) {
                MeshData mesh = builder.build();
                if (mesh != null) {
                    meshes.put(renderType, mesh);
                } else {
                    byteBuffer.close();
                }
            } else {
                byteBuffer.close();
            }
        }

        // Collect block entities from the region
        bes.addAll(region.getBlockEntities().values());

        return new CompileResult(meshes, bes);
    }

    // -------------------------------------------------------------------------
    // Upload (main thread)
    // -------------------------------------------------------------------------

    private void uploadResult(CompileResult result) {
        if (result == null) return;

        // Free existing GPU buffers before replacing them
        buffers.values().forEach(VertexBuffer::close);
        buffers.clear();

        for (Map.Entry<RenderType, MeshData> entry : result.meshes().entrySet()) {
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(entry.getValue());
            VertexBuffer.unbind();
            buffers.put(entry.getKey(), vb);
        }

        this.blockEntities = result.blockEntities().isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(result.blockEntities());

        built = !buffers.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Draw (main thread, called from AssemblyRenderer)
    // -------------------------------------------------------------------------

    /**
     * Draw all opaque and cutout layers.
     *
     * The transform combines:
     *   RenderSystem.getModelViewMatrix()  — camera view
     *   poseStackPose                       — entity position from the renderer
     *   rotation                            — assembly rotation (Quaternionf)
     *
     * Geometry is in local space so this fully positions and rotates the mesh.
     *
     * //@param modelViewFromPoseStack the current model-view matrix as seen by
     *                               the entity renderer (includes entity pos)
     * @param projectionMatrix       the projection matrix
     * @param rotation               the assembly's current rotation quaternion
     *                               (identity if no rotation yet)
     */
    public void draw(PoseStack poseStack,
                     Matrix4f projectionMatrix,
                     Quaternionf rotation) {
        if (!built || buffers.isEmpty()) return;

        // RenderSystem.getModelViewMatrix() is the view matrix (camera transform)
        // poseStack.last().pose() is the entity local transform (entity pos relative to camera)
        // We need both, but separately — view * local * rotation
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())
                .mul(poseStack.last().pose())
                .rotate(rotation);

        for (Map.Entry<RenderType, VertexBuffer> entry : buffers.entrySet()) {
            RenderType renderType = entry.getKey();
            VertexBuffer vb = entry.getValue();

            renderType.setupRenderState();
            vb.bind();
            vb.drawWithShader(modelView, projectionMatrix, RenderSystem.getShader());
            VertexBuffer.unbind();
            renderType.clearRenderState();
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public boolean isBuilt() { return built; }

    public List<BlockEntity> getBlockEntities() { return blockEntities; }

    /**
     * Free all GPU resources and cancel any pending async work.
     * Must be called when the entity is removed.
     */
    public void dispose() {
        if (pendingFuture != null && !pendingFuture.isDone()) {
            pendingFuture.cancel(true);
        }
        buffers.values().forEach(VertexBuffer::close);
        buffers.clear();
        built = false;
        blockEntities = Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Internal types
    // -------------------------------------------------------------------------

    private record CompileResult(
            Map<RenderType, MeshData> meshes,
            List<BlockEntity> blockEntities
    ) {}
}