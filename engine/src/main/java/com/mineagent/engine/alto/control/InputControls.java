// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/control/InputControls.java
// at commit 1f8e038, in Mojang's names (third_party/altoclef). SMH: the companion has no keyboard, so its keys are held
// here, as AltoClef holds and presses them (a press lasts one tick and needs a release before the next), and applied to
// its body each tick by apply(), together with what Baritone's input override forces.
package com.mineagent.engine.alto.control;

import com.mineagent.engine.alto.baritone.api.utils.input.Input;
import com.mineagent.engine.entity.CompanionInputDriver;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Sometimes we want to trigger a "press" for one frame, or do other input forcing.
 * <p>
 * Dealing with keeping track of a press and timing each time you do this is annoying.
 * <p>
 * But this will alleviate all confusion.
 */
public class InputControls {

    private final Queue<Input> toUnpress = new ArrayDeque<>();
    private final Set<Input> _waitForRelease = new HashSet<>(); // a click requires a release.
    // SMH: the keys as the game's key mappings would hold them.
    private final Set<Input> down = EnumSet.noneOf(Input.class);
    // SMH: keys pressed since the last apply (a click registers even when released in the same tick).
    private final Set<Input> clicked = EnumSet.noneOf(Input.class);
    // SMH: what the body had last tick, to send a release when a key comes up.
    private final Set<Input> applied = EnumSet.noneOf(Input.class);
    private final ServerPlayer player;

    public InputControls(ServerPlayer player) {
        this.player = player;
    }

    public void tryPress(Input input) {
        // We just pressed, so let us release.
        if (_waitForRelease.contains(input)) {
            return;
        }
        down.add(input);
        // Also necessary to ensure the game registers the input as "pressed"
        clicked.add(input);
        toUnpress.add(input);
        _waitForRelease.add(input);
    }

    public void hold(Input input) {
        if (!down.contains(input)) {
            clicked.add(input);
        }
        down.add(input);
    }

    public void release(Input input) {
        down.remove(input);
    }

    public boolean isHeldDown(Input input) {
        return down.contains(input);
    }

    public void forceLook(float yaw, float pitch) {
        if (player != null) {
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.setYHeadRot(yaw);
        }
    }

    // Before the user calls input commands for the frame
    public void onTickPre() {
        while (!toUnpress.isEmpty()) {
            down.remove(toUnpress.remove());
        }
    }

    // After the user calls input commands for the frame
    public void onTickPost() {
        _waitForRelease.clear();
    }

    /**
     * SMH: the held keys (and those Baritone's input override forces) put on the body, once a tick, after its walk has
     * had its turn. Movement keys only when no walk is running (a walk drives the body itself, as Baritone's own input
     * would); clicks always: a held left click breaks what it looks at, a right click uses or places once per press, and
     * letting go of the right click stops using what it holds (a bow drawn, food half eaten).
     */
    public void apply(CompanionInputDriver driver, Set<Input> forced, boolean walking) {
        Set<Input> now = EnumSet.noneOf(Input.class);
        now.addAll(down);
        now.addAll(forced);
        if (!walking) {
            float forward = (now.contains(Input.MOVE_FORWARD) ? 1f : 0f) - (now.contains(Input.MOVE_BACK) ? 1f : 0f);
            float strafe = (now.contains(Input.MOVE_LEFT) ? 1f : 0f) - (now.contains(Input.MOVE_RIGHT) ? 1f : 0f);
            boolean anyMove = forward != 0 || strafe != 0 || now.contains(Input.JUMP) || now.contains(Input.SNEAK)
                    || applied.contains(Input.MOVE_FORWARD) || applied.contains(Input.MOVE_BACK)
                    || applied.contains(Input.MOVE_LEFT) || applied.contains(Input.MOVE_RIGHT)
                    || applied.contains(Input.JUMP) || applied.contains(Input.SNEAK);
            if (anyMove) {
                driver.setForward(forward);
                driver.setStrafe(strafe);
                driver.setJumping(now.contains(Input.JUMP));
                driver.setSneaking(now.contains(Input.SNEAK));
                driver.setSprinting(now.contains(Input.SPRINT) && forward > 0);
            }
        }
        if (now.contains(Input.CLICK_LEFT) || clicked.contains(Input.CLICK_LEFT)) {
            driver.leftClick();
        } else if (applied.contains(Input.CLICK_LEFT)) {
            driver.releaseLeftClick();
        }
        boolean rightNew = clicked.contains(Input.CLICK_RIGHT)
                || (now.contains(Input.CLICK_RIGHT) && !applied.contains(Input.CLICK_RIGHT));
        if (rightNew) {
            driver.rightClick();
        } else if (!now.contains(Input.CLICK_RIGHT) && applied.contains(Input.CLICK_RIGHT) && player.isUsingItem()) {
            player.releaseUsingItem();
        }
        applied.clear();
        applied.addAll(now);
        clicked.clear();
    }

    /** SMH: every key let go (the task tree stopped or was interrupted). */
    public void releaseAll(CompanionInputDriver driver) {
        boolean wasBreaking = applied.contains(Input.CLICK_LEFT) || down.contains(Input.CLICK_LEFT);
        down.clear();
        clicked.clear();
        toUnpress.clear();
        _waitForRelease.clear();
        if (driver != null) {
            if (wasBreaking) driver.releaseLeftClick();
            if (!applied.isEmpty()) driver.clear();
        }
        if (applied.contains(Input.CLICK_RIGHT) && player.isUsingItem()) player.releaseUsingItem();
        applied.clear();
    }
}
