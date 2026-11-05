package me.sailex.altoclef.chains;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.tasks.entity.AbstractKillEntityTask;
import me.sailex.altoclef.tasksystem.TaskChain;
import me.sailex.altoclef.tasksystem.TaskRunner;
import me.sailex.automatone.api.pathing.calc.IPath;
import me.sailex.automatone.api.pathing.movement.IMovement;
import me.sailex.automatone.pathing.movement.Movement;
import me.sailex.automatone.utils.BlockStateInterface;

import java.util.Optional;

public class PreEquipItemChain extends SingleTaskChain {
   public PreEquipItemChain(TaskRunner runner) {
      super(runner);
   }

   @Override
   protected void onTaskFinish(AltoClefController mod) {
   }

   @Override
   public float getPriority() {
      this.update(this.controller);
      return -1.0F;
   }

   private void update(AltoClefController mod) {
      if (!mod.getFoodChain().isTryingToEat()) {
         TaskChain currentChain = mod.getTaskRunner().getCurrentTaskChain();
         if (currentChain != null) {
            Optional<IPath> pathOptional = mod.getBaritone().getPathingBehavior().getPath();
            if (!pathOptional.isEmpty()) {
               IPath path = pathOptional.get();
               BlockStateInterface bsi = new BlockStateInterface(this.controller.getBaritone().getPlayerContext());

               for (IMovement iMovement : path.movements()) {
                  Movement movement = (Movement)iMovement;
                  if (movement.toBreak(bsi).stream().anyMatch(pos -> mod.getWorld().getBlockState(pos).getBlock().defaultDestroyTime() > 0.0F)
                     || !movement.toPlace(bsi).isEmpty()) {
                     return;
                  }
               }

               if (currentChain.getTasks().stream().anyMatch(task -> task instanceof AbstractKillEntityTask)) {
                  AbstractKillEntityTask.equipWeapon(mod);
               }
            }
         }
      }
   }

   @Override
   public String getName() {
      return "pre-equip item chain";
   }

   @Override
   public boolean isActive() {
      return true;
   }
}
