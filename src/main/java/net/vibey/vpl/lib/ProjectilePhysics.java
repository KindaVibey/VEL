package net.vibey.vpl.lib;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pure, side-agnostic projectile physics math.
 *
 * <p>Every method is deterministic and stateless — no {@code Level}, no side checks.
 * Call these identically on server (authoritative) and client (prediction) to keep
 * both in sync between network update packets.</p>
 */
public final class ProjectilePhysics {

    private ProjectilePhysics() {}

    // ── Velocity integration ──────────────────────────────────────────────────

    /**
     * Returns the velocity for the <em>next</em> tick after applying drag and gravity.
     */
    public static Vec3 integrateVelocity(Vec3 velocity, ProjectileDefinition def) {
        return new Vec3(
                velocity.x * def.airDrag,
                velocity.y * def.airDrag - def.gravity,
                velocity.z * def.airDrag
        );
    }

    // ── Swept AABB ────────────────────────────────────────────────────────────

    /**
     * Builds the minimal AABB enclosing the line segment {@code from → to},
     * expanded by {@code margin} on all sides.
     *
     * <p>Used as the broad-phase entity query box — one {@code Level.getEntities()}
     * call covers the entire path swept in a single tick.</p>
     */
    public static AABB sweepBox(Vec3 from, Vec3 to, double margin) {
        return new AABB(
                Math.min(from.x, to.x) - margin,
                Math.min(from.y, to.y) - margin,
                Math.min(from.z, to.z) - margin,
                Math.max(from.x, to.x) + margin,
                Math.max(from.y, to.y) + margin,
                Math.max(from.z, to.z) + margin
        );
    }

    // ── Rotation helpers ──────────────────────────────────────────────────────

    /**
     * Pitch in degrees derived from a velocity vector.
     * Positive = nose-down (falling), negative = nose-up (rising).
     */
    public static float pitchFromVelocity(Vec3 velocity) {
        double hDist = velocity.horizontalDistance();
        if (hDist < 1e-4) return velocity.y > 0 ? -90f : 90f;
        return (float) (Mth.atan2(-velocity.y, hDist) * Mth.RAD_TO_DEG);
    }

    /**
     * Yaw in degrees derived from a velocity vector.
     * Matches Minecraft's convention (south = 0°, west = 90°, etc.).
     */
    public static float yawFromVelocity(Vec3 velocity) {
        return (float) (Mth.atan2(velocity.x, velocity.z) * Mth.RAD_TO_DEG);
    }

    // ── Client interpolation ──────────────────────────────────────────────────

    /**
     * Linearly interpolates between {@code prev} and {@code current} for sub-tick render smoothing.
     *
     * @param partialTicks sub-tick fraction in [0, 1]
     */
    public static Vec3 lerp(Vec3 prev, Vec3 current, float partialTicks) {
        return new Vec3(
                Mth.lerp(partialTicks, prev.x, current.x),
                Mth.lerp(partialTicks, prev.y, current.y),
                Mth.lerp(partialTicks, prev.z, current.z)
        );
    }

    /**
     * Predicts the position {@code ticksAhead} ticks into the future.
     * Useful for lead-targeting or client-side extrapolation past the update interval.
     */
    public static Vec3 predictPosition(Vec3 pos, Vec3 velocity, ProjectileDefinition def, int ticksAhead) {
        Vec3 v = velocity;
        Vec3 p = pos;
        for (int i = 0; i < ticksAhead; i++) {
            p = p.add(v);
            v = integrateVelocity(v, def);
        }
        return p;
    }
}