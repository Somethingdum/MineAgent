// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectWoolTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasks.entity.ShearSheepTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.MiningRequirement;
import com.mineagent.sbe.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import java.util.Arrays;
import java.util.HashSet;

public class CollectWoolTask extends ResourceTask {
   private final int count;
   private final HashSet<DyeColor> colors;
   private final Item[] wools;

   public CollectWoolTask(DyeColor[] colors, int count) {
      super(new ItemTarget(ItemHelper.WOOL, count));
      this.colors = new HashSet<>(Arrays.asList(colors));
      this.count = count;
      this.wools = getWoolColorItems(colors);
   }

   public CollectWoolTask(DyeColor color, int count) {
      this(new DyeColor[]{color}, count);
   }

   public CollectWoolTask(int count) {
      this(DyeColor.values(), count);
   }

   private static Item[] getWoolColorItems(DyeColor[] colors) {
      Item[] result = new Item[colors.length];

      for (int i = 0; i < result.length; i++) {
         result[i] = ItemHelper.getColorfulItems(colors[i]).wool;
      }

      return result;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      Block[] woolBlocks = ItemHelper.itemsToBlocks(this.wools);
      if (mod.getBlockScanner().anyFound(woolBlocks)) {
         return new MineAndCollectTask(new ItemTarget(this.wools), woolBlocks, MiningRequirement.HAND);
      } else if (this.isInWrongDimension(mod) && !mod.getEntityTracker().entityFound(Sheep.class)) {
         return this.getToCorrectDimensionTask(mod);
      } else {
         return (Task)(mod.getItemStorage().hasItem(Items.SHEARS)
            ? new ShearSheepTask()
            : new KillAndLootTask(
               Sheep.class,
               entity -> !(entity instanceof Sheep sheep) ? false : this.colors.contains(sheep.getColor()) && !sheep.isSheared(),
               new ItemTarget(this.wools, this.count)
            ));
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectWoolTask && ((CollectWoolTask)other).count == this.count;
   }

   @Override
   protected String toDebugStringName() {
      return "Collect " + this.count + " wool.";
   }
}
