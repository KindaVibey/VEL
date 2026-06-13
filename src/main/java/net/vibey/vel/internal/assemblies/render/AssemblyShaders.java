package net.vibey.vel.internal.assemblies.render;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.vibey.vel.VEL;

import java.io.IOException;

import static com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK;

@OnlyIn(Dist.CLIENT)
public class AssemblyShaders {

    // Held on the render thread only — set via the RegisterShadersEvent callback.
    private static ShaderInstance assemblyShader;

    public static ShaderInstance getAssemblyShader() {
        return assemblyShader;
    }

    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(VEL.MOD_ID, "assembly"),
                        BLOCK
                ),
                shader -> assemblyShader = shader
        );
    }
}