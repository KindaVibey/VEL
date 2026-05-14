package net.vibey.vpl.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.vibey.vpl.entity.BulletEntity;
import net.vibey.vpl.entity.ModEntityTypes;

public class GunItem extends Item {

    public GunItem(Properties properties) {
        super(properties.stacksTo(1).durability(500));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        Vec3 lookVec  = player.getLookAngle();
        Vec3 velocity = lookVec.scale(2.5);
        Vec3 eyePos   = player.getEyePosition(1.0f);
        Vec3 spawnPos = eyePos.add(lookVec);

        if (!level.isClientSide) {
            BulletEntity bullet = new BulletEntity(
                    ModEntityTypes.BULLET.get(),
                    level,
                    spawnPos,
                    velocity,
                    10.0f
            );

            level.addFreshEntity(bullet);

            // NeoForge 1.21: SoundEvents.GENERIC_EXPLODE is a Holder<SoundEvent>
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.5f);

            player.getCooldowns().addCooldown(this, 10);
            EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            itemStack.hurtAndBreak(1, player, slot);
        }

        return InteractionResultHolder.success(itemStack);
    }
}