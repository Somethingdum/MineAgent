// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/chains/FoodChain.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.chains;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.Settings;
import com.mineagent.sbe.altoclef.multiversion.FoodComponentWrapper;
import com.mineagent.sbe.altoclef.multiversion.item.ItemVer;
import com.mineagent.sbe.altoclef.tasks.resources.CollectFoodTask;
import com.mineagent.sbe.altoclef.tasks.speedrun.DragonBreathTracker;
import com.mineagent.sbe.altoclef.tasksystem.TaskRunner;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.helpers.ConfigHelper;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import com.mineagent.sbe.automatone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Optional;

public class FoodChain extends SingleTaskChain {
   private static FoodChainConfig config;
   private static boolean hasFood;
   private final DragonBreathTracker dragonBreathTracker = new DragonBreathTracker();
   private boolean isTryingToEat = false;
   private boolean requestFillup = false;
   private boolean needsToCollectFood = false;
   private Optional<Item> cachedPerfectFood = Optional.empty();
   private boolean shouldStop = false;

   public FoodChain(TaskRunner runner) {
      super(runner);
   }

   @Override
   protected void onTaskFinish(AltoClefController controller) {
   }

   private void startEat(AltoClefController controller, Item food) {
      controller.getSlotHandler().forceEquipItem(new ItemTarget(food), true);
      controller.getBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
      controller.getExtraBaritoneSettings().setInteractionPaused(true);
      this.isTryingToEat = true;
      this.requestFillup = true;
   }

   private void stopEat(AltoClefController controller) {
      if (this.isTryingToEat) {
         controller.getBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
         controller.getExtraBaritoneSettings().setInteractionPaused(false);
         this.isTryingToEat = false;
         this.requestFillup = false;
         if (controller.getItemStorage().hasItem(Items.SHIELD) && !controller.getItemStorage().hasItemInOffhand(controller, Items.SHIELD)) {
            controller.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
         }
      }
   }

   public boolean isTryingToEat() {
      return this.isTryingToEat;
   }

   @Override
   public float getPriority() {
      if (this.controller == null) {
         return Float.NEGATIVE_INFINITY;
      } else if (WorldHelper.isInNetherPortal(this.controller)) {
         this.stopEat(this.controller);
         return Float.NEGATIVE_INFINITY;
      } else if (this.controller.getMobDefenseChain().isShielding()) {
         this.stopEat(this.controller);
         return Float.NEGATIVE_INFINITY;
      } else {
         this.dragonBreathTracker.updateBreath(this.controller);

         for (BlockPos playerIn : WorldHelper.getBlocksTouchingPlayer(this.controller.getEntity())) {
            if (this.dragonBreathTracker.isTouchingDragonBreath(playerIn)) {
               this.stopEat(this.controller);
               return Float.NEGATIVE_INFINITY;
            }
         }

         if (this.controller.getModSettings().isAutoEat() && !this.controller.getEntity().isInLava() && !this.shouldStop) {
            if (this.controller.getMLGBucketChain().doneMLG() && !this.controller.getMLGBucketChain().isFalling(this.controller)) {
               Tuple<Integer, Optional<Item>> calculation = this.calculateFood(this.controller);
               int foodScore = (Integer)calculation.getA();
               this.cachedPerfectFood = (Optional<Item>)calculation.getB();
               hasFood = foodScore > 0;
               if (this.requestFillup && this.controller.getBaritone().getPlayerContext().entity().getFoodData().getFoodLevel() >= 20) {
                  this.requestFillup = false;
               }

               if (!hasFood) {
                  this.requestFillup = false;
               }

               if (hasFood && (this.needsToEat() || this.requestFillup) && this.cachedPerfectFood.isPresent()) {
                  this.startEat(this.controller, this.cachedPerfectFood.get());
               } else {
                  this.stopEat(this.controller);
               }

               Settings settings = this.controller.getModSettings();
               if (this.needsToCollectFood || foodScore < settings.getMinimumFoodAllowed()) {
                  this.needsToCollectFood = foodScore < settings.getFoodUnitsToCollect();
                  if (this.needsToCollectFood) {
                     this.setTask(new CollectFoodTask(settings.getFoodUnitsToCollect()));
                     return 55.0F;
                  }
               }

               this.setTask(null);
               return Float.NEGATIVE_INFINITY;
            } else {
               this.stopEat(this.controller);
               return Float.NEGATIVE_INFINITY;
            }
         } else {
            this.stopEat(this.controller);
            return Float.NEGATIVE_INFINITY;
         }
      }
   }

