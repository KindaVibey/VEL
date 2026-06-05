package net.vibey.vel.api.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public abstract class SingleQuadMultiParticle extends Particle {
    private final Minecraft minecraft;

    public static class SubParticle {

        public final int id;
        public float lx, ly, lz;
        public float lxo, lyo, lzo;
        public float r = 1f, g = 1f, b = 1f, alpha = 1f;
        public float scale = 1f;
        public float roll = 0f, oRoll = 0f;
        private boolean visible = true;
        private float u0 = Float.NaN, u1, v0, v1;

        public SubParticle(int id, float lx, float ly, float lz) {
            this.id = id;
            this.lx  = lx;  this.lxo = lx;
            this.ly  = ly;  this.lyo = ly;
            this.lz  = lz;  this.lzo = lz;
        }

        public SubParticle setColor(float r, float g, float b) {
            this.r = r; this.g = g; this.b = b;
            return this;
        }

        public SubParticle setAlpha(float alpha) {
            this.alpha = alpha;
            return this;
        }

        public SubParticle setScale(float scale) {
            this.scale = scale;
            return this;
        }

        public SubParticle setRoll(float roll) {
            this.oRoll = this.roll;
            this.roll  = roll;
            return this;
        }

        public SubParticle setVisible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public SubParticle setUvOverride(float u0, float u1, float v0, float v1) {
            this.u0 = u0; this.u1 = u1;
            this.v0 = v0; this.v1 = v1;
            return this;
        }

        public SubParticle clearUvOverride() {
            this.u0 = Float.NaN;
            return this;
        }

        public boolean hasUvOverride() { return !Float.isNaN(u0); }

        public SubParticle moveTo(float lx, float ly, float lz) {
            this.lxo = this.lx; this.lyo = this.ly; this.lzo = this.lz;
            this.lx  = lx;     this.ly  = ly;     this.lz  = lz;
            return this;
        }

        public boolean isVisible() { return visible; }

        public SubParticle translate(float dx, float dy, float dz) {
            return moveTo(lx + dx, ly + dy, lz + dz);
        }
    }

    private final Map<Integer, SubParticle> subParticleMap = new LinkedHashMap<>();
    private int nextId = 0;

    protected SingleQuadMultiParticle(ClientLevel level, double x, double y, double z, Minecraft minecraft) {
        super(level, x, y, z);
        this.minecraft = minecraft;
    }

    protected SingleQuadMultiParticle(
            ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, Minecraft minecraft) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.minecraft = minecraft;
    }

    protected ParticleStatus getParticleStatus() {
        return this.minecraft.options.particles().get();
    }

    public boolean isDecreased() {
        return getParticleStatus() == ParticleStatus.DECREASED;
    }
    public boolean isMinimal() {
        return getParticleStatus() == ParticleStatus.MINIMAL;
    }

    public double getAmount() {
        if (isDecreased()) {
            return 0.75;
        } else if (isMinimal()) {
            return 0.5;
        } else {
            return 1.0;
        }
    }

    public SubParticle addSubParticle(SubParticle sp) {
        if (subParticleMap.containsKey(sp.id)) {
            throw new IllegalArgumentException("Sub-particle ID " + sp.id + " is already registered.");
        }
        subParticleMap.put(sp.id, sp);
        return sp;
    }

    public SubParticle addSubParticle(float lx, float ly, float lz) {
        SubParticle sp = new SubParticle(nextId++, lx, ly, lz);
        subParticleMap.put(sp.id, sp);
        return sp;
    }

    public SubParticle removeSubParticle(int id) {
        return subParticleMap.remove(id);
    }

    public SubParticle getSubParticle(int id) {
        return subParticleMap.get(id);
    }

    public Collection<SubParticle> getSubParticles() {
        return Collections.unmodifiableCollection(subParticleMap.values());
    }

    public int subParticleCount() { return subParticleMap.size(); }

    public int addSubParticlesInCircle(int count, float radius) {
        int firstId = nextId;
        float step = (float)(2.0 * Math.PI / count);
        for (int i = 0; i < count; i++) {
            float angle = step * i;
            SubParticle sp = new SubParticle(nextId++, (float)(Math.cos(angle) * radius), 0f, (float)(Math.sin(angle) * radius));
            subParticleMap.put(sp.id, sp);
        }
        return firstId;
    }

    public int addSubParticlesInVerticalCircle(int count, float radius) {
        int firstId = nextId;
        float step = (float)(2.0 * Math.PI / count);
        for (int i = 0; i < count; i++) {
            float angle = step * i;
            SubParticle sp = new SubParticle(nextId++, (float)(Math.cos(angle) * radius), (float)(Math.sin(angle) * radius), 0f);
            subParticleMap.put(sp.id, sp);
        }
        return firstId;
    }

    public int addSubParticlesOnSphere(int count, float radius) {
        count *= getAmount();

        int firstId = nextId;
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < count; i++) {
            double y     = 1.0 - (i / (double)(count - 1)) * 2.0;
            double r     = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = goldenAngle * i;
            SubParticle sp = new SubParticle(nextId++, (float)(Math.cos(theta) * r * radius), (float)(y * radius), (float)(Math.sin(theta) * r * radius));
            subParticleMap.put(sp.id, sp);
        }
        return firstId;
    }

    protected final Map<Integer, float[]> torusData = new HashMap<>();

    public int addSubParticlesOnTorus(int ringCount, int tubeCount,
                                      float majorRadius, float tubeRadiusX, float tubeRadiusY) {
        ringCount *= getAmount();
        tubeCount *= getAmount();

        int firstId = nextId;
        double ringStep = 2.0 * Math.PI / ringCount;
        double tubeStep = 2.0 * Math.PI / tubeCount;
        for (int i = 0; i < ringCount; i++) {
            double u = ringStep * i;
            float cosU = (float) Math.cos(u);
            float sinU = (float) Math.sin(u);
            for (int j = 0; j < tubeCount; j++) {
                double v = tubeStep * j;
                float x = (majorRadius + tubeRadiusX * (float) Math.cos(v)) * cosU;
                float y = tubeRadiusY * (float) Math.sin(v);
                float z = (majorRadius + tubeRadiusX * (float) Math.cos(v)) * sinU;
                SubParticle sp = new SubParticle(nextId++, x, y, z);
                subParticleMap.put(sp.id, sp);
                // store: cosU, sinU, current v angle, majorRadius, tubeRadiusX, tubeRadiusY
                torusData.put(sp.id, new float[]{cosU, sinU, (float) v, majorRadius, tubeRadiusX, tubeRadiusY});
            }
        }
        return firstId;
    }

    public int addSubParticlesOnLine(int count,
                                     float startX, float startY, float startZ,
                                     float endX,   float endY,   float endZ) {
        int firstId = nextId;
        for (int i = 0; i < count; i++) {
            float t = (count > 1) ? (float) i / (count - 1) : 0f;
            SubParticle sp = new SubParticle(nextId++, Mth.lerp(t, startX, endX), Mth.lerp(t, startY, endY), Mth.lerp(t, startZ, endZ));
            subParticleMap.put(sp.id, sp);
        }
        return firstId;
    }

    public int addSubParticlesInBox(int count, float halfW, float halfH, float halfD) {
        int firstId = nextId;
        for (int i = 0; i < count; i++) {
            SubParticle sp = new SubParticle(nextId++,
                    (random.nextFloat() * 2f - 1f) * halfW,
                    (random.nextFloat() * 2f - 1f) * halfH,
                    (random.nextFloat() * 2f - 1f) * halfD);
            subParticleMap.put(sp.id, sp);
        }
        return firstId;
    }

    public int addSubParticleAtOrigin() {
        int id = nextId;
        SubParticle sp = new SubParticle(nextId++, 0f, 0f, 0f);
        subParticleMap.put(sp.id, sp);
        return id;
    }

    public void setAllColors(float r, float g, float b) {
        for (SubParticle sp : subParticleMap.values()) sp.setColor(r, g, b);
    }

    public void setAllAlphas(float alpha) {
        for (SubParticle sp : subParticleMap.values()) sp.setAlpha(alpha);
    }

    public void setAllScales(float scale) {
        for (SubParticle sp : subParticleMap.values()) sp.setScale(scale);
    }

    public void setAllVisible(boolean visible) {
        for (SubParticle sp : subParticleMap.values()) sp.setVisible(visible);
    }

    public SingleQuadParticle.FacingCameraMode getFacingCameraMode() {
        return SingleQuadParticle.FacingCameraMode.LOOKAT_XYZ;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Quaternionf quaternion = new Quaternionf();
        getFacingCameraMode().setRotation(quaternion, camera, partialTicks);
        Vec3 camPos = camera.getPosition();
        float px = (float)(Mth.lerp(partialTicks, this.xo, this.x) - camPos.x());
        float py = (float)(Mth.lerp(partialTicks, this.yo, this.y) - camPos.y());
        float pz = (float)(Mth.lerp(partialTicks, this.zo, this.z) - camPos.z());
        int lightColor = getLightColor(partialTicks);
        for (SubParticle sp : subParticleMap.values()) {
            if (!sp.isVisible()) continue;
            Quaternionf q = new Quaternionf(quaternion);
            if (this.roll != 0f) {
                q.rotateZ(Mth.lerp(partialTicks, this.oRoll, this.roll));
            }
            if (sp.roll != 0f) {
                q.rotateZ(Mth.lerp(partialTicks, sp.oRoll, sp.roll));
            }
            float lx = Mth.lerp(partialTicks, sp.lxo, sp.lx);
            float ly = Mth.lerp(partialTicks, sp.lyo, sp.ly);
            float lz = Mth.lerp(partialTicks, sp.lzo, sp.lz);
            float wx = px + lx;
            float wy = py + ly;
            float wz = pz + lz;
            float size = getQuadSize(partialTicks) * sp.scale;
            float u0 = sp.hasUvOverride() ? sp.u0 : getU0();
            float u1 = sp.hasUvOverride() ? sp.u1 : getU1();
            float v0 = sp.hasUvOverride() ? sp.v0 : getV0();
            float v1 = sp.hasUvOverride() ? sp.v1 : getV1();
            renderSubQuad(buffer, q, wx, wy, wz, size, u0, u1, v0, v1,
                    sp.r, sp.g, sp.b, sp.alpha, lightColor);
        }
    }

    private void renderSubQuad(
            VertexConsumer buffer, Quaternionf quaternion,
            float x, float y, float z,
            float size,
            float u0, float u1, float v0, float v1,
            float r, float g, float b, float alpha,
            int packedLight) {
        renderVertex(buffer, quaternion, x, y, z,  1f, -1f, size, u1, v1, r, g, b, alpha, packedLight);
        renderVertex(buffer, quaternion, x, y, z,  1f,  1f, size, u1, v0, r, g, b, alpha, packedLight);
        renderVertex(buffer, quaternion, x, y, z, -1f,  1f, size, u0, v0, r, g, b, alpha, packedLight);
        renderVertex(buffer, quaternion, x, y, z, -1f, -1f, size, u0, v1, r, g, b, alpha, packedLight);
    }

    private static void renderVertex(
            VertexConsumer buffer, Quaternionf quaternion,
            float x, float y, float z,
            float xOff, float yOff,
            float size,
            float u, float v,
            float r, float g, float b, float alpha,
            int packedLight) {
        Vector3f pos = new Vector3f(xOff, yOff, 0f)
                .rotate(quaternion)
                .mul(size)
                .add(x, y, z);
        buffer.addVertex(pos.x(), pos.y(), pos.z())
                .setUv(u, v)
                .setColor(r, g, b, alpha)
                .setLight(packedLight);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(float partialTicks) {
        float maxReach = getQuadSize(partialTicks);
        for (SubParticle sp : subParticleMap.values()) {
            float reach = (float) Math.sqrt(sp.lx * sp.lx + sp.ly * sp.ly + sp.lz * sp.lz)
                    + getQuadSize(partialTicks) * sp.scale;
            if (reach > maxReach) maxReach = reach;
        }
        return new net.minecraft.world.phys.AABB(
                x - maxReach, y - maxReach, z - maxReach,
                x + maxReach, y + maxReach, z + maxReach);
    }

    protected float quadSize = 0.1F * (this.random.nextFloat() * 0.5F + 0.5F) * 2.0F;

    public float getQuadSize(float partialTicks) { return this.quadSize; }

    @Override
    public Particle scale(float scale) {
        this.quadSize *= scale;
        return super.scale(scale);
    }

    protected abstract float getU0();

    protected abstract float getU1();

    protected abstract float getV0();

    protected abstract float getV1();
}