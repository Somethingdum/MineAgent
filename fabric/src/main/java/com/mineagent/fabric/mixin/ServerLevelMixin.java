package com.mineagent.fabric.mixin;

import com.mineagent.engine.world.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers the blocks real players place (SMH plan 70, MA5.2). The game announces every block a player places with a
 * {@code block_place} game event, and every block destroyed with {@code block_destroy}; this listens to both, on the
 * server, and keeps {@link PlacedBlocks} up to date. It changes nothing about the events themselves.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
            at = @At("HEAD"))
    private void mineagent$rememberPlacedBlocks(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context,
                                                 CallbackInfo ci) {
        try {
            ServerLevel level = (ServerLevel) (Object) this;
            if (event == GameEvent.BLOCK_PLACE) {
                if (context.sourceEntity() instanceof Player player) {
                    PlacedBlocks.placedBy(player, level, BlockPos.containing(pos), context.affectedState());
                    // What its owner is building, for a companion taking the other half of the job (MA10.4).
                    com.mineagent.engine.MineAgentEngine.onPlayerBlock(player, level, BlockPos.containing(pos), context.affectedState(), true);
                }
            } else if (event == GameEvent.BLOCK_DESTROY) {
                if (context.sourceEntity() instanceof Player player) {
                    com.mineagent.engine.MineAgentEngine.onPlayerBlock(player, level, BlockPos.containing(pos), context.affectedState(), false);
                }
                PlacedBlocks.broken(level, BlockPos.containing(pos));
                // Ore mined out, a spawner broken, a chest gone: struck off what companions remember (MA5.7).
                com.mineagent.engine.MineAgentEngine.onBlockGone(level, BlockPos.containing(pos), context.affectedState());
            }
        } catch (RuntimeException ignored) {
            // Remembering is a courtesy; it must never get in the way of the game.
        }
    }
}
