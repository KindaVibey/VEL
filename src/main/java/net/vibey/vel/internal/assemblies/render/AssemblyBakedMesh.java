package net.vibey.vel.internal.assemblies.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
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

    private static final ExecutorService MESH_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "assembly-mesh-builder");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private Map<RenderType, VertexBuffer> buffers = new HashMap<>();
    private final AtomicReference<Map<RenderType, MeshData>> pendingMeshes = new AtomicReference<>(null);
    private boolean built = false;
    private Future<?> pendingBuild = null;

    // First build — sync so there's no gap on first render
    public void buildSync(List<AssemblyBlock> blocks, BlockPos entityPos) {
        if (built) dispose();
        Map<RenderType, MeshData> meshes = compileMeshes(blocks, entityPos);
        uploadMeshes(meshes);
    }

    // Subsequent builds — async so light updates never hitch
    public void buildAsync(List<AssemblyBlock> blocks, BlockPos entityPos) {
        if (pendingBuild != null && !pendingBuild.isDone()) {
            pendingBuild.cancel(true);
        }
        // Snapshot the list so the background thread has its own copy
        List<AssemblyBlock> snapshot = List.copyOf(blocks);
        pendingBuild = MESH_EXECUTOR.submit(() -> {
            Map<RenderType, MeshData> meshes = compileMeshes(snapshot, entityPos);
            pendingMeshes.set(meshes);
        });
    }

    // Pure data compilation — no GL calls, safe off render thread
    private Map<RenderType, MeshData> compileMeshes(List<AssemblyBlock> blocks, BlockPos entityPos) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create();
        AssemblyFakeLevel fakeLevel = new AssemblyFakeLevel(blocks, entityPos);
        Map<RenderType, MeshData> result = new LinkedHashMap<>();

        for (RenderType renderType : RENDER_TYPES) {
            if (Thread.currentThread().isInterrupted()) break;

            // ByteBufferBuilder with initial capacity — expands as needed
            ByteBufferBuilder byteBuffer = new ByteBufferBuilder(renderType.bufferSize());
            BufferBuilder builder = new BufferBuilder(byteBuffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

            boolean hasAny = false;

            for (AssemblyBlock block : blocks) {
                if (Thread.currentThread().isInterrupted()) break;

                BlockPos pos = block.relativePos();
                var state = block.state();
                if (state.isAir()) continue;

                BakedModel model = dispatcher.getBlockModel(state);
                if (!model.getRenderTypes(state, random, ModelData.EMPTY).contains(renderType)) continue;

                PoseStack ps = new PoseStack();
                ps.translate(pos.getX(), pos.getY(), pos.getZ());

                dispatcher.renderBatched(
                        state, pos, fakeLevel, ps,
                        builder, true, random,
                        ModelData.EMPTY, renderType
                );
                hasAny = true;
            }

            if (hasAny) {
                MeshData mesh = builder.build();
                if (mesh != null) result.put(renderType, mesh);
            } else {
                byteBuffer.close();
            }
        }

        return result;
    }

    // Upload to GPU — must be called on render thread
    private void uploadMeshes(Map<RenderType, MeshData> meshes) {
        buffers.values().forEach(VertexBuffer::close);
        buffers.clear();

        for (var entry : meshes.entrySet()) {
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(entry.getValue());
            VertexBuffer.unbind();
            buffers.put(entry.getKey(), vb);
        }

        built = true;
    }

    // Called every frame from renderer — uploads any pending async mesh
    public void tick() {
        Map<RenderType, MeshData> pending = pendingMeshes.getAndSet(null);
        if (pending != null) {
            uploadMeshes(pending);
        }
    }

    public void draw(PoseStack poseStack, Matrix4f projectionMatrix) {
        if (!built) return;

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
        if (pendingBuild != null) pendingBuild.cancel(true);
        buffers.values().forEach(VertexBuffer::close);
        buffers.clear();
        built = false;
    }
}