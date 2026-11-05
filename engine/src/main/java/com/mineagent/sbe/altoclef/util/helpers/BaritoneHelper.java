// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/helpers/BaritoneHelper.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.helpers;

import com.mineagent.sbe.automatone.api.pathing.goals.GoalBlock;
import net.minecraft.world.phys.Vec3;

public class BaritoneHelper {
   public static final Object MINECRAFT_LOCK = new Object();

   public static double calculateGenericHeuristic(Vec3 start, Vec3 target) {
      return calculateGenericHeuristic(start.x, start.y, start.z, target.x, target.y, target.z);
   }

   public static double calculateGenericHeuristic(double xStart, double yStart, double zStart, double xTarget, double yTarget, double zTarget) {
      double xDiff = xTarget - xStart;
      int yDiff = (int)yTarget - (int)yStart;
      double zDiff = zTarget - zStart;
      return GoalBlock.calculate(xDiff, yDiff < 0 ? yDiff - 1 : yDiff, zDiff);
   }
}
