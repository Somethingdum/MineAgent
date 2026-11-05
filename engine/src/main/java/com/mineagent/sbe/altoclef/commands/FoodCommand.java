// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/commands/FoodCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.commands;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.commandsystem.Arg;
import com.mineagent.sbe.altoclef.commandsystem.ArgParser;
import com.mineagent.sbe.altoclef.commandsystem.Command;
import com.mineagent.sbe.altoclef.commandsystem.CommandException;
import com.mineagent.sbe.altoclef.tasks.resources.CollectFoodTask;
import com.mineagent.sbe.altoclef.util.helpers.StorageHelper;

public class FoodCommand extends Command {
   public FoodCommand() throws CommandException {
      super("food", "Collects a certain amount of food. Example: `food 10` to collect 10 units of food.", new Arg<>(Integer.class, "count"));
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      int foodPoints = parser.get(Integer.class);
      foodPoints += StorageHelper.calculateInventoryFoodScore(mod);
      mod.runUserTask(new CollectFoodTask(foodPoints), () -> this.finish());
   }
}
