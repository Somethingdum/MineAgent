// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/chains/WorldSurvivalChain.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.chains;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.multiversion.DimensionVer;
import com.mineagent.sbe.altoclef.tasks.DoToClosestBlockTask;
import com.mineagent.sbe.altoclef.tasks.InteractWithBlockTask;
import com.mineagent.sbe.altoclef.tasks.construction.PutOutFireTask;
import com.mineagent.sbe.altoclef.tasks.movement.EnterNetherPortalTask;
import com.mineagent.sbe.altoclef.tasks.movement.EscapeFromLavaTask;
import com.mineagent.sbe.altoclef.tasks.movement.GetToBlockTask;
import com.mineagent.sbe.altoclef.tasks.movement.SafeRandomShimmyTask;
import com.mineagent.sbe.altoclef.tasksystem.TaskRunner;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.helpers.LookHelper;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import com.mineagent.sbe.altoclef.util.time.TimerGame;
import com.mineagent.sbe.automatone.api.utils.Rotation;
import com.mineagent.sbe.automatone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.Optional;

public class WorldSurvivalChain extends SingleTaskChain {
   private final TimerGame wasInLavaTimer = new TimerGame(1.0);
   private final TimerGame portalStuckTimer = new TimerGame(5.0);
   private boolean wasAvoidingDrowning;
   private BlockPos extinguishWaterPosition;

   public WorldSurvivalChain(TaskRunner runner) {
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
         this.handleDrowning(mod);
         if (this.isInLavaOhShit(mod) && mod.getBehaviour().shouldEscapeLava()) {
            this.setTask(new EscapeFromLavaTask(mod));
            return 100.0F;
         } else if (this.isInFire(mod)) {
            this.setTask(new DoToClosestBlockTask(PutOutFireTask::new, Blocks.FIRE, Blocks.SOUL_FIRE));
            return 100.0F;
         } else {
            if (mod.getModSettings().shouldExtinguishSelfWithWater()) {
               if ((!(this.mainTask instanceof EscapeFromLavaTask) || !this.isCurrentlyRunning(mod))
                  && mod.getPlayer().isOnFire()
                  && !mod.getPlayer().hasEffect(MobEffects.FIRE_RESISTANCE)
                  && !DimensionVer.isUltrawarm(mod.getWorld())) {
                  if (mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
                     BlockPos targetWaterPos = mod.getPlayer().blockPosition();
                     if (WorldHelper.isSolidBlock(this.controller, targetWaterPos.below()) && WorldHelper.canPlace(this.controller, targetWaterPos)) {
                        Optional<Rotation> reach = LookHelper.getReach(this.controller, targetWaterPos.below(), Direction.UP);
                        if (reach.isPresent()) {
                           mod.getBaritone().getLookBehavior().updateTarget(reach.get(), true);
                           if (mod.getBaritone().getPlayerContext().isLookingAt(targetWaterPos.below())
                              && mod.getSlotHandler().forceEquipItem(Items.WATER_BUCKET)) {
                              this.extinguishWaterPosition = targetWaterPos;
                              mod.getInputControls().tryPress(Input.CLICK_RIGHT);
                              this.setTask(null);
                              return 90.0F;
                           }
                        }
                     }
                  }

                  this.setTask(new DoToClosestBlockTask(GetToBlockTask::new, Blocks.WATER));
                  return 90.0F;
               }

               if (mod.getItemStorage().hasItem(Items.BUCKET)
                  && this.extinguishWaterPosition != null
                  && mod.getBlockScanner().isBlockAtPosition(this.extinguishWaterPosition, Blocks.WATER)) {
                  this.setTask(new InteractWithBlockTask(new ItemTarget(Items.BUCKET, 1), Direction.UP, this.extinguishWaterPosition.below(), true));
                  return 60.0F;
               }

               this.extinguishWaterPosition = null;
            }

            if (this.isStuckInNetherPortal()) {
               mod.getExtraBaritoneSettings().setInteractionPaused(true);
            } else {
               this.portalStuckTimer.reset();
               mod.getExtraBaritoneSettings().setInteractionPaused(false);
            }

            if (this.portalStuckTimer.elapsed()) {
               this.setTask(new SafeRandomShimmyTask());
               return 60.0F;
            } else {
               return Float.NEGATIVE_INFINITY;
            }
         }
      }
   }

   private void handleDrowning(AltoClefController mod) {
      boolean avoidedDrowning = false;
      if (mod.getModSettings().shouldAvoidDrowning()
         && !mod.getBaritone().getPathingBehavior().isPathing()
         && mod.getPlayer().isInWater()
         && mod.getPlayer().getAirSupply() < mod.getPlayer().getMaxAirSupply()) {
         mod.getInputControls().hold(Input.JUMP);
         avoidedDrowning = true;
         this.wasAvoidingDrowning = true;
      }

      if (this.wasAvoidingDrowning && !avoidedDrowning) {
         this.wasAvoidingDrowning = false;
         mod.getInputControls().release(Input.JUMP);
      }
   }

   private boolean isInLavaOhShit(AltoClefController mod) {
      if (mod.getPlayer().isInLava() && !mod.getPlayer().hasEffect(MobEffects.FIRE_RESISTANCE)) {
         this.wasInLavaTimer.reset();
         return true;
      } else {
         return mod.getPlayer().isOnFire() && !this.wasInLavaTimer.elapsed();
      }
   }

   private boolean isInFire(AltoClefController mod) {
      if (mod.getPlayer().isOnFire() && !mod.getPlayer().hasEffect(MobEffects.FIRE_RESISTANCE)) {
         for (BlockPos pos : WorldHelper.getBlocksTouchingPlayer(this.controller.getPlayer())) {
            Block b = mod.getWorld().getBlockState(pos).getBlock();
            if (b instanceof BaseFireBlock) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean isStuckInNetherPortal() {
      return WorldHelper.isInNetherPortal(this.controller)
         && !this.controller.getUserTaskChain().getCurrentTask().thisOrChildSatisfies(task -> task instanceof EnterNetherPortalTask);
   }

   @Override
   public String getName() {
      return "Misc World Survival Chain";
   }

   @Override
   public boolean isActive() {
      return true;
   }

   @Override
   protected void onStop() {
      super.onStop();
   }
}
