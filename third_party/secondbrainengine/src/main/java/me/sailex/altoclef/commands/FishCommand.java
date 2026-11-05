package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.commandsystem.CommandException;
import me.sailex.altoclef.tasks.misc.FishTask;

public class FishCommand extends Command {
   public FishCommand() throws CommandException {
      super("fish", "Starts fishing automatically.  Example: `fish` to start fishing. NEEDS FISHING ROD");
   }

   @Override
   protected void call(AltoClefController controller, ArgParser parser) throws CommandException {
      controller.runUserTask(new FishTask(), () -> this.finish());
   }
}
