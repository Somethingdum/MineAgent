// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/chains/PreEquipItemChain.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.chains;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasks.entity.AbstractKillEntityTask;
import com.mineagent.sbe.altoclef.tasksystem.TaskChain;
import com.mineagent.sbe.altoclef.tasksystem.TaskRunner;
import com.mineagent.sbe.automatone.api.pathing.calc.IPath;
import com.mineagent.sbe.automatone.api.pathing.movement.IMovement;
import com.mineagent.sbe.automatone.pathing.movement.Movement;
import com.mineagent.sbe.automatone.utils.BlockStateInterface;

import java.util.Optional;

public class PreEquipItemChain extends SingleTaskChain {
   public PreEquipItemChain(TaskRunner runner) {
      super(runner);
   }

   @Override
   protected void onTaskFinish(AltoClefController mod) {
   }

   @Override
   public float getPriority() {
      this.update(this.controller);
      return -1.0F;
   }

   private void update(AltoClefController mod) {
      if (!mod.getFoodChain().isTryingToEat()) {
         TaskChain currentChain = mod.getTaskRunner().getCurrentTaskChain();
         if (currentChain != null) {
            Optional<IPath> pathOptional = mod.getBaritone().getPathingBehavior().getPath();
            if (!pathOptional.isEmpty()) {
               IPath path = pathOptional.get();
               BlockStateInterface bsi = new BlockStateInterface(this.controller.getBaritone().getPlayerContext());

               for (IMovement iMovement : path.movements()) {
                  Movement movement = (Movement)iMovement;
                  if (movement.toBreak(bsi).stream().anyMatch(pos -> mod.getWorld().getBlockState(pos).getBlock().defaultDestroyTime() > 0.0F)
                     || !movement.toPlace(bsi).isEmpty()) {
                     return;
                  }
               }

               if (currentChain.getTasks().stream().anyMatch(task -> task instanceof AbstractKillEntityTask)) {
                  AbstractKillEntityTask.equipWeapon(mod);
               }
            }
         }
      }
   }

   @Override
   public String getName() {
      return "pre-equip item chain";
   }

   @Override
   public boolean isActive() {
      return true;
   }
}
