// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasksystem/ITaskRequiresGrounded.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasksystem;

import net.minecraft.world.entity.LivingEntity;

public interface ITaskRequiresGrounded extends ITaskCanForce {
   @Override
   default boolean shouldForce(Task interruptingCandidate) {
      if (interruptingCandidate instanceof ITaskOverridesGrounded) {
         return false;
      } else {
         LivingEntity player = ((Task)this).controller.getPlayer();
         return !player.onGround() && !player.isSwimming() && !player.isInWater() && !player.onClimbable();
      }
   }
}
