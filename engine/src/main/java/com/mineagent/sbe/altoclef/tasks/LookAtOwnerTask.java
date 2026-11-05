// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/LookAtOwnerTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks;

import com.mineagent.sbe.altoclef.Playground;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.helpers.LookHelper;

public class LookAtOwnerTask extends Task {
    public static String name = "LookAtOwnerTask";
    @Override
    protected void onStart() {
        
    }

    @Override
    protected Task onTick() {
        if (this.controller.getOwner() != null) {
            LookHelper.lookAt(this.controller, this.controller.getOwner().getEyePosition());
        } else {
            this.controller.getClosestPlayer().ifPresent(
                    (player) -> {
                        LookHelper.lookAt(controller, player.getEyePosition());
                    });
        }
        Playground.IDLE_TEST_TICK_FUNCTION(this.controller);
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof LookAtOwnerTask;
    }

    @Override
    protected String toDebugString() {
        return "LookAtOwnerTask";
    }
}
