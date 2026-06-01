package net.vibey.vel.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.vibey.vel.VEL;

public class ModEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, VEL.MOD_ID);

    public static final net.neoforged.neoforge.registries.DeferredHolder<EntityType<?>, EntityType<BulletEntity>> BULLET =
            ENTITY_TYPES.register("bullet", () -> EntityType.Builder.<BulletEntity>of(
                            BulletEntity::new,
                            MobCategory.MISC
                    )
                    .sized(0.1f, 0.1f)
                    .updateInterval(10)
                    .setShouldReceiveVelocityUpdates(true)
                    .fireImmune()
                    .build("bullet"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
