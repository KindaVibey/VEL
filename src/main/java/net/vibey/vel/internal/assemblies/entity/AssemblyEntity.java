package net.vibey.vel.internal.assemblies.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.vibey.vel.internal.assemblies.Assembly;
import net.vibey.vel.internal.assemblies.AssemblyBlock;
import net.vibey.vel.internal.assemblies.render.AssemblyBakedMesh;

import java.util.ArrayList;
import java.util.List;

public class AssemblyEntity extends Entity {
    private Assembly assembly = new Assembly(new ArrayList<>());

    public AssemblyEntity(EntityType<? extends AssemblyEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.noPhysics = true;
    }

    public Assembly getAssembly() {
        return this.assembly;
    }

    public void setAssembly(Assembly assembly) {
        this.assembly = assembly;
        if (level().isClientSide()) {
            cachedSamplePoints = null;
            lastLightHash = 0;
            if (bakedMesh != null) bakedMesh.dispose();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        ListTag list = new ListTag();
        for (AssemblyBlock block : assembly.getBlocks()) {
            CompoundTag entry = new CompoundTag();
            entry.put("pos", NbtUtils.writeBlockPos(block.relativePos()));
            entry.put("state", NbtUtils.writeBlockState(block.state()));
            list.add(entry);
        }
        tag.put("blocks", list);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        List<AssemblyBlock> blocks = new ArrayList<>();
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPos(entry, "pos").orElse(BlockPos.ZERO);
            var state = NbtUtils.readBlockState(
                    level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
                    entry.getCompound("state")
            );
            blocks.add(new AssemblyBlock(pos, state));
        }
        this.assembly = new Assembly(blocks);
    }

    @OnlyIn(Dist.CLIENT)
    private AssemblyBakedMesh bakedMesh;

    @OnlyIn(Dist.CLIENT)
    private int lastLightHash = 0;

    @OnlyIn(Dist.CLIENT)
    private List<BlockPos> cachedSamplePoints = null;

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) return;
        if (assembly == null || assembly.getBlocks().isEmpty()) return;

        if (bakedMesh != null) bakedMesh.tick();

        if (cachedSamplePoints == null) {
            cachedSamplePoints = computeSamplePoints();
        }

        var lightEngine = level().getLightEngine();
        BlockPos origin = blockPosition();

        int hash = 0;
        for (BlockPos p : cachedSamplePoints) {
            BlockPos world = origin.offset(p);
            int blockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getLightValue(world);
            int skyLight   = lightEngine.getLayerListener(LightLayer.SKY).getLightValue(world);
            hash = hash * 31 + blockLight;
            hash = hash * 31 + skyLight;
        }
        hash = hash * 31 + (int)(level().getDayTime() / 1200);

        if (hash != lastLightHash) {
            lastLightHash = hash;
            if (bakedMesh != null) {
                bakedMesh.buildAsync(assembly.getBlocks(), position());
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private List<BlockPos> computeSamplePoints() {
        if (assembly.getBlocks().isEmpty()) return List.of(BlockPos.ZERO);

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (AssemblyBlock b : assembly.getBlocks()) {
            BlockPos p = b.relativePos();
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }

        int midX = (minX + maxX) / 2;
        int midY = (minY + maxY) / 2;
        int midZ = (minZ + maxZ) / 2;

        return List.of(
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, minY, minZ),
                new BlockPos(minX, maxY, minZ),
                new BlockPos(minX, minY, maxZ),
                new BlockPos(maxX, maxY, maxZ),
                new BlockPos(minX, maxY, maxZ),
                new BlockPos(maxX, minY, maxZ),
                new BlockPos(maxX, maxY, minZ),
                new BlockPos(midX, midY, midZ)
        );
    }

    @OnlyIn(Dist.CLIENT)
    public AssemblyBakedMesh getOrBuildMesh() {
        if (bakedMesh == null) bakedMesh = new AssemblyBakedMesh();
        if (!bakedMesh.isBuilt()) {
            bakedMesh.buildSync(assembly.getBlocks(), position());
        }
        return bakedMesh;
    }
}