// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/commands/LocateStructureCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.commands;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.commandsystem.Arg;
import com.mineagent.sbe.altoclef.commandsystem.ArgParser;
import com.mineagent.sbe.altoclef.commandsystem.Command;
import com.mineagent.sbe.altoclef.commandsystem.CommandException;
import com.mineagent.sbe.altoclef.tasks.movement.GoToStrongholdPortalTask;
import com.mineagent.sbe.altoclef.tasks.movement.LocateDesertTempleTask;

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
