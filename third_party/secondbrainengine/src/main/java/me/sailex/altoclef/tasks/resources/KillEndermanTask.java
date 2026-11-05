package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.multiversion.EntityVer;
import me.sailex.altoclef.tasks.ResourceTask;
import me.sailex.altoclef.tasks.entity.KillEntitiesTask;
import me.sailex.altoclef.tasks.entity.KillEntityTask;
import me.sailex.altoclef.tasks.movement.GetWithinRangeOfBlockTask;
import me.sailex.altoclef.tasks.movement.TimeoutWanderTask;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.Dimension;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.helpers.WorldHelper;
import me.sailex.altoclef.util.time.TimerGame;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.util.Optional;
import java.util.function.Predicate;

public class KillEndermanTask extends ResourceTask {
   private final int count;
   private final TimerGame lookDelay = new TimerGame(0.2);

   public KillEndermanTask(int count) {
      super(new ItemTarget(Items.ENDER_PEARL, count));
      this.count = count;
      this.forceDimension(Dimension.NETHER);
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (!mod.getEntityTracker().entityFound(EnderMan.class)) {
         if (WorldHelper.getCurrentDimension(mod) != Dimension.NETHER) {
            return this.getToCorrectDimensionTask(mod);
         } else {
            Optional<BlockPos> nearest = mod.getBlockScanner()
               .getNearestBlock(Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.WARPED_HYPHAE, Blocks.WARPED_NYLIUM);
            if (nearest.isPresent()) {
               if (WorldHelper.inRangeXZ(nearest.get(), mod.getPlayer().blockPosition(), 40.0)) {
                  this.setDebugState("Waiting for endermen to spawn...");
                  return null;
               } else {
                  this.setDebugState("Getting to warped forest biome");
                  return new GetWithinRangeOfBlockTask(nearest.get(), 35);
               }
            } else {
               this.setDebugState("Warped forest biome not found");
               return new TimeoutWanderTask();
            }
         }
      } else {
         Predicate<Entity> belowNetherRoof = entityx -> WorldHelper.getCurrentDimension(mod) != Dimension.NETHER || entityx.getY() < 125.0;
         int TOO_FAR_AWAY = WorldHelper.getCurrentDimension(mod) == Dimension.NETHER ? 10 : 256;

         for (EnderMan entity : mod.getEntityTracker().getTrackedEntities(EnderMan.class)) {
            if (entity.isAlive() && belowNetherRoof.test(entity) && entity.isCreepy() && EntityVer.getPos(entity).closerThan(EntityVer.getPos(mod.getPlayer()), TOO_FAR_AWAY)
               )
             {
               return new KillEntityTask(entity);
            }
         }

         return new KillEntitiesTask(belowNetherRoof, EnderMan.class);
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof KillEndermanTask task ? task.count == this.count : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Hunting endermen for pearls - " + this.controller.getItemStorage().getItemCount(Items.ENDER_PEARL) + "/" + this.count;
   }
}
