// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/squashed/SmithingSquasher.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.squashed;

import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasks.container.UpgradeInSmithingTableTask;
import com.mineagent.sbe.altoclef.util.ItemTarget;

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
