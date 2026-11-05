package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.commandsystem.CommandException;
import me.sailex.altoclef.tasks.entity.HeroTask;

public class HeroCommand extends Command {
   public HeroCommand() {
      super("hero", "Kill all hostile mobs");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      mod.runUserTask(new HeroTask(), () -> this.finish());
   }
}
