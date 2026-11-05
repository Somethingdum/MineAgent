// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/ShearAndCollectBlockTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.BotBehaviour;
import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.MiningRequirement;
import com.mineagent.sbe.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public class ShearAndCollectBlockTask extends MineAndCollectTask {
   public ShearAndCollectBlockTask(ItemTarget[] itemTargets, Block... blocksToMine) {
      super(itemTargets, blocksToMine, MiningRequirement.HAND);
   }

   public ShearAndCollectBlockTask(Item[] items, int count, Block... blocksToMine) {
      this(new ItemTarget[]{new ItemTarget(items, count)}, blocksToMine);
   }

   public ShearAndCollectBlockTask(Item item, int count, Block... blocksToMine) {
      this(new Item[]{item}, count, blocksToMine);
   }

   @Override
   protected void onStart() {
      BotBehaviour botBehaviour = this.controller.getBehaviour();
      botBehaviour.push();
      botBehaviour.forceUseTool((blockState, itemStack) -> itemStack.getItem() == Items.SHEARS && ItemHelper.areShearsEffective(blockState.getBlock()));
      super.onStart();
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBehaviour().pop();
      super.onStop(interruptTask);
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      return (Task)(!mod.getItemStorage().hasItem(Items.SHEARS) ? TaskCatalogue.getItemTask(Items.SHEARS, 1) : super.onResourceTick(mod));
   }
}
