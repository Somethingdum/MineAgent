// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/entity/KillEntitiesTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.entity;

import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;

public class KillEntitiesTask extends DoToClosestEntityTask {
   public KillEntitiesTask(Predicate<Entity> shouldKill) {
      super(KillEntityTask::new, e -> e.isAlive() && shouldKill.test(e), (Class[])null);
   }

   public KillEntitiesTask(Predicate<Entity> shouldKill, Class<?>... entities) {
      super(KillEntityTask::new, e -> e.isAlive() && shouldKill.test(e), entities);

      assert entities != null;
   }

   public KillEntitiesTask(Class<?>... entities) {
      super(KillEntityTask::new, entities);

      assert entities != null;
   }
}
