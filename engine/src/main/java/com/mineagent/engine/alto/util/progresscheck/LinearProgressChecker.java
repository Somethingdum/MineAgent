// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/progresscheck/LinearProgressChecker.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.progresscheck;

import com.mineagent.engine.alto.util.time.TimerGame;

/**
 * Simple progress checker that requires we always make progress.
 */
public class LinearProgressChecker implements IProgressChecker<Double> {

    private final double minProgress;
    private final TimerGame timer;

    private double lastProgress;
    private double currentProgress;

    private boolean first;

    private boolean failed;

    public LinearProgressChecker(double timeout, double minProgress) {
        this.minProgress = minProgress;
        timer = new TimerGame(timeout);
        reset();
    }

    @Override
    public void setProgress(Double progress) {
        currentProgress = progress;
        if (first) {
            lastProgress = progress;
            first = false;
        }
        if (timer.elapsed()) {
            double improvement = progress - lastProgress;
            if (improvement < minProgress) {
                failed = true;
            }
            first = false;
            timer.reset();
            lastProgress = progress;
        }
    }

    @Override
    public boolean failed() {
        return failed;
    }

    public void reset() {
        //_first = true;
        failed = false;
        timer.reset();
        first = true;
    }
}
