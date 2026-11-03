package com.mineagent.fabric.mixin;

import com.mineagent.engine.MineAgentEngine;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Saves the companions when the game saves its players (SMH plan 70, MA5.9): every autosave and at shutdown. So the
 * companions and the world are always saved at the same moment, and after a crash both come back from it.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "saveAll", at = @At("HEAD"))
    private void mineagent$saveCompanionsWithTheWorld(CallbackInfo ci) {
        try {
            MineAgentEngine.persistAllCompanions();
        } catch (RuntimeException ignored) {
            // Saving companions must never stop the world from saving.
        }
    }
}
