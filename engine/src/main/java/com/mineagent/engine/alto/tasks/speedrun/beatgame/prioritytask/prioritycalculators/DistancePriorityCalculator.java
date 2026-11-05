// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/speedrun/beatgame/prioritytask/prioritycalculators/DistancePriorityCalculator.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.prioritycalculators;

public abstract class DistancePriorityCalculator {

    public final int minCount;
    public final int maxCount;

    protected boolean minCountSatisfied = false;
    protected boolean maxCountSatisfied = false;

    public DistancePriorityCalculator(int minCount, int maxCount) {
        this.minCount = minCount;
        this.maxCount = maxCount;
    }

    public void update(int count) {
        if (count >= minCount) {
            minCountSatisfied = true;
        }
        if (count >= maxCount) {
            maxCountSatisfied = true;
        }
    }


    public double getPriority(double distance) {
        if (Double.isInfinite(distance) || distance == Integer.MAX_VALUE || maxCountSatisfied) return Double.NEGATIVE_INFINITY;

        return calculatePriority(distance);
    }

    abstract double calculatePriority(double distance);

}
