package net.vibey.vel.api.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

import java.util.List;

public class SimpleProjectile extends Projectile {

    public static final EntityDataAccessor<Float> DATA_DAMAGE =
            SynchedEntityData.defineId(SimpleProjectile.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DATA_VEL_X =
            SynchedEntityData.defineId(SimpleProjectile.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DATA_VEL_Y =
            SynchedEntityData.defineId(SimpleProjectile.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DATA_VEL_Z =
            SynchedEntityData.defineId(SimpleProjectile.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DATA_SPAWN_X =
            SynchedEntityData.defineId(SimpleProjectile.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DATA_SPAWN_Y =
            SynchedEntityData.defineId(SimpleProjectile.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DATA_SPAWN_Z =
            SynchedEntityData.defineId(SimpleProjectile.class, EntityDataSerializers.FLOAT);

    public static final double AIR_DRAG = 0.99;
    public static final double GRAVITY = 0.015;
    public static final double COLLISION_MARGIN = 0.10;
    public static final int MAX_LIFETIME_TICKS = 1200;

    private int ticksAlive = 0;
    private AABB collisionSearchBox = null;

    public SimpleProjectile(EntityType<? extends SimpleProjectile> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public SimpleProjectile(EntityType<? extends SimpleProjectile> type, Level level,
                            Vec3 position, Vec3 velocity, float damage) {
        this(type, level);
        this.setPos(position.x, position.y, position.z);
        this.setDeltaMovement(velocity);
        this.updateRotation();
        this.entityData.set(DATA_DAMAGE, damage);
        this.entityData.set(DATA_VEL_X, (float) velocity.x);
        this.entityData.set(DATA_VEL_Y, (float) velocity.y);
        this.entityData.set(DATA_VEL_Z, (float) velocity.z);
        this.entityData.set(DATA_SPAWN_X, (float) position.x);
        this.entityData.set(DATA_SPAWN_Y, (float) position.y);
        this.entityData.set(DATA_SPAWN_Z, (float) position.z);
    }

    public float damage() { return 0.0f; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_DAMAGE, damage());
        builder.define(DATA_VEL_X, 0f);
        builder.define(DATA_VEL_Y, 0f);
        builder.define(DATA_VEL_Z, 0f);
        builder.define(DATA_SPAWN_X, 0f);
        builder.define(DATA_SPAWN_Y, 0f);
        builder.define(DATA_SPAWN_Z, 0f);
    }

    public double getAirDrag() { return AIR_DRAG; }
    public double getGravityForce() { return GRAVITY; }
    public double getCollisionMargin() { return COLLISION_MARGIN; }
    public int getMaxLifetimeTicks() { return MAX_LIFETIME_TICKS; }

    public void onBlockHit(Vec3 currentPos, Vec3 nextPos) {
        Vec3 step = nextPos.subtract(currentPos);
        int subdivisions = Math.max((int) Math.ceil(step.length()), 1);

        for (int i = 0; i < subdivisions; i++) {
            Vec3 from = currentPos.add(step.scale((double) i / subdivisions));
            Vec3 to = currentPos.add(step.scale((double) (i + 1) / subdivisions));

            BlockHitResult result = this.level().clip(new ClipContext(
                    from, to,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    this
            ));

            if (result.getType() != HitResult.Type.MISS) {
                this.discard();
                return;
            }
        }
    }

    public void onEntityHit() {
        List<Entity> nearby = this.level().getEntities(
                this, collisionSearchBox,
                e -> e.isAlive() && e.isPickable()
        );

        if (!nearby.isEmpty()) {
            Entity target = nearby.get(0);
            if (collisionSearchBox.intersects(target.getBoundingBox())) {
                float dmg = this.entityData.get(DATA_DAMAGE);
                target.hurt(this.damageSources().mobProjectile(this, null), dmg);
                this.discard();
            }
        }
    }

    @Override
    public void tick() {
        if (this.level().isClientSide && ticksAlive == 0) {
            float vx = this.entityData.get(DATA_VEL_X);
            float vy = this.entityData.get(DATA_VEL_Y);
            float vz = this.entityData.get(DATA_VEL_Z);
            float sx = this.entityData.get(DATA_SPAWN_X);
            float sy = this.entityData.get(DATA_SPAWN_Y);
            float sz = this.entityData.get(DATA_SPAWN_Z);
            if (vx != 0 || vy != 0 || vz != 0) {
                this.setDeltaMovement(vx, vy, vz);
                this.setPos(sx, sy, sz);
            }
        }

        if (++ticksAlive > getMaxLifetimeTicks()) {
            this.discard();
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        Vec3 currentPos = this.position();
        Vec3 nextPos = currentPos.add(motion);

        if (!this.level().isClientSide) {
            onBlockHit(currentPos, nextPos);
            if (this.isRemoved()) return;

            updateCollisionSearchBox(currentPos, nextPos);
            onEntityHit();
        }

        this.setPos(nextPos.x, nextPos.y, nextPos.z);
        this.setDeltaMovement(
                motion.x * getAirDrag(),
                motion.y - getGravityForce(),
                motion.z * getAirDrag()
        );

        updateRotation();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {

    }

    @Override
    public void lerpMotion(double x, double y, double z) {

    }

    @Override
    protected void updateRotation() {
        Vec3 motion = this.getDeltaMovement();
        double hDist = motion.horizontalDistance();
        if (hDist > 0.001) {
            this.setYRot((float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG));
            this.setXRot((float) (Mth.atan2(motion.y, hDist) * Mth.RAD_TO_DEG));
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.updateRotation();
        this.ticksAlive = 0;
        this.collisionSearchBox = null;
    }

    private void updateCollisionSearchBox(Vec3 from, Vec3 to) {
        double minX = Math.min(from.x, to.x) - getCollisionMargin();
        double minY = Math.min(from.y, to.y) - getCollisionMargin();
        double minZ = Math.min(from.z, to.z) - getCollisionMargin();
        double maxX = Math.max(from.x, to.x) + getCollisionMargin();
        double maxY = Math.max(from.y, to.y) + getCollisionMargin();
        double maxZ = Math.max(from.z, to.z) + getCollisionMargin();
        collisionSearchBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override public void checkDespawn() {}

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.ticksAlive = tag.getInt("Age");
        if (tag.contains("Damage")) {
            this.entityData.set(DATA_DAMAGE, tag.getFloat("Damage"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.ticksAlive);
        tag.putFloat("Damage", this.entityData.get(DATA_DAMAGE));
    }

    public float getDamage() { return this.entityData.get(DATA_DAMAGE); }
    public void setDamage(float dmg) { this.entityData.set(DATA_DAMAGE, dmg); }
    public int getTicksAlive() { return ticksAlive; }
}