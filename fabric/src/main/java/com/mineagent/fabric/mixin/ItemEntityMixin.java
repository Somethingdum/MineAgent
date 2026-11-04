package com.mineagent.fabric.mixin;

import com.mineagent.engine.MineAgentEngine;
import com.mineagent.engine.survival.Manners;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Manners (SMH plan 70, MA10.5): a companion walking over something a player threw down leaves it there, unless they
 * handed it over or asked it to pick things up ({@link Manners#mayTake}). Only companions; players pick up as ever.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
    private void mineagent$leaveWhatPlayersThrew(Player player, CallbackInfo ci) {
        try {
            if (player instanceof ServerPlayer sp && MineAgentEngine.isCompanionPlayer(sp.getUUID())
                    && !Manners.mayTake((ItemEntity) (Object) this, sp)) {
                ci.cancel();
            }
        } catch (RuntimeException ignored) {
            // Manners must never get in the way of the game.
        }
    }
}
