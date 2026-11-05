// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/baritone/GoalDirectionXZ.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.baritone;

import com.mineagent.engine.alto.baritone.Baritone;
import com.mineagent.engine.alto.baritone.api.BaritoneAPI;
import com.mineagent.engine.alto.baritone.api.pathing.goals.Goal;
import net.minecraft.world.phys.Vec3;

public class GoalDirectionXZ implements Goal {
    private final double originx;
    private final double originz;
    private final double dirx;
    private final double dirz;

    private final double sidePenalty;

    public GoalDirectionXZ(Vec3 origin, Vec3 offset, double sidePenalty) {
        this.originx = origin.x();
        //this.y = origin.getY();
        this.originz = origin.z();
        offset = offset.multiply(1, 0, 1);
        offset = offset.normalize();
        this.dirx = offset.x;
        this.dirz = offset.z;
        if (this.dirx == 0 && this.dirz == 0) {
            throw new IllegalArgumentException(offset + "");
        }
        this.sidePenalty = sidePenalty;
    }

    private static String maybeCensor(double value) {
        return Baritone.settings().censorCoordinates.value ? "<censored>" : Double.toString(value);
    }

    public boolean isInGoal(int x, int y, int z) {
        return false;
    }

    public double heuristic(int x, int y, int z) {
        double dx = (x - this.originx),
                dz = (z - this.originz);
        double correctDistance = dx * this.dirx + dz * this.dirz;
        double px = dirx * correctDistance,
                pz = dirz * correctDistance;
        double perpendicularDistance = ((dx - px) * (dx - px)) + ((dz - pz) * (dz - pz));

        return -correctDistance * BaritoneAPI.getSettings().costHeuristic.value
                + perpendicularDistance * sidePenalty;
    }

    public String toString() {
        return String.format("GoalDirection{x=%s, z=%s, dx=%s, dz=%s}", maybeCensor(this.originx), maybeCensor(this.originz), maybeCensor(this.dirx), maybeCensor(this.dirz));
    }
}
