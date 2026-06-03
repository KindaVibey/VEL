package net.vibey.vel.api.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TestMultiParticle extends TextureSheetMultiParticle {

    private TestMultiParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);

        this.lifetime = 160;
        this.quadSize = 0.12f;
        this.hasPhysics = false;
        this.gravity = 0f;
        pickSprite(sprites);

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

        int sphereStart = addSubParticlesOnSphere(5000, 1f);
        for (int i = sphereStart; i < sphereStart + 5000; i++) {
            float t = (float)(i - sphereStart) / 5000f;
            getSubParticle(i).setColor(1f, t, 1f - t).setScale(0.8f).setAlpha(0.7f);
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

    @Override
    public void tick() {
        //this.oRoll = this.roll;
        //this.roll += 0.01f;

        for (SubParticle sp : getSubParticles()) {
            float len = (float) Math.sqrt(sp.lx * sp.lx + sp.ly * sp.ly + sp.lz * sp.lz);
            if (len == 0) continue;

            float nx = sp.lx / len;
            float ny = sp.ly / len;
            float nz = sp.lz / len;

            sp.translate(nx * 0.5f, ny * 0.5f, nz * 0.5f);
        }

        float fade = 1f - ((float) this.age / this.lifetime);
        setAllAlphas(fade);

        if (this.age++ >= this.lifetime) {
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
            return new TestMultiParticle(level, x, y, z, sprites);
        }
    }
}
