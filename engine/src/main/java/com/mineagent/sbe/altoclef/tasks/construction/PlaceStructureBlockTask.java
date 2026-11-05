// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/construction/PlaceStructureBlockTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public class PlaceStructureBlockTask extends PlaceBlockTask {
   public PlaceStructureBlockTask(BlockPos target) {
      super(target, new Block[0], true, true);
   }
}
