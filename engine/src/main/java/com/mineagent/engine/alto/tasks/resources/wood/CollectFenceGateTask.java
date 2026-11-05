// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/resources/wood/CollectFenceGateTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.resources.wood;

import com.mineagent.engine.alto.TaskCatalogue;
import com.mineagent.engine.alto.tasks.resources.CraftWithMatchingPlanksTask;
import com.mineagent.engine.alto.util.CraftingRecipe;
import com.mineagent.engine.alto.util.ItemTarget;
import com.mineagent.engine.alto.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectFenceGateTask extends CraftWithMatchingPlanksTask {

    public CollectFenceGateTask(Item[] targets, ItemTarget planks, int count) {
        super(targets, woodItems -> woodItems.fenceGate, createRecipe(planks), new boolean[]{false, true, false, false, true, false, false, false, false}, count);
    }

    public CollectFenceGateTask(Item target, String plankCatalogueName, int count) {
        this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
    }

    public CollectFenceGateTask(int count) {
        this(ItemHelper.WOOD_FENCE_GATE, TaskCatalogue.getItemTarget("planks", 1), count);
    }

    private static CraftingRecipe createRecipe(ItemTarget planks) {
        ItemTarget p = planks;
        ItemTarget s = TaskCatalogue.getItemTarget("stick", 1);
        return CraftingRecipe.newShapedRecipe(new ItemTarget[]{s, p, s, s, p, s, null, null, null}, 1);
    }
}
