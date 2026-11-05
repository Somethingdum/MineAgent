package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;

public class StopCommand extends Command {
   public StopCommand() {
      super("stop", "Stop task runner (stops all automation), also stops the IDLE task until a new task is started");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) {
      mod.stop();
      this.finish();
   }
}
