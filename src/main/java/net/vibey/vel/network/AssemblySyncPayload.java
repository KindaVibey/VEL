package net.vibey.vel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.vibey.vel.VEL;
import net.vibey.vel.internal.assemblies.AssemblyBlock;

import java.util.List;

public record AssemblySyncPayload(int entityId, CompoundTag assemblyNbt) implements CustomPacketPayload {

    public static final Type<AssemblySyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VEL.MOD_ID, "assembly_sync"));

    public static final StreamCodec<FriendlyByteBuf, AssemblySyncPayload> CODEC =
            StreamCodec.of(AssemblySyncPayload::encode, AssemblySyncPayload::decode);

    public static AssemblySyncPayload of(int entityId, List<AssemblyBlock> blocks) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (AssemblyBlock block : blocks) {
            CompoundTag entry = new CompoundTag();
            // Store exact double offsets — not floored BlockPos — so the
            // half-block centering is preserved across the network.
            entry.putDouble("relX", block.relX());
            entry.putDouble("relY", block.relY());
            entry.putDouble("relZ", block.relZ());
            entry.put("state", NbtUtils.writeBlockState(block.state()));
            list.add(entry);
        }
        tag.put("blocks", list);
        return new AssemblySyncPayload(entityId, tag);
    }

    private static void encode(FriendlyByteBuf buf, AssemblySyncPayload payload) {
        buf.writeInt(payload.entityId());
        buf.writeNbt(payload.assemblyNbt());
    }

    private static AssemblySyncPayload decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        CompoundTag tag = buf.readNbt();
        return new AssemblySyncPayload(entityId, tag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}