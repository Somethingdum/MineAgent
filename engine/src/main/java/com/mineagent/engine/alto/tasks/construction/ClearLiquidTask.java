// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/construction/ClearLiquidTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.construction;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasks.InteractWithBlockTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.ItemTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;

/**
 * Removes a liquid source block at a position.
 */
public class ClearLiquidTask extends Task {

    private final BlockPos _liquidPos;

    public ClearLiquidTask(BlockPos liquidPos) {
        this._liquidPos = liquidPos;
    }

    @Override
    protected void onStart() {

    }

    @Override
    protected Task onTick() {
        if (AltoClef.getInstance().getItemStorage().hasItem(Items.BUCKET)) {
            AltoClef.getInstance().getBehaviour().setRayTracingFluidHandling(ClipContext.Fluid.SOURCE_ONLY);
            return new InteractWithBlockTask(new ItemTarget(Items.BUCKET, 1), _liquidPos, false);
        }

        return new PlaceStructureBlockTask(_liquidPos);
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    @Override
    public boolean isFinished() {
        if (AltoClef.getInstance().getChunkTracker().isChunkLoaded(_liquidPos)) {
            return AltoClef.getInstance().getWorld().getBlockState(_liquidPos).getFluidState().isEmpty();
        }
        return false;
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof ClearLiquidTask task) {
            return task._liquidPos.equals(_liquidPos);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Clear liquid at " + _liquidPos;
    }
}
