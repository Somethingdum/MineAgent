// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/entity/AbstractKillEntityTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.entity;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.chains.MobDefenseChain;
import com.mineagent.sbe.altoclef.multiversion.item.ToolItemVer;
import com.mineagent.sbe.mixins.LivingEntityMixin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.helpers.LookHelper;
import com.mineagent.sbe.altoclef.util.helpers.StorageHelper;
import com.mineagent.sbe.altoclef.util.slots.PlayerSlot;
import java.util.List;

public abstract class AbstractKillEntityTask extends AbstractDoToEntityTask {
   private static final double OTHER_FORCE_FIELD_RANGE = 2.0;
   private static final double CONSIDER_COMBAT_RANGE = 10.0;

   protected AbstractKillEntityTask() {
      this(10.0, 2.0);
   }

   protected AbstractKillEntityTask(double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
      super(combatGuardLowerRange, combatGuardLowerFieldRadius);
   }

   protected AbstractKillEntityTask(double maintainDistance, double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
      super(maintainDistance, combatGuardLowerRange, combatGuardLowerFieldRadius);
   }

   public static Item bestWeapon(AltoClefController mod) {
      Item toolItem1 = null;
      List<ItemStack> invStacks = mod.getItemStorage().getItemStacksPlayerInventory(true);
      Item toolItem2 = MobDefenseChain.getBestWeapon(mod);
      if (toolItem2 != null) {
         return toolItem2;
      } else {
         Item item = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot(mod.getInventory())).getItem();
         float bestDamage = Float.NEGATIVE_INFINITY;

         if (ToolItemVer.isToolItem(item)) {
            bestDamage = ToolItemVer.getAttackDamage(item);
         }

         for (ItemStack invStack : invStacks) {
             Item toolItem = invStack.getItem();
            if (ToolItemVer.isToolItem(toolItem)) {
               float itemDamage = ToolItemVer.getAttackDamage(toolItem);
               if (itemDamage > bestDamage) {
                  toolItem1 = toolItem;
                  bestDamage = itemDamage;
               }
            }
         }

         return toolItem1;
      }
   }

   public static boolean equipWeapon(AltoClefController mod) {
      Item bestWeapon = bestWeapon(mod);
      Item equipedWeapon = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot(mod.getInventory())).getItem();
      if (bestWeapon != null && bestWeapon != equipedWeapon) {
         mod.getSlotHandler().forceEquipItem(bestWeapon);
         return true;
      } else {
         return false;
      }
   }

   @Override
   protected Task onEntityInteract(AltoClefController mod, Entity entity) {
      if (!equipWeapon(mod)) {
         float hitProg = this.getAttackCooldownProgress(mod.getPlayer(), 0.0F);
         if (hitProg >= 1.0F && (mod.getPlayer().onGround() || mod.getPlayer().getDeltaMovement().y() < 0.0 || mod.getPlayer().isInWater())) {
            LookHelper.lookAt(mod, entity.getEyePosition());
            mod.getControllerExtras().attack(entity);
         }
      }

      return null;
   }

   public float getAttackCooldownProgressPerTick(LivingEntity entity) {
      return 5.0F;
   }

   public float getAttackCooldownProgress(LivingEntity entity, float baseTime) {
      return Mth.clamp((((LivingEntityMixin)entity).getLastAttackedTicks() + baseTime) / this.getAttackCooldownProgressPerTick(entity), 0.0F, 1.0F);
   }
}
