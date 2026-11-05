// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/squashed/TypeSquasher.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.squashed;

import com.mineagent.sbe.altoclef.tasks.ResourceTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class TypeSquasher<T extends ResourceTask> {
   private final List<T> tasks = new ArrayList<>();

   void add(T task) {
      this.tasks.add(task);
   }

   public List<ResourceTask> getSquashed() {
      return this.tasks.isEmpty() ? Collections.emptyList() : this.getSquashed(this.tasks);
   }

   protected abstract List<ResourceTask> getSquashed(List<T> var1);
}
