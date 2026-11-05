// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/entity/ShearSheepTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.entity;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.Debug;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Items;
import java.util.Optional;

public class ShearSheepTask extends AbstractDoToEntityTask {
   public ShearSheepTask() {
      super(0.0, -1.0, -1.0);
   }

   @Override
   protected boolean isSubEqual(AbstractDoToEntityTask other) {
      return other instanceof ShearSheepTask;
   }

   @Override
   protected Task onEntityInteract(AltoClefController mod, Entity entity) {
      if (!mod.getItemStorage().hasItem(Items.SHEARS)) {
         Debug.logWarning("Failed to shear sheep because you have no shears.");
         return null;
      } else {
         if (mod.getSlotHandler().forceEquipItem(Items.SHEARS)) {
            ((Sheep)entity).shear( SoundSource.PLAYERS );
            mod.getPlayer().getMainHandItem().hurtAndBreak(1, mod.getPlayer(),  EquipmentSlot.MAINHAND  );
         }

         return null;
      }
   }

   @Override
   protected Optional<Entity> getEntityTarget(AltoClefController mod) {
      return mod.getEntityTracker()
         .getClosestEntity(
                 EntityVer.getPos(mod.getPlayer()), entity -> !(entity instanceof Sheep sheep) ? false : sheep.readyForShearing() && !sheep.isSheared(), Sheep.class
         );
   }

   @Override
   protected String toDebugString() {
      return "Shearing Sheep";
   }
}
