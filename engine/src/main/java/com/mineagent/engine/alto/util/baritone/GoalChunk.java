// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/baritone/GoalChunk.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.baritone;

import com.mineagent.engine.alto.baritone.api.pathing.goals.Goal;
import com.mineagent.engine.alto.baritone.api.pathing.goals.GoalXZ;
import net.minecraft.world.level.ChunkPos;

public class GoalChunk implements Goal {

    private final ChunkPos pos;

    public GoalChunk(ChunkPos pos) {
        this.pos = pos;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return pos.getMinBlockX() <= x && x <= pos.getMaxBlockX() &&
                pos.getMinBlockZ() <= z && z <= pos.getMaxBlockZ();
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double cx = (pos.getMinBlockX() + pos.getMaxBlockX()) / 2.0, cz = (pos.getMinBlockZ() + pos.getMaxBlockZ()) / 2.0;
        return GoalXZ.calculate(cx - x, cz - z);
    }
}
