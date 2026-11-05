package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.Arg;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.commandsystem.CommandException;
import me.sailex.altoclef.tasks.misc.FarmTask;
import me.sailex.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class FarmCommand extends Command {
   public FarmCommand() throws CommandException {
      super(
         "farm",
         "Starts farming nearby crops automatically within range.  Example: `farm 10` to farm crops withing a range of 10 blocks",
         new Arg<>(Integer.class, "range")
      );
   }

   @Override
   protected void call(AltoClefController controller, ArgParser parser) throws CommandException {
      Integer range = parser.get(Integer.class);
      BlockPos origin = controller.getEntity().blockPosition();
      Task farmTask = new FarmTask(range, origin);
      controller.runUserTask(farmTask, () -> this.finish());
   }
}
