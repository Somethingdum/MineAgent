// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/commands/InventoryCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.commands;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.commandsystem.Arg;
import com.mineagent.sbe.altoclef.commandsystem.ArgParser;
import com.mineagent.sbe.altoclef.commandsystem.Command;
import com.mineagent.sbe.altoclef.commandsystem.CommandException;
import com.mineagent.sbe.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;

public class InventoryCommand extends Command {
   public InventoryCommand() throws CommandException {
      super("inventory", "Prints the bot's inventory OR returns how many of an item the bot has", new Arg<>(String.class, "item", null, 1));
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      String item = parser.get(String.class);
      if (item == null) {
         HashMap<String, Integer> counts = new HashMap<>();

         for (int i = 0; i < mod.getInventory().getContainerSize(); i++) {
            ItemStack stack = mod.getInventory().getItem(i);
            if (!stack.isEmpty()) {
               String name = ItemHelper.stripItemName(stack.getItem());
               if (!counts.containsKey(name)) {
                  counts.put(name, 0);
               }

               counts.put(name, counts.get(name) + stack.getCount());
            }
         }

         mod.log("INVENTORY: ");

         for (String name : counts.keySet()) {
            mod.log(name + " : " + counts.get(name));
         }

         mod.log("(inventory list sent) ");
      } else {
         Item[] matches = TaskCatalogue.getItemMatches(item);
         if (matches == null || matches.length == 0) {
            mod.logWarning("Item \"" + item + "\" is not catalogued/recognized.");
            this.finish();
            return;
         }

         int count = mod.getItemStorage().getItemCount(matches);
         if (count == 0) {
            mod.log(item + " COUNT: (none)");
         } else {
            mod.log(item + " COUNT: " + count);
         }
      }

      this.finish();
   }
}
