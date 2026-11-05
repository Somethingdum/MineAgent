// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/movement/RunAwayFromCreepersTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.movement;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.chains.MobDefenseChain;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.baritone.GoalRunAwayFromEntities;
import com.mineagent.engine.baritone.api.pathing.goals.Goal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

public class RunAwayFromCreepersTask extends CustomBaritoneGoalTask {

    private final double _distanceToRun;

    public RunAwayFromCreepersTask(double distance) {
        _distanceToRun = distance;
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof RunAwayFromCreepersTask task) {
            //if (task._mob.getPos().squaredDistanceTo(_mob.getPos()) > 0.5) return false;
            if (Math.abs(task._distanceToRun - _distanceToRun) > 1) return false;
            return true;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Run " + _distanceToRun + " blocks away from creepers";
    }

    @Override
    protected Goal newGoal(AltoClef mod) {
        // We want to run away NOW
        mod.getClientBaritone().getPathingBehavior().forceCancel();
        return new GoalRunAwayFromCreepers(mod, _distanceToRun);
    }

    private static class GoalRunAwayFromCreepers extends GoalRunAwayFromEntities {

        public GoalRunAwayFromCreepers(AltoClef mod, double distance) {
            super(mod, distance, false, 10);
        }

        @Override
        protected List<Entity> getEntities(AltoClef mod) {
            return new ArrayList<>(mod.getEntityTracker().getTrackedEntities(Creeper.class));
        }

        @Override
        protected double getCostOfEntity(Entity entity, int x, int y, int z) {
            if (entity instanceof Creeper) {
                return MobDefenseChain.getCreeperSafety(new Vec3(x + 0.5, y + 0.5, z + 0.5), (Creeper) entity);
            }
            return super.getCostOfEntity(entity, x, y, z);
        }
    }
}
