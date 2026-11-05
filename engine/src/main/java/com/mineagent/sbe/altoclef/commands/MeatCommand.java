// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/commands/MeatCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.commands;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.commandsystem.Arg;
import com.mineagent.sbe.altoclef.commandsystem.ArgParser;
import com.mineagent.sbe.altoclef.commandsystem.Command;
import com.mineagent.sbe.altoclef.commandsystem.CommandException;
import com.mineagent.sbe.altoclef.tasks.resources.CollectMeatTask;
import com.mineagent.sbe.altoclef.util.helpers.StorageHelper;

public class MeatCommand extends Command {
   public MeatCommand() throws CommandException {
      super(
         "meat",
         "Collects a certain amount of food units of meat. ex. `meat 10` collects 10 units of food (half of the entire hunger bar)",
         new Arg<>(Integer.class, "count")
      );
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      int count = parser.get(Integer.class);
      count += StorageHelper.calculateInventoryFoodScore(mod);
      mod.runUserTask(new CollectMeatTask(count), () -> this.finish());
   }
}
