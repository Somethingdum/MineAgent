// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/speedrun/DragonBreathTracker.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.speedrun;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.BotBehaviour;
import com.mineagent.engine.alto.tasks.movement.CustomBaritoneGoalTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.helpers.WorldHelper;
import com.mineagent.engine.alto.util.progresscheck.MovementProgressChecker;
import com.mineagent.engine.alto.baritone.api.pathing.goals.Goal;
import com.mineagent.engine.alto.baritone.api.pathing.goals.GoalRunAway;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.AreaEffectCloud;

public class DragonBreathTracker {
    private final HashSet<BlockPos> breathBlocks = new HashSet<>();

    public void updateBreath(AltoClef mod) {
        breathBlocks.clear();
        for (AreaEffectCloud cloud : mod.getEntityTracker().getTrackedEntities(AreaEffectCloud.class)) {
            for (BlockPos bad : WorldHelper.getBlocksTouchingBox(cloud.getBoundingBox())) {
                breathBlocks.add(bad);
            }
        }
    }

    public boolean isTouchingDragonBreath(BlockPos pos) {
        return breathBlocks.contains(pos);
    }

    public Task getRunAwayTask() {
        return new RunAwayFromDragonsBreathTask();
    }

    private class RunAwayFromDragonsBreathTask extends CustomBaritoneGoalTask {

        @Override
        protected void onStart() {
            super.onStart();
            BotBehaviour botBehaviour = AltoClef.getInstance().getBehaviour();

            botBehaviour.push();
            botBehaviour.setBlockPlacePenalty(Double.POSITIVE_INFINITY);
            // do NOT ever wander
            checker = new MovementProgressChecker((int) Float.POSITIVE_INFINITY);
        }

        @Override
        protected void onStop(Task interruptTask) {
            super.onStop(interruptTask);
            AltoClef.getInstance().getBehaviour().pop();
        }

        @Override
        protected Goal newGoal(AltoClef mod) {
            return new GoalRunAway(10, breathBlocks.toArray(BlockPos[]::new));
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof RunAwayFromDragonsBreathTask;
        }

        @Override
        protected String toDebugString() {
            return "ESCAPE Dragons Breath";
        }
    }
}
