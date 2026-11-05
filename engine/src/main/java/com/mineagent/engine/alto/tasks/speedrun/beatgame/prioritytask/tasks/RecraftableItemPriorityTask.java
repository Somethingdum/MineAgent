// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/speedrun/beatgame/prioritytask/tasks/RecraftableItemPriorityTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.tasks;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.util.RecipeTarget;

import java.util.function.Function;

public class RecraftableItemPriorityTask extends CraftItemPriorityTask{


    private final double recraftPriority;

    public RecraftableItemPriorityTask(double priority, double recraftPriority, RecipeTarget toCraft, Function<AltoClef, Boolean> canCall ) {
        super(priority, toCraft, canCall);
        this.recraftPriority = recraftPriority;
    }


    @Override
    protected double getPriority(AltoClef mod) {
        if (isSatisfied()) return recraftPriority;

        return super.getPriority(mod);
    }
}
