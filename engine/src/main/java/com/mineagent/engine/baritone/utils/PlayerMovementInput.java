// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/main/java/baritone/utils/PlayerMovementInput.java
// at commit 11ea66c (branch 1.21.1). SMH: the impulses Baritone's movement input works out from the keys it forces, put
// on the companion's body by its input driver (a client's player reads them from its Input each tick; a sneaking
// client moves at 0.3 of them, as here).
package com.mineagent.engine.baritone.utils;

import com.mineagent.api.entity.InputDriver;
import com.mineagent.engine.baritone.api.utils.input.Input;

public class PlayerMovementInput {

    private final InputOverrideHandler handler;
    public float leftImpulse, forwardImpulse;
    public boolean jumping, shiftKeyDown, up, down, left, right;

    PlayerMovementInput(InputOverrideHandler handler) {
        this.handler = handler;
    }

    public void tick() {
        this.leftImpulse = 0.0F;
        this.forwardImpulse = 0.0F;
        this.jumping = handler.isInputForcedDown(Input.JUMP); // oppa gangnam style
        if (this.up = handler.isInputForcedDown(Input.MOVE_FORWARD)) {
            this.forwardImpulse++;
        }
        if (this.down = handler.isInputForcedDown(Input.MOVE_BACK)) {
            this.forwardImpulse--;
        }
        if (this.left = handler.isInputForcedDown(Input.MOVE_LEFT)) {
            this.leftImpulse++;
        }
        if (this.right = handler.isInputForcedDown(Input.MOVE_RIGHT)) {
            this.leftImpulse--;
        }
        if (this.shiftKeyDown = handler.isInputForcedDown(Input.SNEAK)) {
            this.leftImpulse *= 0.3D;
            this.forwardImpulse *= 0.3D;
        }
    }

    /** SMH: onto the body. */
    public void applyTo(InputDriver driver) {
        driver.setForward(forwardImpulse);
        driver.setStrafe(leftImpulse);
        driver.setJumping(jumping);
        driver.setSneaking(shiftKeyDown);
        driver.setSprinting(handler.isInputForcedDown(Input.SPRINT) && forwardImpulse > 0.8F);
    }
}
