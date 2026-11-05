package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.TaskCatalogue;
import me.sailex.altoclef.commandsystem.Arg;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.commandsystem.CommandException;
import me.sailex.altoclef.commandsystem.ItemList;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.ItemTarget;

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
