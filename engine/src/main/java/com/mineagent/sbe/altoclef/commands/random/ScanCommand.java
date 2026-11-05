// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/commands/random/ScanCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.commands.random;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.commands.BlockScanner;
import com.mineagent.sbe.altoclef.commandsystem.Arg;
import com.mineagent.sbe.altoclef.commandsystem.ArgParser;
import com.mineagent.sbe.altoclef.commandsystem.Command;
import com.mineagent.sbe.altoclef.commandsystem.CommandException;
import com.mineagent.sbe.altoclef.util.helpers.FuzzySearchHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ScanCommand extends Command {
   public ScanCommand() throws CommandException {
      super("scan", "Locates nearest block", new Arg<>(String.class, "block", "DIRT", 0));
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      String blockStr = parser.get(String.class);
      Field[] declaredFields = Blocks.class.getDeclaredFields();
      Block block = null;
      List<String> allBlockNames = new ArrayList<>();

      for (Field field : declaredFields) {
         field.setAccessible(true);

         try {
            String fieldName = field.getName();
            allBlockNames.add(fieldName.toLowerCase());
            if (fieldName.equalsIgnoreCase(blockStr)) {
               block = (Block) field.get(Blocks.class);
            }
         } catch (IllegalAccessException var12) {
            throw new RuntimeException(var12);
         }

         field.setAccessible(false);
      }

      if (block == null) {
         String closest = FuzzySearchHelper.getClosestMatchMinecraftItems(blockStr, allBlockNames);
         mod.log("Block named: \"" + blockStr + "\" not a valid block. Perhaps the user meant \"" + closest + "\"?" + (blockStr.contains("log") ? " Can try 'log' as well": ""));

         this.finish();
      } else {
         BlockScanner blockScanner = mod.getBlockScanner();
         Optional<BlockPos> p = blockScanner.getNearestBlock(block, EntityVer.getPos(mod.getPlayer()));
         if (p.isPresent()) {
            mod.log("Closest " + blockStr + ": " + p.get().toString());
         } else {
            mod.log("No blocks of type " + blockStr + " found nearby.");
         }

         this.finish();
      }
   }
}
