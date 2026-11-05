package me.sailex.altoclef.commands.random;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commands.BlockScanner;
import me.sailex.altoclef.commandsystem.Arg;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.commandsystem.CommandException;
import me.sailex.altoclef.util.helpers.FuzzySearchHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import me.sailex.altoclef.multiversion.EntityVer;
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
