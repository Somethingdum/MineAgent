// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/entity/KillEntityTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.entity;

import com.mineagent.sbe.altoclef.AltoClefController;
import net.minecraft.world.entity.Entity;
import java.util.Objects;
import java.util.Optional;

public class KillEntityTask extends AbstractKillEntityTask {
   private final Entity target;

   public KillEntityTask(Entity entity) {
      this.target = entity;
   }

   public KillEntityTask(Entity entity, double maintainDistance, double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
      super(maintainDistance, combatGuardLowerRange, combatGuardLowerFieldRadius);
      this.target = entity;
   }

   @Override
   protected Optional<Entity> getEntityTarget(AltoClefController mod) {
      return Optional.of(this.target);
   }

   @Override
   protected boolean isSubEqual(AbstractDoToEntityTask other) {
      return other instanceof KillEntityTask task ? Objects.equals(task.target, this.target) : false;
   }

   @Override
   protected String toDebugString() {
      return "Killing " + this.target.getType().getDescriptionId();
   }
}
