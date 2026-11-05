package me.sailex.altoclef.tasks.speedrun.beatgame.prioritytask.tasks;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.util.RecipeTarget;

import java.util.function.Function;

public class RecraftableItemPriorityTask extends CraftItemPriorityTask {
   private final double recraftPriority;

   public RecraftableItemPriorityTask(double priority, double recraftPriority, RecipeTarget toCraft, Function<AltoClefController, Boolean> canCall) {
      super(priority, toCraft, canCall);
      this.recraftPriority = recraftPriority;
   }

   @Override
   protected double getPriority(AltoClefController mod) {
      return this.isSatisfied() ? this.recraftPriority : super.getPriority(mod);
   }
}
