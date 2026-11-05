package me.sailex.altoclef.commands.random;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.tasks.speedrun.OneCycleTask;

public class CycleTestCommand extends Command {
   public CycleTestCommand() {
      super("cycle", "One cycles the dragon B)");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) {
      mod.runUserTask(new OneCycleTask(), () -> this.finish());
   }
}
