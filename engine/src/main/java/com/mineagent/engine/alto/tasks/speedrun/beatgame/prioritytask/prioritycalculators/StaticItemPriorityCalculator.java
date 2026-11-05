// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/speedrun/beatgame/prioritytask/prioritycalculators/StaticItemPriorityCalculator.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.prioritycalculators;

public class StaticItemPriorityCalculator extends ItemPriorityCalculator{

    public static StaticItemPriorityCalculator of(int priority) {
        return new StaticItemPriorityCalculator(priority,1,1);
    }

    private final int priority;

    public StaticItemPriorityCalculator(int priority, int minCount, int maxCount) {
        super(minCount, maxCount);
        this.priority = priority;
    }

    @Override
    double calculatePriority(int count) {
        return priority;
    }
}
