package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;

public class PauseCommand extends Command {
   public PauseCommand() {
      super("pause", "Pauses the bot after the task thats running (Still in development!)");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) {
      mod.setStoredTask(mod.getUserTaskChain().getCurrentTask());
      mod.setPaused(true);
      mod.getUserTaskChain().stop();
      mod.log("Pausing Bot and time");
      this.finish();
   }
}
