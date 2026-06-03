package net.vibey.vel.event;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.vibey.vel.VEL;
import net.vibey.vel.api.particle.ModParticles;

@EventBusSubscriber(modid = VEL.MOD_ID)
public class ParticleTestEvent {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        ItemStack stack = event.getItemStack();

        if (!level.isClientSide) return;
        if (stack.getItem() != Items.STICK) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        level.addParticle(
                ModParticles.TEST_MULTI.get(),
                player.getX(),
                player.getY() + 1.0,
                player.getZ(),
                0, 0, 0
        );

        event.setCanceled(true);
    }
}