   @Override
   public String getName() {
      return "Food chain";
   }

   @Override
   protected void onStop() {
      super.onStop();
      if (this.controller != null) {
         this.stopEat(this.controller);
      }
   }

   public boolean needsToEat() {
      if (hasFood && !this.shouldStop) {
         LivingEntity player = this.controller.getEntity();
         int foodLevel = this.controller.getBaritone().getPlayerContext().entity().getFoodData().getFoodLevel();
         float health = player.getHealth();
         if (foodLevel >= 20) {
            return false;
         } else if (health <= 10.0F) {
            return true;
         } else if (player.isOnFire() || player.hasEffect(MobEffects.WITHER) || health < config.alwaysEatWhenWitherOrFireAndHealthBelow) {
            return true;
         } else if (foodLevel <= config.alwaysEatWhenBelowHunger) {
            return true;
         } else if (health < config.alwaysEatWhenBelowHealth) {
            return true;
         } else if (foodLevel < config.alwaysEatWhenBelowHungerAndPerfectFit && this.cachedPerfectFood.isPresent()) {
            int need = 20 - foodLevel;
            Item best = this.cachedPerfectFood.get();
            int fills = Optional.ofNullable(ItemVer.getFoodComponent(best)).map(FoodComponentWrapper::getHunger).orElse(-1);
            return fills > 0 && fills <= need;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private Tuple<Integer, Optional<Item>> calculateFood(AltoClefController controller) {
      Item bestFood = null;
      double bestFoodScore = Double.NEGATIVE_INFINITY;
      int foodTotal = 0;
      LivingEntity player = controller.getEntity();
      float health = player.getHealth();
      float hunger = controller.getBaritone().getPlayerContext().entity().getFoodData().getFoodLevel();
      float saturation = controller.getBaritone().getPlayerContext().entity().getFoodData().getSaturationLevel();

      for (ItemStack stack : controller.getItemStorage().getItemStacksPlayerInventory(true)) {
         if (ItemVer.isFood(stack) && !stack.is(Items.SPIDER_EYE)) {
            FoodComponentWrapper food = ItemVer.getFoodComponent(stack.getItem());
            if (food != null) {
               float hungerIfEaten = Math.min(hunger + food.getHunger(), 20.0F);
               float saturationIfEaten = Math.min(hungerIfEaten, saturation + food.getSaturationModifier());
               float gainedSaturation = saturationIfEaten - saturation;
               float gainedHunger = hungerIfEaten - hunger;
               float hungerWasted = food.getHunger() - gainedHunger;
               float score = gainedSaturation * 2.0F - hungerWasted;
               if (stack.is(Items.ROTTEN_FLESH)) {
                  score -= 100.0F;
               }

               if (score > bestFoodScore) {
                  bestFoodScore = score;
                  bestFood = stack.getItem();
               }

               foodTotal += food.getHunger() * stack.getCount();
            }
         }
      }

      return new Tuple(foodTotal, Optional.ofNullable(bestFood));
   }

   public boolean hasFood() {
      return hasFood;
   }

   public void shouldStop(boolean shouldStopInput) {
      this.shouldStop = shouldStopInput;
   }

   public boolean isShouldStop() {
      return this.shouldStop;
   }

   static {
      ConfigHelper.loadConfig(
         "configs/food_chain_settings.json", FoodChainConfig::new, FoodChainConfig.class, newConfig -> config = newConfig
      );
   }

   static class FoodChainConfig {
      public int alwaysEatWhenWitherOrFireAndHealthBelow = 6;
      public int alwaysEatWhenBelowHunger = 10;
      public int alwaysEatWhenBelowHealth = 14;
      public int alwaysEatWhenBelowHungerAndPerfectFit = 15;
   }
}
