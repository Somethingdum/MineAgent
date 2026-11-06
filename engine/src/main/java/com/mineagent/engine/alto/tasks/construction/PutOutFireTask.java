// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/construction/PutOutFireTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.construction;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasks.InteractWithBlockTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.ItemTarget;
import com.mineagent.sbe.automatone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Given a block position with fire in it, extinguish the fire at that position
 */
public class PutOutFireTask extends Task {

    private final BlockPos _firePosition;

    public PutOutFireTask(BlockPos firePosition) {
        _firePosition = firePosition;
    }

    @Override
    protected void onStart() {

    }

    @Override
    protected Task onTick() {
        return new InteractWithBlockTask(ItemTarget.EMPTY, null, _firePosition, Input.CLICK_LEFT, false, false);
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    @Override
    public boolean isFinished() {
        BlockState s = AltoClef.getInstance().getWorld().getBlockState(_firePosition);
        return (s.getBlock() != Blocks.FIRE && s.getBlock() != Blocks.SOUL_FIRE);
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof PutOutFireTask task) {
            return (task._firePosition.equals(_firePosition));
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Putting out fire at " + _firePosition;
    }
}
