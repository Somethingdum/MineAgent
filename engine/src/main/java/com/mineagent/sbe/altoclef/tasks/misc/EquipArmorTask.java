// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/misc/EquipArmorTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.misc;

import com.mineagent.sbe.altoclef.tasks.squashed.CataloguedResourceTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.helpers.StorageHelper;
import net.minecraft.world.item.Item;
import java.util.Arrays;

public class EquipArmorTask extends Task {
   private final ItemTarget[] toEquip;

   public EquipArmorTask(ItemTarget... toEquip) {
      this.toEquip = toEquip;
   }

   public EquipArmorTask(Item... toEquip) {
      this(Arrays.stream(toEquip).map(ItemTarget::new).toArray(ItemTarget[]::new));
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      ItemTarget[] armorNotPresent = Arrays.stream(this.toEquip)
         .filter(
            targetx -> !this.controller.getItemStorage().hasItem(targetx.getMatches()) && !StorageHelper.isArmorEquipped(this.controller, targetx.getMatches())
         )
         .toArray(ItemTarget[]::new);
      if (armorNotPresent.length > 0) {
         this.setDebugState("Obtaining armor to equip.");
         return new CataloguedResourceTask(armorNotPresent);
      } else {
         this.setDebugState("Equipping armor.");

         for (ItemTarget target : this.toEquip) {
            if (!StorageHelper.isArmorEquipped(this.controller, target.getMatches())) {
               this.controller.getSlotHandler().forceEquipArmor(this.controller, target);
            }
         }

         return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      return Arrays.stream(this.toEquip).allMatch(target -> StorageHelper.isArmorEquipped(this.controller, target.getMatches()));
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof EquipArmorTask task ? Arrays.equals((Object[])task.toEquip, (Object[])this.toEquip) : false;
   }

   @Override
   protected String toDebugString() {
      return "Equipping armor: " + Arrays.toString((Object[])this.toEquip);
   }
}
