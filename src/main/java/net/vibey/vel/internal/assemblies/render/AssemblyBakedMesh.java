package net.vibey.vel.internal.assemblies.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.vibey.vel.internal.assemblies.AssemblyBlock;

import java.util.*;

public class AssemblyBakedMesh {

    private static final List<RenderType> RENDER_TYPES = List.of(
            RenderType.solid(),
            RenderType.cutout(),
            RenderType.cutoutMipped(),
            RenderType.translucent()
    );

    private final Map<RenderType, VertexBuffer> buffers = new HashMap<>();
    private boolean built = false;

    public void build(List<AssemblyBlock> blocks, BlockPos entityPos) {
        if (built) dispose();

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create();
        // No packedLight — fake level samples real light per block
        AssemblyFakeLevel fakeLevel = new AssemblyFakeLevel(blocks, entityPos);

        for (RenderType renderType : RENDER_TYPES) {
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

            boolean hasAny = false;

            for (AssemblyBlock block : blocks) {
                BlockPos pos = block.relativePos();
                var state = block.state();
                if (state.isAir()) continue;

                BakedModel model = dispatcher.getBlockModel(state);
                if (!model.getRenderTypes(state, random, ModelData.EMPTY).contains(renderType)) continue;

                PoseStack ps = new PoseStack();
                ps.translate(pos.getX(), pos.getY(), pos.getZ());

                dispatcher.renderBatched(
                        state,
                        pos,
                        fakeLevel,
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
                MeshData mesh = builder.buildOrThrow();
                VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vb.bind();
                vb.upload(mesh);
                VertexBuffer.unbind();
                buffers.put(renderType, vb);
            }
        }

        built = true;
    }

    public void draw(PoseStack poseStack, org.joml.Matrix4f projectionMatrix) {
        if (!built) return;

        for (var entry : buffers.entrySet()) {
            RenderType renderType = entry.getKey();
            VertexBuffer vb = entry.getValue();

            renderType.setupRenderState();
            vb.bind();
            vb.drawWithShader(
                    // Combine the RenderSystem modelView WITH the entity's poseStack
                    new org.joml.Matrix4f(RenderSystem.getModelViewMatrix())
                            .mul(poseStack.last().pose()),
                    projectionMatrix,
                    RenderSystem.getShader()
            );
            VertexBuffer.unbind();
            renderType.clearRenderState();
        }
    }

    public boolean isBuilt() { return built; }

    public void dispose() {
        buffers.values().forEach(VertexBuffer::close);
        buffers.clear();
        built = false;
    }
}