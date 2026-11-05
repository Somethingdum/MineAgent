// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/speedrun/beatgame/prioritytask/prioritycalculators/ItemPriorityCalculator.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.prioritycalculators;

public abstract class ItemPriorityCalculator {

    public final int minCount;
    public final int maxCount;

    protected boolean minCountSatisfied = false;
    protected boolean maxCountSatisfied = false;

    public ItemPriorityCalculator(int minCount, int maxCount) {
        this.minCount = minCount;
        this.maxCount = maxCount;
    }

    public final double getPriority(int count) {
        if (count > minCount) {
            minCountSatisfied = true;
        }
        if (count > maxCount) {
            maxCountSatisfied = true;
        }

        if (minCountSatisfied) {
            count = Math.max(minCount, count);
        }

        if (maxCountSatisfied) return Double.NEGATIVE_INFINITY;

        return calculatePriority(count);
    }

    abstract double calculatePriority(int count);


}
