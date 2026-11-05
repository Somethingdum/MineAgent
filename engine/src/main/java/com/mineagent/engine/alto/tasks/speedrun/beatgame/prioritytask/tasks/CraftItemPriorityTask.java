// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/speedrun/beatgame/prioritytask/tasks/CraftItemPriorityTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.tasks;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.Debug;
import com.mineagent.engine.alto.tasks.CraftInInventoryTask;
import com.mineagent.engine.alto.tasks.container.CraftInTableTask;
import com.mineagent.engine.alto.tasks.speedrun.beatgame.BeatMinecraftTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.RecipeTarget;
import com.mineagent.engine.alto.util.helpers.CraftingHelper;

import java.util.function.Function;

/**
 crafts an item with constant priority...
 */
public class CraftItemPriorityTask extends PriorityTask{

    public final double priority;
    public final RecipeTarget recipeTarget;
    private boolean satisfied = false;

    // note: bypassForceCooldown is set to true, because we usually don't want to wait when crafting
    public CraftItemPriorityTask(double priority, RecipeTarget toCraft) {
        this(priority, toCraft, mod -> true);
    }

    public CraftItemPriorityTask(double priority, RecipeTarget toCraft, Function<AltoClef, Boolean> canCall) {
        this(priority, toCraft, canCall, false, true, true);
    }

    public CraftItemPriorityTask(double priority, RecipeTarget toCraft, boolean shouldForce, boolean canCache, boolean bypassForceCooldown) {
        this(priority, toCraft, mod -> true, shouldForce, canCache, bypassForceCooldown);
    }

    public CraftItemPriorityTask(double priority, RecipeTarget toCraft, Function<AltoClef, Boolean> canCall, boolean shouldForce, boolean canCache, boolean bypassForceCooldown) {
        super(canCall, shouldForce, canCache, bypassForceCooldown);
        this.priority = priority;
        this.recipeTarget = toCraft;
    }

    @Override
    public Task getTask(AltoClef mod) {
        if (recipeTarget.getRecipe().isBig()) {
            return new CraftInTableTask(recipeTarget);
        }

        return new CraftInInventoryTask(recipeTarget);
    }

    @Override
    public String getDebugString() {
        return "Crafting "+recipeTarget;
    }

    @Override
    protected double getPriority(AltoClef mod) {
        if (BeatMinecraftTask.hasItem(mod, recipeTarget.getOutputItem())) {
            Debug.logInternal("THIS IS SATISFIED "+recipeTarget.getOutputItem());
            satisfied = true;
        }
        Debug.logInternal("NOT SATISFIED");

        if (satisfied) return Double.NEGATIVE_INFINITY;

        return priority;
    }


    @Override
    public boolean needCraftingOnStart(AltoClef mod) {
        return CraftingHelper.canCraftItemNow(mod, recipeTarget.getOutputItem());
    }

    public boolean isSatisfied() {
        return satisfied;
    }
}
