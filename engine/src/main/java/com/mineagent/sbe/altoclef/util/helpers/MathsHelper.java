// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/helpers/MathsHelper.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.helpers;

import net.minecraft.world.phys.Vec3;

public interface MathsHelper {
   static Vec3 project(Vec3 vec, Vec3 onto, boolean assumeOntoNormalized) {
      if (!assumeOntoNormalized) {
         onto = onto.normalize();
      }

      return onto.scale(vec.dot(onto));
   }

   static Vec3 project(Vec3 vec, Vec3 onto) {
      return project(vec, onto, false);
   }

   static Vec3 projectOntoPlane(Vec3 vec, Vec3 normal, boolean assumeNormalNormalized) {
      Vec3 p = project(vec, normal, assumeNormalNormalized);
      return vec.subtract(p);
   }

   static Vec3 projectOntoPlane(Vec3 vec, Vec3 normal) {
      return projectOntoPlane(vec, normal, false);
   }
}
