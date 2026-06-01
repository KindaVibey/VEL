package net.vibey.vel.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.vibey.vel.VEL;
import net.vibey.vel.block.ModBlocks;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VEL.MOD_ID);

    public static final DeferredItem<Item> GUN = ITEMS.register("gun",
            () -> new GunItem(new Item.Properties()));

    public static final DeferredItem<Item> GUN_TURRET = ITEMS.register("gun_turret",
            () -> new BlockItem(ModBlocks.GUN_TURRET.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
