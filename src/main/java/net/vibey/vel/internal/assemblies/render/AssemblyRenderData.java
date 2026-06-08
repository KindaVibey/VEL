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

@OnlyIn(Dist.CLIENT)
public class AssemblyRenderData {

    private static final List<RenderType> RENDER_TYPES = List.of(
            RenderType.solid(),
            RenderType.cutout(),
            RenderType.cutoutMipped(),
            RenderType.translucent()
    );

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() / 2, 4)),
            r -> {
                Thread t = new Thread(r, "vel-assembly-builder");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 2);
                return t;
            }
    );

    private final Map<RenderType, VertexBuffer> buffers = new LinkedHashMap<>();
    private final AtomicReference<CompileResult> pendingResult = new AtomicReference<>(null);
    private Future<?> pendingFuture = null;
    private boolean built = false;
    private List<BlockEntity> blockEntities = Collections.emptyList();
    private BlockPos lastLightOrigin = null;

    private static final double LIGHT_RESAMPLE_DISTANCE_SQ = 4.0 * 4.0;

    public void requestRebuild(List<AssemblyBlock> blocks, Vec3 entityPos) {
        if (blocks.isEmpty()) {
            dispose();
            return;
        }

        if (pendingFuture != null && !pendingFuture.isDone()) {
            pendingFuture.cancel(true);
        }

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

    public void requestLightRefresh(List<AssemblyBlock> blocks, Vec3 entityPos) {
        requestRebuild(blocks, entityPos);
    }

    public void tick() {
        CompileResult result = pendingResult.getAndSet(null);
        if (result != null) {
            uploadResult(result);
        }
    }

    public boolean needsLightRefresh(Vec3 currentPos) {
        if (lastLightOrigin == null) return false;
        double dx = currentPos.x - lastLightOrigin.getX();
        double dy = currentPos.y - lastLightOrigin.getY();
        double dz = currentPos.z - lastLightOrigin.getZ();
        return (dx * dx + dy * dy + dz * dz) > LIGHT_RESAMPLE_DISTANCE_SQ;
    }

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

                BlockState state = ab.state();
                if (state.isAir()) continue;

                RenderType blockRenderType = ItemBlockRenderTypes.getChunkRenderType(state);
                if (blockRenderType != renderType) continue;

                PoseStack ps = new PoseStack();
                ps.translate(ab.relX(), ab.relY(), ab.relZ());

                dispatcher.renderBatched(
                        state,
                        ab.relativeBlockPos(),
                        region,
                        ps,
                        builder,
                        true,
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

        bes.addAll(region.getBlockEntities().values());

        return new CompileResult(meshes, bes);
    }

    private void uploadResult(CompileResult result) {
        if (result == null) return;

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

    public void draw(PoseStack poseStack,
                     Matrix4f projectionMatrix,
                     Quaternionf rotation) {
        if (!built || buffers.isEmpty()) return;

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

    public boolean isBuilt() { return built; }

    public List<BlockEntity> getBlockEntities() { return blockEntities; }

    public void dispose() {
        if (pendingFuture != null && !pendingFuture.isDone()) {
            pendingFuture.cancel(true);
        }
        buffers.values().forEach(VertexBuffer::close);
        buffers.clear();
        built = false;
        blockEntities = Collections.emptyList();
    }

    private record CompileResult(
            Map<RenderType, MeshData> meshes,
            List<BlockEntity> blockEntities
    ) {}
}