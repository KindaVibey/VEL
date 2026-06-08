// AssemblyEntity.java
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.vibey.vel.internal.assemblies.Assembly;
import net.vibey.vel.internal.assemblies.AssemblyBlock;
import net.vibey.vel.internal.assemblies.render.AssemblyRenderData;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

public class AssemblyEntity extends Entity {

    private Assembly assembly = new Assembly(new ArrayList<>());

    // Rotation — currently identity. Replace with physics/rotation logic
    // when implemented. The renderer reads this each frame.
    private final Quaternionf assemblyRotation = new Quaternionf();

    @OnlyIn(Dist.CLIENT)
    private AssemblyRenderData renderData;

    @OnlyIn(Dist.CLIENT)
    private BlockPos lastRebuildPos;

    public AssemblyEntity(EntityType<? extends AssemblyEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.noPhysics = true;
    }

    // -----------------------------------------------------------------------
    // Assembly data
    // -----------------------------------------------------------------------

    public Assembly getAssembly() {
        return assembly;
    }

    public void setAssembly(Assembly assembly) {
        this.assembly = assembly;
        if (level().isClientSide()) {
            scheduleFullRebuild();
        }
    }

    // -----------------------------------------------------------------------
    // Rotation accessor (renderer uses this)
    // -----------------------------------------------------------------------

    public Quaternionf getAssemblyRotation() {
        return assemblyRotation;
    }

    public void setAssemblyRotation(Quaternionf rotation) {
        this.assemblyRotation.set(rotation);
    }

    // -----------------------------------------------------------------------
    // Tick — client side only, drives the render data lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide()) return;
        if (assembly == null || assembly.getBlocks().isEmpty()) return;

        if (renderData != null) {
            renderData.tick();
        }

        if (renderData != null && renderData.isBuilt()) {
            if (renderData.needsLightRefresh(position())) {
                renderData.requestLightRefresh(assembly.getBlocks(), position());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Render data lifecycle (client only)
    // -----------------------------------------------------------------------

    @OnlyIn(Dist.CLIENT)
    private void scheduleFullRebuild() {
        if (renderData == null) {
            renderData = new AssemblyRenderData();
        }
        if (assembly != null && !assembly.getBlocks().isEmpty()) {
            renderData.requestRebuild(assembly.getBlocks(), position());
            lastRebuildPos = blockPosition();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public AssemblyRenderData getRenderData() {
        if (renderData == null && assembly != null && !assembly.getBlocks().isEmpty()) {
            scheduleFullRebuild();
        }
        return renderData;
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (level().isClientSide() && renderData != null) {
            renderData.dispose();
            renderData = null;
        }
    }

    // -----------------------------------------------------------------------
    // Entity boilerplate
    // -----------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        ListTag list = new ListTag();
        for (AssemblyBlock block : assembly.getBlocks()) {
            CompoundTag entry = new CompoundTag();
            // Store double relative coords since AssemblyBlock no longer uses BlockPos
            entry.putDouble("relX", block.relX());
            entry.putDouble("relY", block.relY());
            entry.putDouble("relZ", block.relZ());
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
            double relX = entry.getDouble("relX");
            double relY = entry.getDouble("relY");
            double relZ = entry.getDouble("relZ");
            var state = NbtUtils.readBlockState(
                    level().registryAccess().lookupOrThrow(
                            net.minecraft.core.registries.Registries.BLOCK),
                    entry.getCompound("state")
            );
            blocks.add(new AssemblyBlock(relX, relY, relZ, state));
        }
        this.assembly = new Assembly(blocks);
        if (level().isClientSide()) {
            scheduleFullRebuild();
        }
    }
}