package me.sailex.altoclef.commands.random;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.commandsystem.CommandException;
import me.sailex.altoclef.tasksystem.Task;

public class DummyTaskCommand extends Command {
   public DummyTaskCommand() {
      super("dummy", "Doesnt do anything");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      mod.runUserTask(new DummyTask(), () -> this.finish());
   }

   private class DummyTask extends Task {
      @Override
      protected void onStart() {
      }

      @Override
      protected Task onTick() {
         return null;
      }

      @Override
      protected void onStop(Task interruptTask) {
      }

      @Override
      protected boolean isEqual(Task other) {
         return false;
      }

      @Override
      protected String toDebugString() {
         return null;
      }
   }
}
