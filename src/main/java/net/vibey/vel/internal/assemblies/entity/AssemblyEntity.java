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
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.vibey.vel.internal.assemblies.Assembly;
import net.vibey.vel.internal.assemblies.AssemblyBlock;
import net.vibey.vel.internal.assemblies.render.AssemblyBakedMesh;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class AssemblyEntity extends Entity {

    private Assembly assembly = new Assembly(new ArrayList<>());
    private final Quaternionf rotation = new Quaternionf(); // identity = no rotation
    private Vector3f pivot = new Vector3f(0, 0.5f, 0);

    public AssemblyEntity(EntityType<? extends AssemblyEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.noPhysics = true;
    }

    public Assembly getAssembly() { return assembly; }

    public boolean cornersSet = false;

    public double minX = Double.MAX_VALUE,  minY = Double.MAX_VALUE,  minZ = Double.MAX_VALUE;
    public double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

    public void assemblyCorners(){
        for (AssemblyBlock block : assembly.getBlocks()) {
            BlockPos p = block.relativePos();
            for (int cx = 0; cx <= 1; cx++) {
                for (int cy = 0; cy <= 1; cy++) {
                    for (int cz = 0; cz <= 1; cz++) {
                        double x = p.getX() + cx;
                        double y = p.getY() + cy;
                        double z = p.getZ() + cz;
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    }
                }
            }
        }
    }

    public void setAssembly(Assembly assembly) {
        this.assembly = assembly;
        if (level().isClientSide()) {
            meshDirty = true;
        }
    }

    public Quaternionf getRotation() { return rotation; }

    public void setRotation(Quaternionf q) {
        rotation.set(q).normalize();
    }

    public void setPivot(float x, float y, float z) {
        this.pivot = new Vector3f(x, y, z);
    }

    public Vector3f getPivot() { return pivot; }

    public final Quaternionf prevRotation = new Quaternionf(); // add this

    @Override
    public void tick() {
        prevRotation.set(rotation); // save before mutating
        rotation.rotateXYZ(0.005f, 0.013f, 0.007f).normalize();
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

        CompoundTag rot = new CompoundTag();
        rot.putFloat("x", rotation.x());
        rot.putFloat("y", rotation.y());
        rot.putFloat("z", rotation.z());
        rot.putFloat("w", rotation.w());
        tag.put("rotation", rot);
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

        if (tag.contains("rotation")) {
            CompoundTag rot = tag.getCompound("rotation");
            rotation.set(
                    rot.getFloat("x"),
                    rot.getFloat("y"),
                    rot.getFloat("z"),
                    rot.getFloat("w")
            ).normalize();
        }

        if (level().isClientSide()) {
            meshDirty = true;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (level().isClientSide() && bakedMesh != null) {
            bakedMesh.dispose();
        }
    }

    @OnlyIn(Dist.CLIENT) private AssemblyBakedMesh bakedMesh;
    @OnlyIn(Dist.CLIENT) private boolean meshDirty = true;

    @OnlyIn(Dist.CLIENT)
    public AssemblyBakedMesh getOrBuildMesh() {
        if (bakedMesh == null) {
            bakedMesh = new AssemblyBakedMesh();
        }
        if (meshDirty) {
            meshDirty = false;
            bakedMesh.rebuild(assembly.getBlocks(), position());
        }
        return bakedMesh;
    }
}