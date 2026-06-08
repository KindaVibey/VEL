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

    public void buildSync(List<AssemblyBlock> blocks, Vec3 entityPos) {
        if (built) dispose();
        Map<RenderType, MeshData> meshes = compileMeshes(blocks, entityPos);
        uploadMeshes(meshes);
    }

    public void buildAsync(List<AssemblyBlock> blocks, Vec3 entityPos) {
        if (pendingBuild != null && !pendingBuild.isDone()) {
            pendingBuild.cancel(true);
        }
        List<AssemblyBlock> snapshot = List.copyOf(blocks);
        pendingBuild = MESH_EXECUTOR.submit(() -> {
            Map<RenderType, MeshData> meshes = compileMeshes(snapshot, entityPos);
            pendingMeshes.set(meshes);
        });
    }

    private Map<RenderType, MeshData> compileMeshes(List<AssemblyBlock> blocks, Vec3 entityPos) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create();

        // Floor to get the origin block pos (matches Assembly.capture's origin calculation)
        BlockPos entityBlockPos = BlockPos.containing(entityPos);
        AssemblyFakeLevel fakeLevel = new AssemblyFakeLevel(blocks, entityBlockPos);

        // The fractional part of the entity pos is already carried by the poseStack
        // in draw(). We must subtract it from each block's translation here so blocks
        // end up at their exact world positions and don't shift by the fractional amount.
        double fracX = entityPos.x - entityBlockPos.getX();
        double fracY = entityPos.y - entityBlockPos.getY();
        double fracZ = entityPos.z - entityBlockPos.getZ();

        Map<RenderType, MeshData> result = new LinkedHashMap<>();

        for (RenderType renderType : RENDER_TYPES) {
            if (Thread.currentThread().isInterrupted()) break;

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
                if (mesh != null) result.put(renderType, mesh);
            } else {
                byteBuffer.close();
            }
        }

        return result;
    }

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

    public void tick() {
        Map<RenderType, MeshData> pending = pendingMeshes.getAndSet(null);
        if (pending != null) {
            uploadMeshes(pending);
        }
    }

    public void draw(PoseStack poseStack, Matrix4f projectionMatrix) {
        if (!built) return;

        // poseStack already encodes (entityPos - cameraPos) including the fractional part.
        // compileMeshes subtracts that fractional part from each block, so together
        // every block lands exactly at its correct world position.
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