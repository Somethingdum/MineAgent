// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/commands/GetCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.commands;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.commandsystem.Arg;
import com.mineagent.sbe.altoclef.commandsystem.ArgParser;
import com.mineagent.sbe.altoclef.commandsystem.Command;
import com.mineagent.sbe.altoclef.commandsystem.CommandException;
import com.mineagent.sbe.altoclef.commandsystem.ItemList;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;

import java.util.ArrayList;
import java.util.List;

public class GetCommand extends Command {
   public GetCommand() throws CommandException {
      super(
         "get",
         "Get a resource or Craft an item in Minecraft. You can craft item even if you don't have ingredients in inventory already. Examples: `get log 20` gets 20 logs, `get diamond_chestplate 1` gets 1 diamond chestplate. For equipments you have to specify the type of equipments like wooden, stone, iron, golden and diamond.",
         new Arg<>(ItemList.class, "items")
      );
   }

   private void getItems(AltoClefController mod, ItemTarget... items) {
      List<ItemTarget> resultTargets = new ArrayList<>();

      for (ItemTarget target : items) {
         int count = target.getTargetCount();
         count += mod.getItemStorage().getItemCountInventoryOnly(target.getMatches());
         resultTargets.add(new ItemTarget(target, count));
      }
      items = resultTargets.toArray(new ItemTarget[0]);

      if (items.length != 0) {
         Task targetTask;
         if (items.length == 1) {
            targetTask = TaskCatalogue.getItemTask(items[0]);
         } else {
            targetTask = TaskCatalogue.getSquashedItemTask(items);
         }

         if (targetTask != null) {
            mod.runUserTask(targetTask, () -> this.finish());
         } else {
            this.finish();
         }
      } else {
         mod.log("You must specify at least one item!");
         this.finish();
      }
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      ItemList items = parser.get(ItemList.class);
      this.getItems(mod, items.items);
   }
}
