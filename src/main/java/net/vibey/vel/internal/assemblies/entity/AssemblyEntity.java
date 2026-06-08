// AssemblyEntity.java  (modified — clean up tick, add rotation field,
//                       expose getRenderData())
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

    // -----------------------------------------------------------------------
    // Rotation — currently identity. Replace with your physics/rotation
    // logic when you add it. The renderer reads this each frame so you
    // just need to update this field.
    // -----------------------------------------------------------------------
    private final Quaternionf assemblyRotation = new Quaternionf(); // identity

    // -----------------------------------------------------------------------
    // Client-side render data. @OnlyIn so it doesn't exist on the server.
    // -----------------------------------------------------------------------
    @OnlyIn(Dist.CLIENT)
    private AssemblyRenderData renderData;

    // Track last rebuild position so we know when to do a light refresh.
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

    /**
     * Set the assembly rotation. Call this from your physics/input logic
     * when rotation is implemented.
     */
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

        // Let the render data upload any pending compiled meshes
        if (renderData != null) {
            renderData.tick();
        }

        // Check if we need a light refresh due to movement.
        // This is cheap (just a distance check) and only triggers a rebuild
        // when the assembly has moved far enough that the baked light is stale.
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

    /**
     * Called by the renderer each frame. Returns null if not yet initialised.
     */
    @OnlyIn(Dist.CLIENT)
    public AssemblyRenderData getRenderData() {
        // Lazily initialise on first access (handles the case where setAssembly
        // was called before the client was ready)
        if (renderData == null && assembly != null && !assembly.getBlocks().isEmpty()) {
            scheduleFullRebuild();
        }
        return renderData;
    }

    /**
     * Clean up GPU resources when the entity is removed.
     */
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
                    level().registryAccess().lookupOrThrow(
                            net.minecraft.core.registries.Registries.BLOCK),
                    entry.getCompound("state")
            );
            blocks.add(new AssemblyBlock(pos, state));
        }
        this.assembly = new Assembly(blocks);
        if (level().isClientSide()) {
            scheduleFullRebuild();
        }
    }
}