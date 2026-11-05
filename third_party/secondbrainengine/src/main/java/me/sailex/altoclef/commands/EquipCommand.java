package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.Arg;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.commandsystem.CommandException;
import me.sailex.altoclef.commandsystem.ItemList;
import me.sailex.altoclef.multiversion.item.ToolItemVer;
import me.sailex.altoclef.tasks.misc.EquipArmorTask;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class EquipCommand extends Command {
   public EquipCommand() throws CommandException {
      super("equip", "Equips items. Example; `equip iron_chestplate` equips an iron chestplate.", new Arg<>(ItemList.class, "[equippable_items]"));
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      ItemTarget[] items;
      if (parser.getArgUnits().length == 1) {
         String var4 = parser.getArgUnits()[0].toLowerCase();
         switch (var4) {
            case "leather":
               items = ItemTarget.of(ItemHelper.LEATHER_ARMORS);
               break;
            case "iron":
               items = ItemTarget.of(ItemHelper.IRON_ARMORS);
               break;
            case "gold":
               items = ItemTarget.of(ItemHelper.GOLDEN_ARMORS);
               break;
            case "diamond":
               items = ItemTarget.of(ItemHelper.DIAMOND_ARMORS);
               break;
            case "netherite":
               items = ItemTarget.of(ItemHelper.NETHERITE_ARMORS);
               break;
            default:
               items = parser.get(ItemList.class).items;
         }
      } else {
         items = parser.get(ItemList.class).items;
      }

      for (ItemTarget target : items) {
         for (Item item : target.getMatches()) {
            if (!(ToolItemVer.isArmorItem(item))) {
               throw new CommandException("'" + item.toString().toUpperCase() + "' cannot be equipped!");
            }
         }
      }

      mod.runUserTask(new EquipArmorTask(items), () -> this.finish());
   }
}
