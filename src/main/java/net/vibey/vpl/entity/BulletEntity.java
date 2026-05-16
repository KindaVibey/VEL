package net.vibey.vpl.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.vibey.vpl.api.projectile.SimpleProjectile;

public class BulletEntity extends SimpleProjectile {

    public BulletEntity(EntityType<? extends BulletEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public BulletEntity(EntityType<? extends BulletEntity> type, Level level,
                        Vec3 position, Vec3 velocity, float damage) {
        super(type, level, position, velocity, damage);
    }

    @Override
    public float damage(){return 2.0f;}
}