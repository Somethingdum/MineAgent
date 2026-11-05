package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.tasksystem.Task;

import java.util.List;

public class StatusCommand extends Command {
   public StatusCommand() {
      super("status", "Get status of currently executing command");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) {
      List<Task> tasks = mod.getUserTaskChain().getTasks();
      if (tasks.isEmpty()) {
         mod.log("No tasks currently running.");
      } else {
         mod.log("CURRENT TASK: " + tasks.get(0).toString());
      }

      this.finish();
   }
}
