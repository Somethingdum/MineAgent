// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasksystem/ITaskRequiresGrounded.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasksystem;

import com.mineagent.engine.alto.AltoClef;
import net.minecraft.server.level.ServerPlayer;

/**
 * Some tasks may mess up royally if we interrupt them while mid air.
 * For instance, if we're doing some parkour and a baritone task is stopped,
 * the player will fall to whatever is below them, perhaps their death.
 */
public interface ITaskRequiresGrounded extends ITaskCanForce {
    @Override
    default boolean shouldForce(Task interruptingCandidate) {
        if (interruptingCandidate instanceof ITaskOverridesGrounded)
            return false;

        ServerPlayer player = AltoClef.getInstance().getPlayer();
        return !(player.onGround() || player.isSwimming() || player.isInWater() || player.onClimbable());
    }
}
