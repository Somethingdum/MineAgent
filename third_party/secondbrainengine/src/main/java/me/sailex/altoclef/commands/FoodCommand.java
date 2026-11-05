package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.Arg;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.commandsystem.CommandException;
import me.sailex.altoclef.tasks.resources.CollectFoodTask;
import me.sailex.altoclef.util.helpers.StorageHelper;

public class FoodCommand extends Command {
   public FoodCommand() throws CommandException {
      super("food", "Collects a certain amount of food. Example: `food 10` to collect 10 units of food.", new Arg<>(Integer.class, "count"));
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      int foodPoints = parser.get(Integer.class);
      foodPoints += StorageHelper.calculateInventoryFoodScore(mod);
      mod.runUserTask(new CollectFoodTask(foodPoints), () -> this.finish());
   }
}
