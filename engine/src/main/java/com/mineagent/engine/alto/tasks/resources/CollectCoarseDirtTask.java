// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/resources/CollectCoarseDirtTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.resources;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasks.CraftInInventoryTask;
import com.mineagent.engine.alto.tasks.ResourceTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.*;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectCoarseDirtTask extends ResourceTask {

    private static final float CLOSE_ENOUGH_COARSE_DIRT = 128;
    private final int _count;

    public CollectCoarseDirtTask(int targetCount) {
        super(Items.COARSE_DIRT, targetCount);
        _count = targetCount;
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
        double c = Math.ceil((double) (_count - mod.getItemStorage().getItemCount(Items.COARSE_DIRT)) / 4) * 2; // Minimum number of dirt / gravel needed to complete the recipe, accounting for coarse dirt already collected.
        Optional<BlockPos> closest = mod.getBlockScanner().getNearestBlock(Blocks.COARSE_DIRT);

        // If not enough dirt and gravel for the recipe, and coarse dirt within a certain distance, collect coarse dirt
        if (!(mod.getItemStorage().getItemCount(Items.DIRT) >= c &&
                mod.getItemStorage().getItemCount(Items.GRAVEL) >= c) &&
                closest.isPresent() && closest.get().closerToCenterThan(mod.getPlayer().position(), CLOSE_ENOUGH_COARSE_DIRT)) {
            return new MineAndCollectTask(new ItemTarget(Items.COARSE_DIRT), new Block[]{Blocks.COARSE_DIRT}, MiningRequirement.HAND).forceDimension(Dimension.OVERWORLD);
        } else {
            int target = _count;
            ItemTarget d = new ItemTarget(Items.DIRT, 1);
            ItemTarget g = new ItemTarget(Items.GRAVEL, 1);
            return new CraftInInventoryTask(new RecipeTarget(Items.COARSE_DIRT, target, CraftingRecipe.newShapedRecipe("coarse_dirt", new ItemTarget[]{d, g, g, d}, 4)));
        }
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectCoarseDirtTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + _count + " Coarse Dirt.";
    }
}
