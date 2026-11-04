package com.mineagent.neoforge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * A creeper's blast, seen just before it takes its blocks (SMH plan 70: creeper holes filled near home): the blocks it
 * is about to blow away and what they are, handed to the companions. It changes nothing about the explosion.
 */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @Shadow @Final private Level level;

    @Inject(method = "finalizeExplosion(Z)V", at = @At("HEAD"))
    private void mineagent$noteBlast(boolean spawnParticles, CallbackInfo ci) {
        try {
            Explosion self = (Explosion) (Object) this;
            if (!(level instanceof ServerLevel server) || !self.interactsWithBlocks()) return;
            if (!(self.getDirectSourceEntity() instanceof net.minecraft.world.entity.monster.Creeper)) return;
            List<BlockPos> blown = new ArrayList<>();
            List<net.minecraft.world.level.block.state.BlockState> was = new ArrayList<>();
            for (BlockPos p : self.getToBlow()) {
                var st = server.getBlockState(p);
                if (st.isAir()) continue;
                blown.add(p.immutable());
                was.add(st);
            }
            if (!blown.isEmpty()) com.mineagent.engine.MineAgentEngine.onCreeperBlast(server, blown, was);
        } catch (RuntimeException ignored) {
            // Noting a crater is a courtesy; it must never get in the way of the game.
        }
    }
}
