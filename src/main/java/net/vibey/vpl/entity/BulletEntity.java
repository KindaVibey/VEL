package net.vibey.vpl.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

import java.util.List;

/**
 * VPL BulletEntity — Vibey Projectile Library
 *
 * The key design goal: physics simulation runs identically on BOTH server and client.
 * This means the client never needs to interpolate between server-sent positions;
 * it always knows exactly where the bullet is based on the initial spawn packet alone.
 *
 * Server: authoritative collision detection + entity damage
 * Client: purely visual — runs the same math to render the bullet correctly every frame
 *
 * The only server→client sync needed is the initial ClientboundAddEntityPacket
 * (pos + velocity), and the updateInterval of 10 ticks acts as a correction
 * fallback for any floating-point drift over long flight times.
 */
public class BulletEntity extends Projectile {

    private static final EntityDataAccessor<Float> DATA_DAMAGE =
            SynchedEntityData.defineId(BulletEntity.class, EntityDataSerializers.FLOAT);

    /** Per-tick velocity multiplier simulating air resistance */
    public static final double AIR_DRAG     = 0.99;

    /** Per-tick downward acceleration in blocks/tick² */
    public static final double GRAVITY      = 0.015;

    /** Half-width of the swept AABB used for entity hit detection */
    public static final double COLLISION_MARGIN = 0.10;

    /** Bullet despawns after this many ticks (~60 seconds) */
    public static final int MAX_LIFETIME_TICKS = 1200;

    private int ticksAlive = 0;

    // Reused each tick to avoid allocation pressure at high fire rates
    private AABB cachedSearchBox = null;

    public BulletEntity(EntityType<? extends BulletEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public BulletEntity(EntityType<? extends BulletEntity> type, Level level,
                        Vec3 position, Vec3 velocity, float damage) {
        this(type, level);
        this.setPos(position.x, position.y, position.z);
        this.setDeltaMovement(velocity);
        this.updateRotation();
        this.entityData.set(DATA_DAMAGE, damage);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // NeoForge / MC 1.21: defineSynchedData takes a Builder instead of calling define() directly
        builder.define(DATA_DAMAGE, 10.0f);
    }

    @Override
    public void tick() {
        this.baseTick();

        if (++ticksAlive > MAX_LIFETIME_TICKS) {
            this.discard();
            return;
        }

        Vec3 motion     = this.getDeltaMovement();
        Vec3 currentPos = this.position();
        Vec3 nextPos    = currentPos.add(motion);

        // Collision detection is server-only — client just simulates position
        if (!this.level().isClientSide) {
            // Block collision: swept ray from current to next position
            BlockHitResult blockHit = this.level().clip(new ClipContext(
                    currentPos, nextPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    this
            ));

            if (blockHit.getType() != HitResult.Type.MISS) {
                this.discard();
                return;
            }

            // Entity collision: swept AABB search, then AABB intersection test
            updateCachedSearchBox(currentPos, nextPos);

            List<Entity> nearby = this.level().getEntities(
                    this, cachedSearchBox,
                    e -> e.isAlive() && e.isPickable()
            );

            if (!nearby.isEmpty()) {
                Entity target = nearby.get(0);
                if (cachedSearchBox.intersects(target.getBoundingBox())) {
                    float dmg = this.entityData.get(DATA_DAMAGE);
                    target.hurt(this.damageSources().mobProjectile(this, null), dmg);
                    this.discard();
                    return;
                }
            }
        }

        // Move — runs on both sides so the client always knows where the bullet is
        this.setPos(nextPos.x, nextPos.y, nextPos.z);

        // Apply drag and gravity — must match exactly on client and server
        this.setDeltaMovement(
                motion.x * AIR_DRAG,
                motion.y - GRAVITY,
                motion.z * AIR_DRAG
        );

        this.updateRotation();
    }

    @Override
    protected void updateRotation() {
        Vec3 motion = this.getDeltaMovement();
        double hDist = motion.horizontalDistance();
        if (hDist > 0.001) {
            this.setYRot((float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG));
            this.setXRot((float) (Mth.atan2(motion.y, hDist)   * Mth.RAD_TO_DEG));
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        }
    }

    // NeoForge 1.21: getAddEntityPacket() is replaced by getAddEntityPacket(ServerEntity)
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        // NeoForge/MC 1.21: velocity is reconstructed by the parent call via
        // setDeltaMovement using the packet's xa/ya/za (stored as short * 8000 on wire).
        // We just need to sync our rotation from the new velocity.
        this.updateRotation();
        this.ticksAlive = 0;
        this.cachedSearchBox = null;
    }

    /**
     * Builds a swept AABB covering the bullet's path this tick, expanded by
     * COLLISION_MARGIN on all sides to catch fast-moving bullets that might
     * otherwise skip past thin entities.
     */
    private void updateCachedSearchBox(Vec3 from, Vec3 to) {
        double minX = Math.min(from.x, to.x) - COLLISION_MARGIN;
        double minY = Math.min(from.y, to.y) - COLLISION_MARGIN;
        double minZ = Math.min(from.z, to.z) - COLLISION_MARGIN;
        double maxX = Math.max(from.x, to.x) + COLLISION_MARGIN;
        double maxY = Math.max(from.y, to.y) + COLLISION_MARGIN;
        double maxZ = Math.max(from.z, to.z) + COLLISION_MARGIN;
        cachedSearchBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // --- Lifecycle overrides ---

    @Override public void checkDespawn() {}

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override public boolean isPickable()        { return false; }
    @Override public boolean isPushable()        { return false; }
    @Override public boolean canBeCollidedWith() { return false; }


    // --- Save / Load ---

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

    // --- Accessors ---

    public float getDamage()          { return this.entityData.get(DATA_DAMAGE); }
    public void  setDamage(float dmg) { this.entityData.set(DATA_DAMAGE, dmg);   }
    public int   getTicksAlive()      { return ticksAlive; }
}
