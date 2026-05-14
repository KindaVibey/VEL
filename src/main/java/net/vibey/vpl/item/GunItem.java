package net.vibey.vpl.item;

import net.vibey.vpl.entity.BulletEntity;
import net.vibey.vpl.entity.ModEntityTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GunItem extends Item {

    public GunItem(Properties properties) {
        super(properties.stacksTo(1).durability(500));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            Vec3 look     = player.getLookAngle();
            Vec3 spawnPos = player.getEyePosition().add(look);
            Vec3 velocity = look.scale(2.5);

            BulletEntity bullet = new BulletEntity(
                    ModEntityTypes.BULLET.get(), level, spawnPos, velocity, 10f);
            bullet.setOwner(player);
            level.addFreshEntity(bullet);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.5f);

            player.getCooldowns().addCooldown(this, 10);
            stack.hurtAndBreak(1, player, hand);
        }

        return InteractionResultHolder.success(stack);
    }
}