package net.vibey.vel.api.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@OnlyIn(Dist.CLIENT)
public class TestMultiParticle extends TextureSheetMultiParticle {

    private TestMultiParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, Minecraft minecraft) {
        super(level, x, y, z, minecraft);

        this.lifetime = 140;
        this.quadSize = 0.12f;
        this.hasPhysics = false;
        this.gravity = 0f;

        TextureAtlasSprite texture = sprites.get(0, 1);

//        int circleStart = addSubParticlesInCircle(12, 1.5f);
//        for (int i = circleStart; i < circleStart + 12; i++) {
//            float hue = (float) i / 12f;
//            getSubParticle(i).setColor(hue, 1f - hue, 0.5f).setScale(1.2f);
//        }
//
//        int vCircleStart = addSubParticlesInVerticalCircle(12, 1.5f);
//        for (int i = vCircleStart; i < vCircleStart + 12; i++) {
//            float hue = (float)(i - vCircleStart) / 12f;
//            getSubParticle(i).setColor(0.2f, hue, 1f).setScale(1.2f);
//        }

//        int sphereStart = addSubParticlesOnSphere(1000, 5f);
//        for (int i = sphereStart; i < sphereStart + subParticleCount(); i++) {
//            //float t = (float)(i - sphereStart) / 500f;
//            getSubParticle(i).setScale(0.8f).setAlpha(0.7f);
//        }

          int torusStart = addSubParticlesOnTorus(50, 20, 10, 2, 4);
            for (int i = torusStart; i < torusStart + subParticleCount(); i++) {
                //float t = (float)(i - sphereStart) / 500f;
                getSubParticle(i).setUvOverride(
                    texture.getU0(), texture.getU1(), texture.getV0(), texture.getV1()
                );
                getSubParticle(i).setScale(0.8f).setAlpha(0.7f);
            }

//        int lineStart = addSubParticlesOnLine(8, -1.5f, 0f, 0f, 1.5f, 0f, 0f);
//        for (int i = lineStart; i < lineStart + 8; i++) {
//            getSubParticle(i).setColor(1f, 1f, 0f).setScale(0.9f);
//        }
//
//        int boxStart = addSubParticlesInBox(16, 1.0f, 1.0f, 1.0f);
//        for (int i = boxStart; i < boxStart + 16; i++) {
//            getSubParticle(i).setColor(0f, 1f, 0.8f).setScale(0.6f).setAlpha(0.6f);
//        }

        //addSubParticleAtOrigin();
        //getSubParticle(subParticleCount() - 1).setColor(1.1f, 1.1f, 1.1f).setScale(2f);
    }

    //int tickCounter = 0;

    @Override
    public void tick() {
        //tickCounter++;

        this.oRoll = this.roll;
        this.roll += 0.01f;

        float speed = 0.05f;

        for (SubParticle sp : getSubParticles()) {
            float[] d = torusData.get(sp.id);
            if (d == null) continue;

            float cosU      = d[0];
            float sinU      = d[1];
            d[2]           += speed;
            float v         = d[2];
            float major     = d[3];
            float tubeX     = d[4];
            float tubeY     = d[5];

            float x = (major + tubeX * (float) Math.cos(v)) * cosU;
            float y = tubeY * (float) Math.sin(v);
            float z = (major + tubeX * (float) Math.cos(v)) * sinU;

            sp.moveTo(x, y, z);

            sp.setColor(
                    sp.r + ThreadLocalRandom.current().nextFloat(0.01f, 0.1f),
                    sp.g + ThreadLocalRandom.current().nextFloat(0.01f, 0.1f),
                    sp.b + ThreadLocalRandom.current().nextFloat(0.01f, 0.1f)
            );
        }

        //if (tickCounter == 5){tickCounter = 0;}

//        for (SubParticle sp : getSubParticles()) {
//            float len = (float) Math.sqrt(sp.lx * sp.lx + sp.ly * sp.ly + sp.lz * sp.lz);
//            if (len == 0) continue;
//
//            float nx = sp.lx / len;
//            float ny = sp.ly / len;
//            float nz = sp.lz / len;
//
//            sp.translate(nx * 1.1f, ny * 1.1f, nz * 1.1f);
//        }

//        for (SubParticle sp : getSubParticles()) {
//            sp.setColor(
//                    ThreadLocalRandom.current().nextFloat(),
//                    ThreadLocalRandom.current().nextFloat(),
//                    ThreadLocalRandom.current().nextFloat()
//            );
//        }

        float fade = 1f - ((float) this.age++ / this.lifetime);
        setAllAlphas(fade);

        if (this.age >= this.lifetime) {
            this.remove();
        }
    }

    @Override
    public net.minecraft.client.particle.ParticleRenderType getRenderType() {
        return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public net.minecraft.client.particle.Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x, double y, double z,
                double dx, double dy, double dz) {
            return new TestMultiParticle(level, x, y, z, sprites, Minecraft.getInstance());
        }
    }
}
