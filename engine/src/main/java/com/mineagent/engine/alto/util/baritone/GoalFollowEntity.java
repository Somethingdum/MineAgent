// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/baritone/GoalFollowEntity.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.baritone;

import com.mineagent.engine.baritone.api.pathing.goals.Goal;
import com.mineagent.engine.baritone.api.pathing.goals.GoalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

public class GoalFollowEntity implements Goal {

    private final Entity entity;
    private final double closeEnoughDistance;

    public GoalFollowEntity(Entity entity, double closeEnoughDistance) {
        this.entity = entity;
        this.closeEnoughDistance = closeEnoughDistance;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        return entity.blockPosition().equals(p) || p.closerToCenterThan(entity.position(), closeEnoughDistance);
    }

    @Override
    public double heuristic(int x, int y, int z) {
        //synchronized (BaritoneHelper.MINECRAFT_LOCK) {
        double xDiff = x - entity.position().x();
        int yDiff = y - entity.blockPosition().getY();
        double zDiff = z - entity.position().z();
        return GoalBlock.calculate(xDiff, yDiff, zDiff);
        //}
    }
}
