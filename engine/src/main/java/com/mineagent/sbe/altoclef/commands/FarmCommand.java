// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/commands/FarmCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.commands;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.commandsystem.Arg;
import com.mineagent.sbe.altoclef.commandsystem.ArgParser;
import com.mineagent.sbe.altoclef.commandsystem.Command;
import com.mineagent.sbe.altoclef.commandsystem.CommandException;
import com.mineagent.sbe.altoclef.tasks.misc.FarmTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class FarmCommand extends Command {
   public FarmCommand() throws CommandException {
      super(
         "farm",
         "Starts farming nearby crops automatically within range.  Example: `farm 10` to farm crops withing a range of 10 blocks",
         new Arg<>(Integer.class, "range")
      );
   }

   @Override
   protected void call(AltoClefController controller, ArgParser parser) throws CommandException {
      Integer range = parser.get(Integer.class);
      BlockPos origin = controller.getEntity().blockPosition();
      Task farmTask = new FarmTask(range, origin);
      controller.runUserTask(farmTask, () -> this.finish());
   }
}
