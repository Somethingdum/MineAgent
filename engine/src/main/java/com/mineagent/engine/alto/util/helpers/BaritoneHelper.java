// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/helpers/BaritoneHelper.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.helpers;

import com.mineagent.sbe.automatone.api.pathing.goals.GoalBlock;
import net.minecraft.world.phys.Vec3;

public class BaritoneHelper {

    /**
     * Use whenever accessing Minecraft data (from ClientWorld or ClientPlayerEntity) in baritone
     */
    public static final Object MINECRAFT_LOCK = new Object();

    public static double calculateGenericHeuristic(Vec3 start, Vec3 target) {
        return calculateGenericHeuristic(start.x, start.y, start.z, target.x, target.y, target.z);
    }

    /**
     * Returns baritone's approximation heuristic from start to target
     */
    public static double calculateGenericHeuristic(double xStart, double yStart, double zStart, double xTarget, double yTarget, double zTarget) {
        double xDiff = xTarget - xStart;
        int yDiff = (int) yTarget - (int) yStart;
        double zDiff = zTarget - zStart;
        return GoalBlock.calculate(xDiff, yDiff < 0 ? yDiff - 1 : yDiff, zDiff);
    }
}
