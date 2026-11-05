package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.Arg;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.commandsystem.CommandException;
import me.sailex.altoclef.tasks.movement.GoToStrongholdPortalTask;
import me.sailex.altoclef.tasks.movement.LocateDesertTempleTask;

public class LocateStructureCommand extends Command {
   public LocateStructureCommand() throws CommandException {
      super(
         "locate_structure",
         "Locate a world generated structure. Only works for stronghold and desert_temple",
         new Arg<>(Structure.class, "structure")
      );
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      Structure structure = parser.get(Structure.class);
      switch (structure) {
         case STRONGHOLD:
            mod.runUserTask(new GoToStrongholdPortalTask(1), () -> this.finish());
            break;
         case DESERT_TEMPLE:
            mod.runUserTask(new LocateDesertTempleTask(), () -> this.finish());
      }
   }

   public static enum Structure {
      DESERT_TEMPLE,
      STRONGHOLD;
   }
}
