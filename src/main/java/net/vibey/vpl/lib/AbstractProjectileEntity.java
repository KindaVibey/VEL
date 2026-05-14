package net.vibey.vpl.lib;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Base class for all VPL-managed projectiles.
 *
 * <h2>Physics model</h2>
 * <p>Physics run on <strong>both</strong> server and client every tick using the same
 * {@link ProjectilePhysics} methods. The client predicts position independently between
 * server network updates, so bullets fly smoothly at any framerate without rubber-banding.
 * The server remains authoritative for damage and block-hit discard.</p>
 *
 * <h2>How to extend</h2>
 * <ol>
 *   <li>Create a {@link ProjectileDefinition} with your physics/visual config.</li>
 *   <li>Subclass this, passing the definition to {@code super(...)}.</li>
 *   <li>Override {@link #onHitEntity} and/or {@link #onHitBlock} for custom behavior.</li>
 *   <li>Override {@link #readAdditionalData} / {@link #additionalSaveData} for extra NBT.</li>
 * </ol>
 *
 * <pre>{@code
 * public class RocketEntity extends AbstractProjectileEntity {
 *
 *     public static final ProjectileDefinition DEF = ProjectileDefinition
 *         .builder(ResourceLocation.fromNamespaceAndPath("mymod", "textures/entity/rocket.png"))
 *         .gravity(0.0).airDrag(1.0).baseDamage(20f).build();
 *
 *     // Required factory constructor - EntityType calls this
 *     public RocketEntity(EntityType<?> type, Level level) {
 *         super(type, level, DEF);
 *     }
 *
 *     // Convenience constructor for spawning from code
 *     public RocketEntity(EntityType<?> type, Level level, Vec3 pos, Vec3 vel, float dmg) {
 *         super(type, level, DEF, pos, vel, dmg);
 *     }
 *
 *     @Override
 *     protected void onHitEntity(Entity target) {
 *         // explode, then deal base damage
 *         super.onHitEntity(target);
 *     }
 * }
 * }</pre>
 */
public abstract class AbstractProjectileEntity extends Projectile {

    // Each subclass must define its OWN DATA_DAMAGE accessor using its own class token.
    // If two classes share one, IDs will desync on the network and crash.
    // Subclasses that need to add extra synced data should define additional accessors
    // using their own class token and call builder.define() inside defineSynchedData.
    private static final EntityDataAccessor<Float> DATA_DAMAGE =
            SynchedEntityData.defineId(AbstractProjectileEntity.class, EntityDataSerializers.FLOAT);

    protected final ProjectileDefinition definition;

    private int ticksAlive = 0;

    // Previous-tick position — stored each tick for the renderer to lerp between.
    // NOT synced; both sides run the same physics so they independently track this.
    private Vec3 prevPos = null;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Required factory constructor. EntityType calls this when deserializing.
     * Subclasses must expose this exact two-arg signature.
     */
    protected AbstractProjectileEntity(EntityType<? extends AbstractProjectileEntity> type,
                                       Level level,
                                       ProjectileDefinition definition) {
        super(type, level);
        this.definition = definition;
        this.noCulling  = true;
    }

    /**
     * Convenience constructor for spawning from code (items, blocks, etc.).
     *
     * @param type       the registered EntityType
     * @param level      the world
     * @param definition physics + visual config
     * @param position   initial world-space position
     * @param velocity   initial velocity in blocks/tick
     * @param damage     damage dealt on entity hit (overrides definition.baseDamage)
     */
    protected AbstractProjectileEntity(EntityType<? extends AbstractProjectileEntity> type,
                                       Level level,
                                       ProjectileDefinition definition,
                                       Vec3 position,
                                       Vec3 velocity,
                                       float damage) {
        this(type, level, definition);
        this.setPos(position.x, position.y, position.z);
        this.setDeltaMovement(velocity);
        this.applyRotationFromVelocity(velocity);
        this.entityData.set(DATA_DAMAGE, damage);
        this.prevPos = position;
    }

    // ── Synced data ───────────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // NOTE: Do NOT call super here — Projectile.defineSynchedData does not exist.
        builder.define(DATA_DAMAGE, definition.baseDamage);
    }

    // ── Tick — runs on BOTH sides ─────────────────────────────────────────────

    @Override
    public final void tick() {
        super.tick(); // Projectile.tick() handles owner tracking

        prevPos = this.position();

        if (++ticksAlive > definition.maxLifetimeTicks) {
            this.discard();
            return;
        }

        Vec3 motion     = this.getDeltaMovement();
        Vec3 currentPos = this.position();
        Vec3 nextPos    = currentPos.add(motion);

        // ── Server only: authoritative collision ──────────────────────────────
        if (!this.level().isClientSide) {

            // Block collision
            BlockHitResult blockHit = this.level().clip(new ClipContext(
                    currentPos, nextPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    this
            ));

            if (blockHit.getType() != HitResult.Type.MISS) {
                onHitBlock(blockHit);
                this.discard();
                return;
            }

            // Entity collision — swept AABB broad phase
            AABB sweep = ProjectilePhysics.sweepBox(currentPos, nextPos, definition.collisionMargin);
            List<Entity> nearby = this.level().getEntities(
                    this, sweep,
                    e -> e.isAlive() && e.isPickable()
            );

            for (Entity candidate : nearby) {
                if (sweep.intersects(candidate.getBoundingBox())) {
                    onHitEntity(candidate);
                    this.discard();
                    return;
                }
            }
        }

        // ── Both sides: advance position + integrate physics ──────────────────
        this.setPos(nextPos.x, nextPos.y, nextPos.z);
        Vec3 nextMotion = ProjectilePhysics.integrateVelocity(motion, definition);
        this.setDeltaMovement(nextMotion);
        this.applyRotationFromVelocity(nextMotion);
    }

    // ── Collision hooks ───────────────────────────────────────────────────────

    /**
     * Called server-side when this projectile hits a living entity.
     * Default: deals {@link #getDamage()} as mob-projectile damage.
     * Override to add effects; call {@code super} to keep the damage.
     */
    protected void onHitEntity(Entity target) {
        target.hurt(this.damageSources().mobProjectile(this, this.getOwner()), getDamage());
    }

    /**
     * Called server-side when this projectile hits a block face.
     * Default: no-op. The projectile is discarded by the caller after this returns.
     */
    protected void onHitBlock(BlockHitResult hit) {}

    // ── Required Projectile abstract methods ──────────────────────────────────

    // Projectile.onHit is called by Projectile.tick() in vanilla flow.
    // We manage our own collision loop above, so this is intentionally empty.
    @Override
    protected void onHit(HitResult result) {}

    // These are called by vanilla's Projectile.tick() if we let it run clip logic,
    // which we don't — our tick() bypasses that. Implementations are here for safety.
    @Override
    protected void onHitEntity(EntityHitResult result) {}

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // delegate to our simpler hook
        onHitBlock(result);
    }

    // ── Rotation ──────────────────────────────────────────────────────────────

    private void applyRotationFromVelocity(Vec3 v) {
        if (v.horizontalDistance() > 1e-4 || Math.abs(v.y) > 1e-4) {
            this.setYRot(ProjectilePhysics.yawFromVelocity(v));
            this.setXRot(ProjectilePhysics.pitchFromVelocity(v));
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        }
    }

    // ── Client rendering helpers ──────────────────────────────────────────────

    /**
     * Returns the smoothly interpolated render position for the current frame.
     * Call from your renderer's {@code render()} method.
     *
     * @param partialTicks sub-tick fraction in [0, 1]
     */
    public Vec3 getRenderPosition(float partialTicks) {
        if (prevPos == null) return this.position();
        return ProjectilePhysics.lerp(prevPos, this.position(), partialTicks);
    }

    /**
     * Predicts where this entity will be {@code ticksAhead} ticks from now.
     * Client-side utility only — not authoritative.
     */
    public Vec3 predictFuturePosition(int ticksAhead) {
        return ProjectilePhysics.predictPosition(
                this.position(), this.getDeltaMovement(), definition, ticksAhead);
    }

    // ── Spawn packet — 1.21 signature ─────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    // recreateFromPacket is inherited from Projectile and reads xa/ya/za correctly.

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected final void readAdditionalSaveData(CompoundTag tag) {
        ticksAlive = tag.getInt("Age");
        if (tag.contains("Damage")) {
            entityData.set(DATA_DAMAGE, tag.getFloat("Damage"));
        }
        readAdditionalData(tag);
    }

    @Override
    protected final void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", ticksAlive);
        tag.putFloat("Damage", entityData.get(DATA_DAMAGE));
        additionalSaveData(tag);
    }

    /** Override to read subclass NBT fields. */
    protected void readAdditionalData(CompoundTag tag) {}

    /** Override to write subclass NBT fields. */
    protected void additionalSaveData(CompoundTag tag) {}

    // ── Accessors ─────────────────────────────────────────────────────────────

    public float getDamage()                  { return entityData.get(DATA_DAMAGE); }
    public void  setDamage(float damage)      { entityData.set(DATA_DAMAGE, damage); }
    public int   getTicksAlive()              { return ticksAlive; }
    public ProjectileDefinition getDefinition() { return definition; }

    // ── Misc overrides ────────────────────────────────────────────────────────

    @Override public void    checkDespawn()                         {}
    @Override public boolean shouldRenderAtSqrDistance(double d)   { return true; }
    @Override public boolean shouldBeSaved()                       { return false; }
    @Override public boolean isPickable()                          { return false; }
    @Override public boolean isPushable()                          { return false; }

    @Override
    protected float getEyeHeight(Pose pose, EntityDimensions dimensions) { return 0f; }
}