// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/commands/StashCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.commands;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.commandsystem.Arg;
import com.mineagent.sbe.altoclef.commandsystem.ArgParser;
import com.mineagent.sbe.altoclef.commandsystem.Command;
import com.mineagent.sbe.altoclef.commandsystem.CommandException;
import com.mineagent.sbe.altoclef.commandsystem.ItemList;
import com.mineagent.sbe.altoclef.tasks.container.StoreInStashTask;
import com.mineagent.sbe.altoclef.util.BlockRange;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;

public class StashCommand extends Command {
   public StashCommand() throws CommandException {
      super(
         "stash",
         "Store an item in a chest/container stash. Will deposit ALL non-equipped items if item list is empty.",
         new Arg<>(Integer.class, "x_start"),
         new Arg<>(Integer.class, "y_start"),
         new Arg<>(Integer.class, "z_start"),
         new Arg<>(Integer.class, "x_end"),
         new Arg<>(Integer.class, "y_end"),
         new Arg<>(Integer.class, "z_end"),
         new Arg<>(ItemList.class, "items (empty for ALL)", null, 6, false)
      );
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      BlockPos start = new BlockPos(parser.get(Integer.class), parser.get(Integer.class), parser.get(Integer.class));
      BlockPos end = new BlockPos(parser.get(Integer.class), parser.get(Integer.class), parser.get(Integer.class));
      ItemList itemList = parser.get(ItemList.class);
      ItemTarget[] items;
      if (itemList == null) {
         items = DepositCommand.getAllNonEquippedOrToolItemsAsTarget(mod);
      } else {
         items = itemList.items;
      }

      mod.runUserTask(new StoreInStashTask(true, new BlockRange(start, end, WorldHelper.getCurrentDimension(mod)), items), () -> this.finish());
   }
}
