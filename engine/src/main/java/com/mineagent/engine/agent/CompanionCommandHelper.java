// SMH plan 70, one agent: the ported SecondBrainEngine moves a body by commands (ICommandHelper: move, jump, sneak, attack,
// use, drop, look); SecondBrainEngine does it with Carpet's "/player <name> ..." (its CarpetPlayerCommandHelper), the
// companion's body with its own input driver, the same one every other part of the mod moves it with. Each command as
// Carpet's action pack does it for a fake player (fabric-carpet, MIT: EntityPlayerActionPack: move sets the walk keys
// until stopped, jump once, attack and use one click). Written for this mod.
package com.mineagent.engine.agent;

import com.mineagent.api.entity.InputDriver;
import com.mineagent.sbe.automatone.api.utils.ICommandHelper;
import com.mineagent.sbe.automatone.api.utils.MoveDirection;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionCommandHelper implements ICommandHelper {

    private final ServerPlayer player;
    private final InputDriver driver;
    private float forward, strafe;

    public CompanionCommandHelper(ServerPlayer player, InputDriver driver) {
        this.player = player;
        this.driver = driver;
    }

    @Override
    public void executeMove(MoveDirection direction) {
        com.mineagent.engine.entity.KeyWriters.as("automatone", () -> executeMoveNow(direction));
    }

    private void executeMoveNow(MoveDirection direction) {
        switch (direction) {
            case FORWARD -> forward = 1f;
            case BACKWARD -> forward = -1f;
            case LEFT -> strafe = 1f;
            case RIGHT -> strafe = -1f;
            default -> { }
        }
        driver.setForward(forward);
        driver.setStrafe(strafe);
    }

    @Override
    public void executeMoveStop() {
        com.mineagent.engine.entity.KeyWriters.as("automatone", () -> executeMoveStopNow());
    }

    private void executeMoveStopNow() {
        forward = 0f;
        strafe = 0f;
        driver.setForward(0f);
        driver.setStrafe(0f);
        driver.setJumping(false);
    }

    @Override
    public void executeSpawn(String username) {
        // The companion's body is spawned by the mod, not by a command.
    }

    @Override
    public void executeLook(Direction direction) {
        player.setYRot(direction.toYRot());
        player.setYHeadRot(direction.toYRot());
    }

    @Override
    public void executeJump() {
        com.mineagent.engine.entity.KeyWriters.as("automatone", () -> executeJumpNow());
    }

    private void executeJumpNow() {
        driver.setJumping(true);
    }

    @Override
    public void executeAttack() {
        com.mineagent.engine.entity.KeyWriters.as("automatone", () -> executeAttackNow());
    }

    private void executeAttackNow() {
        driver.leftClick();
    }

    @Override
    public void executeDrop(int slot, boolean dropAll) {
        var inv = player.getInventory();
        int s = slot < 0 ? inv.selected : slot;
        var stack = inv.getItem(s);
        if (stack.isEmpty()) return;
        var out = inv.removeItem(s, dropAll ? stack.getCount() : 1);
        player.drop(out, false, true);
    }

    @Override
    public void executeUse() {
        com.mineagent.engine.entity.KeyWriters.as("automatone", () -> executeUseNow());
    }

    private void executeUseNow() {
        driver.rightClick();
    }

    @Override
    public void executeSneak() {
        com.mineagent.engine.entity.KeyWriters.as("automatone", () -> executeSneakNow());
    }

    private void executeSneakNow() {
        driver.setSneaking(true);
    }

    @Override
    public void executeUnSneak() {
        com.mineagent.engine.entity.KeyWriters.as("automatone", () -> executeUnSneakNow());
    }

    private void executeUnSneakNow() {
        driver.setSneaking(false);
    }

    @Override
    public void executeStopAll() {
        com.mineagent.engine.entity.KeyWriters.as("automatone", () -> executeStopAllNow());
    }

    private void executeStopAllNow() {
        forward = 0f;
        strafe = 0f;
        driver.clear();
    }
}
