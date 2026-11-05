// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/resources/wood/CollectHangingSignTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.resources.wood;

import com.mineagent.engine.alto.TaskCatalogue;
import com.mineagent.engine.alto.tasks.resources.CraftWithMatchingStrippedLogsTask;
import com.mineagent.engine.alto.util.CraftingRecipe;
import com.mineagent.engine.alto.util.ItemTarget;
import com.mineagent.engine.alto.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectHangingSignTask extends CraftWithMatchingStrippedLogsTask {

    public CollectHangingSignTask(Item[] targets, ItemTarget strippedLogs, int count) {
        // Bottom 6 are stripped logs, must be the same.
        super(targets, woodItems -> woodItems.hangingSign, createRecipe(strippedLogs), new boolean[]{false, false, false, true, true, true, true, true, true}, count);
    }

    public CollectHangingSignTask(Item target, String strippedLogCatalogueName, int count) {
        this(new Item[]{target}, new ItemTarget(strippedLogCatalogueName, 1), count);
    }

    public CollectHangingSignTask(int count) {
        this(ItemHelper.WOOD_HANGING_SIGN, TaskCatalogue.getItemTarget("stripped_logs", 1), count);


    }


    private static CraftingRecipe createRecipe(ItemTarget strippedLogs) {
        ItemTarget s = strippedLogs;
        ItemTarget chain = TaskCatalogue.getItemTarget("chain", 1);
        return CraftingRecipe.newShapedRecipe(new ItemTarget[]{chain, null, chain, s, s, s, s, s, s}, 6);
    }
}
