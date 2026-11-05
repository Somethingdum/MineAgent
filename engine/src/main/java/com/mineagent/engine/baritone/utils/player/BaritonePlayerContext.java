// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/main/java/baritone/utils/player/BaritonePlayerContext.java
// at commit 11ea66c (branch 1.21.1). SMH: the companion's server-side body in place of the client's player (its body is
// set again when it respawns); no world data cache; what it looks at is traced from where it faces, as a client's is.
package com.mineagent.engine.baritone.utils.player;

import com.mineagent.engine.baritone.Baritone;
import com.mineagent.engine.baritone.api.utils.*;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.HitResult;

public final class BaritonePlayerContext implements IPlayerContext {

    private final Baritone baritone;
    private ServerPlayer player;
    private final BaritonePlayerController playerController;

    public BaritonePlayerContext(Baritone baritone, ServerPlayer player) {
        this.baritone = baritone;
        this.player = player;
        this.playerController = new BaritonePlayerController(this);
    }

    /** SMH: the companion's new body after a respawn. */
    public void setPlayer(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public ServerPlayer player() {
        return this.player;
    }

    @Override
    public IPlayerController playerController() {
        return this.playerController;
    }

    @Override
    public ServerLevel world() {
        return this.player.serverLevel();
    }

    @Override
    public BetterBlockPos viewerPos() {
        return this.playerFeet();
    }

    @Override
    public Rotation playerRotations() {
        return this.baritone.getLookBehavior().getEffectiveRotation().orElseGet(IPlayerContext.super::playerRotations);
    }

    @Override
    public HitResult objectMouseOver() {
        return RayTraceUtils.rayTraceTowards(player(), playerRotations(), playerController().getBlockReachDistance());
    }
}
