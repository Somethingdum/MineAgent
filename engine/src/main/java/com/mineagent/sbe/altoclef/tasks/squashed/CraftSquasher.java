// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/squashed/CraftSquasher.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.squashed;

import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasks.container.CraftInTableTask;
import com.mineagent.sbe.altoclef.util.RecipeTarget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CraftSquasher extends TypeSquasher<CraftInTableTask> {
   @Override
   protected List<ResourceTask> getSquashed(List<CraftInTableTask> tasks) {
      List<RecipeTarget> targetRecipies = new ArrayList<>();

      for (CraftInTableTask task : tasks) {
         targetRecipies.addAll(Arrays.asList(task.getRecipeTargets()));
      }

      return Collections.singletonList(new CraftInTableTask(targetRecipies.toArray(RecipeTarget[]::new)));
   }
}
