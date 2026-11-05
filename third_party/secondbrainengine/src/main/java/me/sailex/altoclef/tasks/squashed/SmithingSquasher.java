package me.sailex.altoclef.tasks.squashed;

import me.sailex.altoclef.tasks.ResourceTask;
import me.sailex.altoclef.tasks.container.UpgradeInSmithingTableTask;
import me.sailex.altoclef.util.ItemTarget;

import java.util.ArrayList;
import java.util.List;

public class SmithingSquasher extends TypeSquasher<UpgradeInSmithingTableTask> {
   @Override
   protected List<ResourceTask> getSquashed(List<UpgradeInSmithingTableTask> tasks) {
      if (tasks.isEmpty()) {
         return new ArrayList<>();
      } else {
         List<ResourceTask> result = new ArrayList<>();
         List<ItemTarget> materialsToCollect = new ArrayList<>();

         for (UpgradeInSmithingTableTask task : tasks) {
            materialsToCollect.add(task.getMaterials());
            materialsToCollect.add(task.getTools());
            materialsToCollect.add(task.getTemplate());
         }

         if (!materialsToCollect.isEmpty()) {
            result.add(new CataloguedResourceTask(materialsToCollect.toArray(new ItemTarget[0])));
         }

         result.addAll(tasks);
         return result;
      }
   }
}
