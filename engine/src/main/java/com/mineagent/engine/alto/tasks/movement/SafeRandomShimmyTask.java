// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/movement/SafeRandomShimmyTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.movement;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.Debug;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.helpers.LookHelper;
import com.mineagent.engine.alto.util.time.TimerGame;
import com.mineagent.sbe.automatone.Baritone;
import com.mineagent.sbe.automatone.api.utils.input.Input;

/**
 * Will move around randomly while holding shift
 * Used to escape weird situations where baritone doesn't work.
 */
public class SafeRandomShimmyTask extends Task {

    private final TimerGame _lookTimer;

    public SafeRandomShimmyTask(float randomLookInterval) {
        _lookTimer = new TimerGame(randomLookInterval);
    }

    public SafeRandomShimmyTask() {
        this(5);
    }

    @Override
    protected void onStart() {
        _lookTimer.reset();
    }

    @Override
    protected Task onTick() {

        if (_lookTimer.elapsed()) {
            Debug.logMessage("Random Orientation");
            _lookTimer.reset();
            LookHelper.randomOrientation();
        }

        Baritone baritone = AltoClef.getInstance().getClientBaritone();

        baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {
        Baritone baritone = AltoClef.getInstance().getClientBaritone();

        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SafeRandomShimmyTask;
    }

    @Override
    protected String toDebugString() {
        return "Shimmying";
    }
}
