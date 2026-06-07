package net.vibey.vel.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.vibey.vel.internal.assemblies.Assembly;
import net.vibey.vel.internal.assemblies.entity.AssemblyEntity;
import net.vibey.vel.entity.ModEntityTypes;
import net.vibey.vel.network.AssemblySyncPayload;

public class AssemblyWandItem extends Item {

    public AssemblyWandItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos clicked = context.getClickedPos();

        if (player == null || level.isClientSide()) return InteractionResult.SUCCESS;

        var tag = player.getPersistentData();

        if (!tag.contains("assembly_pos1")) {
            tag.putLong("assembly_pos1", clicked.asLong());
            player.sendSystemMessage(Component.literal("Corner 1 set: " + clicked.toShortString()));
            return InteractionResult.SUCCESS;
        }

        BlockPos pos1 = BlockPos.of(tag.getLong("assembly_pos1"));
        BlockPos pos2 = clicked;
        tag.remove("assembly_pos1");

        BlockPos min = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ())
        );
        BlockPos max = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );

        Assembly assembly = Assembly.capture(level, min, max);

        if (assembly.getBlocks().isEmpty()) {
            player.sendSystemMessage(Component.literal("No blocks found in selection!"));
            return InteractionResult.SUCCESS;
        }

        assembly.removeBlocks(level, min, max);

        AssemblyEntity entity = ModEntityTypes.ASSEMBLY.get().create(level);
        if (entity == null) return InteractionResult.FAIL;

        entity.setAssembly(assembly);
        entity.setPos(min.getX(), min.getY(), min.getZ());
        level.addFreshEntity(entity);

        PacketDistributor.sendToPlayersNear(
                (net.minecraft.server.level.ServerLevel) level,
                null,
                entity.getX(), entity.getY(), entity.getZ(),
                64.0,
                AssemblySyncPayload.of(entity.getId(), assembly.getBlocks())
        );

        player.sendSystemMessage(Component.literal(
                "Assembly created with " + assembly.getBlocks().size() + " blocks!"
        ));

        return InteractionResult.SUCCESS;
    }
}