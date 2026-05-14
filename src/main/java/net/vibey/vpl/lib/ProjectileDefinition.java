package net.vibey.vpl.lib;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable descriptor for a projectile type.
 * Build one via {@link Builder} and pass it into your {@link AbstractProjectileEntity} subclass.
 *
 * <p>All physics constants run identically on server and client so client-side
 * prediction stays in sync with the server between network updates.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public static final ProjectileDefinition DEF = ProjectileDefinition
 *     .builder(ResourceLocation.fromNamespaceAndPath("mymod", "textures/entity/rocket.png"))
 *     .gravity(0.0)
 *     .airDrag(1.0)
 *     .baseDamage(20.0f)
 *     .build();
 * }</pre>
 */
public final class ProjectileDefinition {

    /** Per-tick speed multiplier. 1.0 = no drag. */
    public final double airDrag;

    /** Downward acceleration per tick (blocks/tick²). 0 = flat trajectory. */
    public final double gravity;

    /** Expansion added to the swept AABB for entity collision broad-phase. */
    public final double collisionMargin;

    /** Ticks before the projectile is automatically discarded. */
    public final int maxLifetimeTicks;

    /** Base damage dealt on entity hit. Can be changed per-instance via {@code setDamage()}. */
    public final float baseDamage;

    /** Texture resource location used by the projectile renderer. */
    public final ResourceLocation texture;

    private ProjectileDefinition(Builder b) {
        this.airDrag          = b.airDrag;
        this.gravity          = b.gravity;
        this.collisionMargin  = b.collisionMargin;
        this.maxLifetimeTicks = b.maxLifetimeTicks;
        this.baseDamage       = b.baseDamage;
        this.texture          = b.texture;
    }

    public static Builder builder(ResourceLocation texture) {
        return new Builder(texture);
    }

    public static final class Builder {
        private double airDrag         = 0.99;
        private double gravity         = 0.015;
        private double collisionMargin = 0.10;
        private int    maxLifetimeTicks = 1200;
        private float  baseDamage      = 5.0f;
        private final ResourceLocation texture;

        private Builder(ResourceLocation texture) {
            this.texture = texture;
        }

        /** Per-tick speed multiplier [0–1]. Default 0.99. */
        public Builder airDrag(double drag)        { airDrag = drag;           return this; }

        /** Downward accel per tick. Default 0.015. 0 = no gravity. */
        public Builder gravity(double g)           { gravity = g;              return this; }

        /** Sweep-box expansion for entity hit detection. Default 0.10. */
        public Builder collisionMargin(double m)   { collisionMargin = m;      return this; }

        /** Auto-discard after N ticks. Default 1200 (60 s at 20 TPS). */
        public Builder maxLifetimeTicks(int ticks) { maxLifetimeTicks = ticks; return this; }

        /** Base damage on entity hit. Default 5. */
        public Builder baseDamage(float dmg)       { baseDamage = dmg;         return this; }

        public ProjectileDefinition build()        { return new ProjectileDefinition(this); }
    }
}