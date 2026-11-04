package com.mineagent.neoforge.mixin;

import com.mineagent.engine.MineAgentEngine;
import com.mineagent.engine.survival.Manners;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
        mineagent$countBefore = -1;
        try {
            if (player instanceof ServerPlayer sp && MineAgentEngine.isCompanionPlayer(sp.getUUID())) {
                ItemEntity self = (ItemEntity) (Object) this;
                if (!Manners.mayTake(self, sp)) {
                    ci.cancel();
                    return;
                }
                // How many it held, and who threw it: so what it picked up is known after.
                if (self.getOwner() instanceof Player thrower) {
                    mineagent$countBefore = self.getItem().getCount();
                    mineagent$thrower = thrower.getUUID();
                }
            }
        } catch (RuntimeException ignored) {
            // Manners must never get in the way of the game.
        }
    }

    @Unique private int mineagent$countBefore = -1;
    @Unique private java.util.UUID mineagent$thrower;

    /** What a companion picked up of what a player threw down: given back, if that player owes it (SMH plan 70). */
    @Inject(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At("TAIL"))
    private void mineagent$notePickedUp(Player player, CallbackInfo ci) {
        try {
            int before = mineagent$countBefore;
            mineagent$countBefore = -1;
            if (before <= 0 || mineagent$thrower == null || !(player instanceof ServerPlayer sp)) return;
            ItemEntity self = (ItemEntity) (Object) this;
            int picked = self.isRemoved() ? before : before - self.getItem().getCount();
            if (picked > 0) {
                MineAgentEngine.onCompanionPickedUp(sp, mineagent$thrower,
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(self.getItem().getItem()).toString(), picked);
            }
        } catch (RuntimeException ignored) {
            // a courtesy
        }
    }
}
