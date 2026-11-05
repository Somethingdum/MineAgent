// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/resources/CollectBlockByOneTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.resources;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.multiversion.versionedfields.Blocks;
import com.mineagent.engine.alto.multiversion.versionedfields.Items;
import com.mineagent.engine.alto.tasks.ResourceTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.MiningRequirement;
import java.util.Arrays;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class CollectBlockByOneTask extends ResourceTask {

    private final Item item;
    private final Block[] blocks;
    private final MiningRequirement requirement;
    private final int count;

    public CollectBlockByOneTask(Item item, Block[] blocks, MiningRequirement requirement, int targetCount) {
        super(item, targetCount);
        this.item = item;
        this.blocks = blocks;
        this.requirement = requirement;
        count = targetCount;
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {

    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        return new MineAndCollectTask(item, 1, blocks, requirement);
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        if (other instanceof CollectBlockByOneTask task) {
            return task.count == count && task.item.equals(item) && Arrays.stream(task.blocks).allMatch(block -> Arrays.stream(blocks).toList().contains(block));
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Collect "+item;
    }


    public static class CollectCobblestoneTask extends CollectBlockByOneTask {

        public CollectCobblestoneTask(int targetCount) {
            super(Items.COBBLESTONE, new Block[]{Blocks.STONE, Blocks.COBBLESTONE}, MiningRequirement.WOOD, targetCount);
        }
    }

    public static class CollectCobbledDeepslateTask extends CollectBlockByOneTask {

        public CollectCobbledDeepslateTask(int targetCount) {
            super(Items.COBBLED_DEEPSLATE, new Block[]{Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE}, MiningRequirement.WOOD, targetCount);
        }
    }

    public static class CollectEndStoneTask extends CollectBlockByOneTask {

        public CollectEndStoneTask(int targetCount) {
            super(Items.END_STONE, new Block[]{Blocks.END_STONE}, MiningRequirement.WOOD, targetCount);
        }
    }

}
