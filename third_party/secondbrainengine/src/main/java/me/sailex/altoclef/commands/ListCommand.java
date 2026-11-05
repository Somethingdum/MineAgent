package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.TaskCatalogue;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.commandsystem.CommandException;

import java.util.Arrays;

public class ListCommand extends Command {
   public ListCommand() {
      super("list", "List all obtainable items");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      mod.log("#### LIST OF ALL OBTAINABLE ITEMS ####");
      mod.log(Arrays.toString(TaskCatalogue.resourceNames().toArray()));
      mod.log("############# END LIST ###############");
   }
}
