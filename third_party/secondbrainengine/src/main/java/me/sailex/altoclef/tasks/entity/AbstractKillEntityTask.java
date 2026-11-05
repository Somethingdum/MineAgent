package me.sailex.altoclef.tasks.entity;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.chains.MobDefenseChain;
import me.sailex.altoclef.multiversion.item.ToolItemVer;
import me.sailex.mixins.LivingEntityMixin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.helpers.LookHelper;
import me.sailex.altoclef.util.helpers.StorageHelper;
import me.sailex.altoclef.util.slots.PlayerSlot;
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
