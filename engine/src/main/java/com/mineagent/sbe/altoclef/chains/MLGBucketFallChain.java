// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/chains/MLGBucketFallChain.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.chains;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.altoclef.tasks.movement.MLGBucketTask;
import com.mineagent.sbe.altoclef.tasksystem.ITaskOverridesGrounded;
import com.mineagent.sbe.altoclef.tasksystem.TaskRunner;
import com.mineagent.sbe.altoclef.util.helpers.LookHelper;
import com.mineagent.sbe.altoclef.util.time.TimerGame;
import com.mineagent.sbe.automatone.api.utils.Rotation;
import com.mineagent.sbe.automatone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import java.util.Optional;

public class MLGBucketFallChain extends SingleTaskChain implements ITaskOverridesGrounded {
   private final TimerGame tryCollectWaterTimer = new TimerGame(4.0);
   private final TimerGame pickupRepeatTimer = new TimerGame(0.25);
   private MLGBucketTask lastMLG = null;
   private boolean wasPickingUp = false;
   private boolean doingChorusFruit = false;

   public MLGBucketFallChain(TaskRunner runner) {
      super(runner);
   }

   @Override
   protected void onTaskFinish(AltoClefController mod) {
   }

   @Override
   public float getPriority() {
      if (!AltoClefController.inGame()) {
         return Float.NEGATIVE_INFINITY;
      } else {
         AltoClefController mod = this.controller;
         if (this.isFalling(mod)) {
            this.tryCollectWaterTimer.reset();
            this.setTask(new MLGBucketTask());
            this.lastMLG = (MLGBucketTask)this.mainTask;
            return 100.0F;
         } else {
            if (!this.tryCollectWaterTimer.elapsed()
               && mod.getItemStorage().hasItem(Items.BUCKET)
               && !mod.getItemStorage().hasItem(Items.WATER_BUCKET)
               && this.lastMLG != null) {
               BlockPos placed = this.lastMLG.getWaterPlacedPos();

               boolean isPlacedWater;
               try {
                  isPlacedWater = mod.getWorld().getBlockState(placed).getBlock() == Blocks.WATER;
               } catch (Exception var6) {
                  isPlacedWater = false;
               }

               if (placed != null && placed.closerToCenterThan(EntityVer.getPos(mod.getPlayer()), 5.5) && isPlacedWater) {
                  mod.getBehaviour().push();
                  mod.getBehaviour().setRayTracingFluidHandling(Fluid.SOURCE_ONLY);
                  Optional<Rotation> reach = LookHelper.getReach(this.controller, placed, Direction.UP);
                  if (reach.isPresent()) {
                     mod.getBaritone().getLookBehavior().updateTarget(reach.get(), true);
                     if (mod.getBaritone().getPlayerContext().isLookingAt(placed) && mod.getSlotHandler().forceEquipItem(Items.BUCKET)) {
                        if (this.pickupRepeatTimer.elapsed()) {
                           this.pickupRepeatTimer.reset();
                           mod.getInputControls().tryPress(Input.CLICK_RIGHT);
                           this.wasPickingUp = true;
                        } else if (this.wasPickingUp) {
                           this.wasPickingUp = false;
                        }
                     }
                  } else {
                     this.setTask(TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1));
                  }

                  mod.getBehaviour().pop();
                  return 60.0F;
               }
            }

            if (this.wasPickingUp) {
               this.wasPickingUp = false;
               this.lastMLG = null;
            }

            if (mod.getPlayer().hasEffect(MobEffects.LEVITATION)
               && ((MobEffectInstance)mod.getPlayer().getActiveEffectsMap().get(MobEffects.LEVITATION)).getDuration() <= 70
               && mod.getItemStorage().hasItemInventoryOnly(Items.CHORUS_FRUIT)
               && !mod.getItemStorage().hasItemInventoryOnly(Items.WATER_BUCKET)) {
               this.doingChorusFruit = true;
               mod.getSlotHandler().forceEquipItem(Items.CHORUS_FRUIT);
               mod.getInputControls().hold(Input.CLICK_RIGHT);
               mod.getExtraBaritoneSettings().setInteractionPaused(true);
            } else if (this.doingChorusFruit) {
               this.doingChorusFruit = false;
               mod.getInputControls().release(Input.CLICK_RIGHT);
               mod.getExtraBaritoneSettings().setInteractionPaused(false);
            }

            this.lastMLG = null;
            return Float.NEGATIVE_INFINITY;
         }
      }
   }

   @Override
   public String getName() {
      return "MLG Water Bucket Fall Chain";
   }

   @Override
   public boolean isActive() {
      return true;
   }

   public boolean doneMLG() {
      return this.lastMLG == null;
   }

   public boolean isChorusFruiting() {
      return this.doingChorusFruit;
   }

   public boolean isFalling(AltoClefController mod) {
      if (!mod.getModSettings().shouldAutoMLGBucket()) {
         return false;
      } else if (!mod.getPlayer().isSwimming() && !mod.getPlayer().isInWater() && !mod.getPlayer().onGround() && !mod.getPlayer().onClimbable()) {
         double ySpeed = mod.getPlayer().getDeltaMovement().y;
         return ySpeed < -0.7;
      } else {
         return false;
      }
   }
}
