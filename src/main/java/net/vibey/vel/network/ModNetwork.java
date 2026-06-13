package net.vibey.vel.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.vibey.vel.VEL;
import net.vibey.vel.internal.assemblies.Assembly;
import net.vibey.vel.internal.assemblies.AssemblyBlock;
import net.vibey.vel.internal.assemblies.entity.AssemblyEntity;

import java.util.ArrayList;
import java.util.List;

public class ModNetwork {

    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VEL.MOD_ID);

        registrar.playToClient(
                AssemblySyncPayload.TYPE,
                AssemblySyncPayload.CODEC,
                (payload, context) -> {
                    // This runs on the CLIENT
                    context.enqueueWork(() -> {
                        var level = net.minecraft.client.Minecraft.getInstance().level;
                        if (level == null) return;

                        Entity entity = level.getEntity(payload.entityId());
                        if (!(entity instanceof AssemblyEntity assemblyEntity)) return;

                        // Decode NBT into blocks using client registry
                        List<AssemblyBlock> blocks = new ArrayList<>();
                        ListTag list = payload.assemblyNbt().getList("blocks", Tag.TAG_COMPOUND);
                        for (int i = 0; i < list.size(); i++) {
                            CompoundTag entry = list.getCompound(i);
                            BlockPos pos = NbtUtils.readBlockPos(entry, "pos").orElse(BlockPos.ZERO);
                            BlockState state = NbtUtils.readBlockState(
                                    level.registryAccess().lookupOrThrow(
                                            net.minecraft.core.registries.Registries.BLOCK
                                    ),
                                    entry.getCompound("state")
                            );
                            blocks.add(new AssemblyBlock(pos, state));
                        }

                        assemblyEntity.setAssembly(new Assembly(blocks));
                    });
                }
        );
    }
}