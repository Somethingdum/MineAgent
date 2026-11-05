package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.Arg;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.commandsystem.CommandException;
import me.sailex.altoclef.tasks.resources.CollectMeatTask;
import me.sailex.altoclef.util.helpers.StorageHelper;

public class MeatCommand extends Command {
   public MeatCommand() throws CommandException {
      super(
         "meat",
         "Collects a certain amount of food units of meat. ex. `meat 10` collects 10 units of food (half of the entire hunger bar)",
         new Arg<>(Integer.class, "count")
      );
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      int count = parser.get(Integer.class);
      count += StorageHelper.calculateInventoryFoodScore(mod);
      mod.runUserTask(new CollectMeatTask(count), () -> this.finish());
   }
}
