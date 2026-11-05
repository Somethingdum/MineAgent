// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/SafeNetherPortalTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.control.InputControls;
import com.mineagent.sbe.altoclef.tasks.construction.PlaceStructureBlockTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.helpers.LookHelper;
import com.mineagent.sbe.altoclef.util.time.TimerGame;
import com.mineagent.sbe.automatone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import java.util.ArrayList;
import java.util.List;

public class SafeNetherPortalTask extends Task {
   private final TimerGame wait = new TimerGame(1.0);
   private boolean keyReset = false;
   private boolean finished = false;
   private List<BlockPos> positions = null;
   private List<Direction> directions = null;
   private Axis axis = null;

   @Override
   protected void onStart() {
      this.controller.getBaritone().getInputOverrideHandler().clearAllKeys();
      this.wait.reset();
   }

   @Override
   protected Task onTick() {
      if (!this.wait.elapsed()) {
         return null;
      } else {
         AltoClefController mod = this.controller;
         if (!this.keyReset) {
            this.keyReset = true;
            mod.getBaritone().getInputOverrideHandler().clearAllKeys();
         }

         if (mod.getPlayer().getPortalCooldown() < 10) {
            if (this.positions != null && this.directions != null) {
               BlockPos pos1 = mod.getPlayer().getOnPos().relative(this.axis, 1);
               BlockPos pos2 = mod.getPlayer().getOnPos().relative(this.axis, -1);
               if (mod.getWorld().getBlockState(pos1).isAir() || mod.getWorld().getBlockState(pos1).getBlock().equals(Blocks.SOUL_SAND)) {
                  boolean passed = false;

                  for (Direction dir : Direction.values()) {
                     if (mod.getWorld().getBlockState(pos1.above().relative(dir)).getBlock().equals(Blocks.NETHER_PORTAL)) {
                        passed = true;
                        break;
                     }
                  }

                  if (passed) {
                     return new ReplaceSafeBlock(pos1);
                  }
               }

               if (mod.getWorld().getBlockState(pos2).isAir() || mod.getWorld().getBlockState(pos2).getBlock().equals(Blocks.SOUL_SAND)) {
                  boolean passed = false;

                  for (Direction dirx : Direction.values()) {
                     if (mod.getWorld().getBlockState(pos2.above().relative(dirx)).getBlock().equals(Blocks.NETHER_PORTAL)) {
                        passed = true;
                        break;
                     }
                  }

                  if (passed) {
                     return new ReplaceSafeBlock(pos2);
                  }
               }
            }

            this.finished = true;
            this.setDebugState("We are not in a portal");
            return null;
         } else {
            BlockState state = mod.getWorld().getBlockState(mod.getPlayer().blockPosition());
            if (this.positions != null && this.directions != null) {
               for (BlockPos pos : this.positions) {
                  for (Direction dirxx : this.directions) {
                     BlockPos newPos = pos.below().relative(dirxx);
                     if (mod.getWorld().getBlockState(newPos).isAir() || mod.getWorld().getBlockState(newPos).getBlock().equals(Blocks.SOUL_SAND)) {
                        this.setDebugState("Changing block...");
                        return new ReplaceSafeBlock(newPos);
                     }
                  }
               }

               this.finished = true;
               this.setDebugState("Portal is safe");
               return null;
            } else {
               if (state.getBlock().equals(Blocks.NETHER_PORTAL)) {
                  this.axis = (Axis)state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
                  this.positions = new ArrayList<>();
                  this.positions.add(mod.getPlayer().blockPosition());

                  for (Direction dirxxx : Direction.values()) {
                     if (!dirxxx.getAxis().isVertical()) {
                        BlockPos pos = mod.getPlayer().blockPosition().relative(dirxxx);
                        if (mod.getWorld().getBlockState(pos).getBlock().equals(Blocks.NETHER_PORTAL)) {
                           this.positions.add(pos);
                        }
                     }
                  }

                  this.directions = List.of(Direction.WEST, Direction.EAST);
                  if (this.axis == Axis.X) {
                     this.directions = List.of(Direction.NORTH, Direction.SOUTH);
                  }
               } else {
                  this.finished = true;
                  this.setDebugState("We are not standing inside a nether portal block");
               }

               return null;
            }
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      InputControls controls = this.controller.getInputControls();
      controls.release(Input.MOVE_FORWARD);
      controls.release(Input.SNEAK);
      controls.release(Input.CLICK_LEFT);
      this.controller.getBaritone().getInputOverrideHandler().clearAllKeys();
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof SafeNetherPortalTask;
   }

   @Override
   protected String toDebugString() {
      return "Making nether portal safe";
   }

   @Override
   public boolean isFinished() {
      return this.finished;
   }

   private static class ReplaceSafeBlock extends Task {
      private final BlockPos pos;
      private boolean finished = false;

      public ReplaceSafeBlock(BlockPos pos) {
         this.pos = pos;
      }

      @Override
      protected void onStart() {
         this.controller.getBaritone().getInputOverrideHandler().clearAllKeys();
      }

      @Override
      protected Task onTick() {
         AltoClefController mod = this.controller;
         if (mod.getWorld().getBlockState(this.pos).isAir()) {
            this.setDebugState("Placing block...");
            return new PlaceStructureBlockTask(this.pos);
         } else if (!this.controller.getWorld().getBlockState(this.pos).getBlock().equals(Blocks.SOUL_SAND)) {
            this.finished = true;
            return null;
         } else {
            LookHelper.lookAt(mod, this.pos);
            if (mod.getPlayer().pick(3.0, 0.0F, true) instanceof BlockHitResult blockHitResult
               && mod.getWorld().getBlockState(blockHitResult.getBlockPos()).getBlock().equals(Blocks.NETHER_PORTAL)) {
               this.setDebugState("Getting closer to target...");
               mod.getInputControls().hold(Input.MOVE_FORWARD);
               mod.getInputControls().hold(Input.SNEAK);
            } else {
               this.setDebugState("Breaking block");
               mod.getInputControls().release(Input.MOVE_FORWARD);
               mod.getInputControls().release(Input.SNEAK);
               mod.getInputControls().hold(Input.CLICK_LEFT);
            }

            return null;
         }
      }

      @Override
      protected void onStop(Task interruptTask) {
         InputControls controls = this.controller.getInputControls();
         controls.release(Input.MOVE_FORWARD);
         controls.release(Input.SNEAK);
         controls.release(Input.CLICK_LEFT);
         this.controller.getBaritone().getInputOverrideHandler().clearAllKeys();
      }

      @Override
      public boolean isFinished() {
         return this.finished;
      }

      @Override
      protected boolean isEqual(Task other) {
         return other instanceof ReplaceSafeBlock same && same.pos.equals(this.pos);
      }

      @Override
      protected String toDebugString() {
         return "Making sure " + this.pos + " is safe";
      }
   }
}
