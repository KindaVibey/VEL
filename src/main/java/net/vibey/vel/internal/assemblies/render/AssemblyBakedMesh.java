package net.vibey.vel.internal.assemblies.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.vibey.vel.internal.assemblies.AssemblyBlock;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class AssemblyBakedMesh {

    private static final List<RenderType> RENDER_TYPES = List.of(
            RenderType.solid(),
            RenderType.cutout(),
            RenderType.cutoutMipped(),
            RenderType.translucent()
    );

    private static final ExecutorService MESH_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() / 2, 4)),
            r -> {
                Thread t = new Thread(r, "assembly-mesh-builder");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 2);
                return t;
            }
    );

    private final LinkedHashMap<RenderType, VertexBuffer> buffers = new LinkedHashMap<>();
    private final AtomicReference<Map<RenderType, MeshData>> pendingMeshes = new AtomicReference<>(null);
    private boolean built = false;
    private Future<?> pendingBuild = null;

    public void requestRebuild(List<AssemblyBlock> blocks, Vec3 entityPos) {
        if (blocks.isEmpty()) return;

        BlockPos entityBlockPos = BlockPos.containing(entityPos);
        AssemblyFakeLevel fakeLevel = new AssemblyFakeLevel(blocks, entityBlockPos);

        double fracX = entityPos.x - entityBlockPos.getX();
        double fracY = entityPos.y - entityBlockPos.getY();
        double fracZ = entityPos.z - entityBlockPos.getZ();

        if (pendingBuild != null && !pendingBuild.isDone()) {
            pendingBuild.cancel(true);
        }

        List<AssemblyBlock> snapshot = List.copyOf(blocks);

        pendingBuild = MESH_EXECUTOR.submit(() -> {
            Map<RenderType, MeshData> meshes = compileMeshes(
                    snapshot, fakeLevel, fracX, fracY, fracZ
            );
            pendingMeshes.set(meshes);
        });
    }

    private Map<RenderType, MeshData> compileMeshes(
            List<AssemblyBlock> blocks,
            AssemblyFakeLevel fakeLevel,
            double fracX, double fracY, double fracZ) {

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create();
        Map<RenderType, MeshData> result = new LinkedHashMap<>();

        for (RenderType renderType : RENDER_TYPES) {
            if (Thread.currentThread().isInterrupted()) break;

            ByteBufferBuilder byteBuffer = new ByteBufferBuilder(renderType.bufferSize());
            BufferBuilder builder = new BufferBuilder(
                    byteBuffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK
            );

            boolean hasAny = false;

            for (AssemblyBlock block : blocks) {
                if (Thread.currentThread().isInterrupted()) break;

                BlockPos pos = block.relativePos();
                var state = block.state();
                if (state.isAir()) continue;

                BakedModel model = dispatcher.getBlockModel(state);
                if (!model.getRenderTypes(state, random, ModelData.EMPTY).contains(renderType)) continue;

                PoseStack ps = new PoseStack();
                ps.translate(pos.getX() - fracX, pos.getY() - fracY, pos.getZ() - fracZ);

                dispatcher.renderBatched(
                        state, pos, fakeLevel, ps,
                        builder, true, random,
                        ModelData.EMPTY, renderType
                );
                hasAny = true;
            }

            if (hasAny) {
                MeshData mesh = builder.build();
                if (mesh != null) {
                    result.put(renderType, mesh);
                } else {
                    byteBuffer.close();
                }
            } else {
                byteBuffer.close();
            }
        }

        return result;
    }

    public void tick() {
        Map<RenderType, MeshData> pending = pendingMeshes.getAndSet(null);
        if (pending != null) {
            uploadMeshes(pending);
        }
    }

    private void uploadMeshes(Map<RenderType, MeshData> meshes) {
        // Free old GPU memory
        buffers.values().forEach(VertexBuffer::close);
        buffers.clear();

        for (var entry : meshes.entrySet()) {
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(entry.getValue());
            VertexBuffer.unbind();
            buffers.put(entry.getKey(), vb);
        }

        built = !buffers.isEmpty();
    }

    public void draw(PoseStack poseStack, Matrix4f projectionMatrix) {
        if (!built || buffers.isEmpty()) return;

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())
                .mul(poseStack.last().pose());

        for (var entry : buffers.entrySet()) {
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

    public void dispose() {
        if (pendingBuild != null && !pendingBuild.isDone()) {
            pendingBuild.cancel(true);
        }
        buffers.values().forEach(VertexBuffer::close);
        buffers.clear();
        built = false;
    }
}