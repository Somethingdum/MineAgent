// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md):
// adris/altoclef/chains/MLGBucketFallChain.java at commit 1f8e038, in Mojang's names (third_party/altoclef). SMH: what
// AltoClef's tasks ask of it; the bucket itself is the companion's own fall chain (survival/MLGChain).
package com.mineagent.engine.alto.chains;

import com.mineagent.engine.alto.AltoClef;

public class MLGBucketFallChain {

    private final AltoClef host;

    public MLGBucketFallChain(AltoClef host) {
        this.host = host;
    }

    /** SMH: no catch is under way when our fall chain doesn't have the body. */
    public boolean doneMLG() {
        return !host.companionCatchingFall();
    }

    public boolean isChorusFruiting() {
        return false;
    }

    public boolean isFalling(AltoClef mod) {
        if (!mod.getModSettings().shouldAutoMLGBucket()) {
            return false;
        }
        if (mod.getPlayer().isSwimming() || mod.getPlayer().isInWater() || mod.getPlayer().onGround() || mod.getPlayer().onClimbable()) {
            // We're grounded.
            return false;
        }
        double ySpeed = mod.getPlayer().getDeltaMovement().y;
        return ySpeed < -0.7;
    }
}
