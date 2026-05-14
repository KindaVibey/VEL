package net.vibey.vpl.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.vibey.vpl.VPL;
import net.vibey.vpl.block.ModBlocks;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VPL.MOD_ID);

    public static final DeferredItem<Item> GUN = ITEMS.register("gun",
            () -> new GunItem(new Item.Properties()));

    public static final DeferredItem<Item> GUN_TURRET = ITEMS.register("gun_turret",
            () -> new BlockItem(ModBlocks.GUN_TURRET.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
