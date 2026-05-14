package net.vibey.vpl.entity;

import net.vibey.vpl.lib.AbstractProjectileEntity;
import net.vibey.vpl.lib.ProjectileDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Default bullet projectile — a minimal example of extending {@link AbstractProjectileEntity}.
 */
public class BulletEntity extends AbstractProjectileEntity {

    public static final ProjectileDefinition DEFINITION = ProjectileDefinition
            .builder(ResourceLocation.fromNamespaceAndPath("vpl", "textures/entity/bullet.png"))
            .airDrag(0.99)
            .gravity(0.015)
            .collisionMargin(0.10)
            .maxLifetimeTicks(1200)
            .baseDamage(10.0f)
            .build();

    // ── Required 2-arg factory constructor — EntityType calls this ────────────
    public BulletEntity(EntityType<? extends BulletEntity> type, Level level) {
        super(type, level, DEFINITION);
    }

    // ── Convenience constructor for spawning from code ────────────────────────
    public BulletEntity(EntityType<? extends BulletEntity> type, Level level,
                        Vec3 position, Vec3 velocity, float damage) {
        super(type, level, DEFINITION, position, velocity, damage);
    }

    @Override
    protected void onHitEntity(Entity target) {
        super.onHitEntity(target); // deals base damage
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        // no-op for basic bullets
    }
}