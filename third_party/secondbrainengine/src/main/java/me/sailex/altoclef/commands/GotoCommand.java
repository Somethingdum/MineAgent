package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.Arg;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.commandsystem.CommandException;
import me.sailex.altoclef.commandsystem.GotoTarget;
import me.sailex.altoclef.tasks.movement.DefaultGoToDimensionTask;
import me.sailex.altoclef.tasks.movement.GetToBlockTask;
import me.sailex.altoclef.tasks.movement.GetToXZTask;
import me.sailex.altoclef.tasks.movement.GetToYTask;
import me.sailex.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class GotoCommand extends Command {
   public GotoCommand() throws CommandException {
      super(
         "goto",
         "Tell bot to travel to a set of coordinates",
         new Arg<>(GotoTarget.class, "[x y z dimension]/[x z dimension]/[y dimension]/[dimension]/[x y z]/[x z]/[y]")
      );
   }

   public static Task getMovementTaskFor(GotoTarget target) {
      return (Task)(switch (target.getType()) {
         case XYZ -> new GetToBlockTask(new BlockPos(target.getX(), target.getY(), target.getZ()), target.getDimension());
         case XZ -> new GetToXZTask(target.getX(), target.getZ(), target.getDimension());
         case Y -> new GetToYTask(target.getY(), target.getDimension());
         case NONE -> new DefaultGoToDimensionTask(target.getDimension());
      });
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      GotoTarget target = parser.get(GotoTarget.class);
      mod.runUserTask(getMovementTaskFor(target), () -> this.finish());
   }
}
