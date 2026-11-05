// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/mixins/WorldBlockModifiedMixin.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.mixins;

import com.mineagent.sbe.altoclef.eventbus.EventBus;
import com.mineagent.sbe.altoclef.eventbus.events.BlockPlaceEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Level.class})
public class WorldBlockModifiedMixin {
   @Unique
   private boolean hasBlock(BlockState state, BlockPos pos) {
      return !state.isAir() && state.isRedstoneConductor((Level)(Object)this, pos);
   }

   
   
   @Inject(
      method = {"onBlockStateChange"}, // SMH: Mojang's name
      at = {@At("HEAD")}
   )
   
   public void onBlockWasChanged(BlockPos pos, BlockState oldBlock, BlockState newBlock, CallbackInfo ci) {
      if (!((Level)(Object)this).isClientSide() && !this.hasBlock(oldBlock, pos) && this.hasBlock(newBlock, pos)) {
         BlockPlaceEvent evt = new BlockPlaceEvent(pos, newBlock);
         EventBus.publish(evt);
      }
   }
}
