// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/control/PlayerExtraController.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.control;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.eventbus.EventBus;
import com.mineagent.sbe.altoclef.eventbus.events.BlockBreakingCancelEvent;
import com.mineagent.sbe.altoclef.eventbus.events.BlockBreakingEvent;
import com.mineagent.sbe.altoclef.multiversion.ServerPlayerEntityVer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

public class PlayerExtraController {
   private final AltoClefController mod;
   private BlockPos blockBreakPos;

   public PlayerExtraController(AltoClefController mod) {
      this.mod = mod;
      EventBus.subscribe(BlockBreakingEvent.class, evt -> this.onBlockBreak(evt.blockPos));
      EventBus.subscribe(BlockBreakingCancelEvent.class, evt -> this.onBlockStopBreaking());
   }

   private void onBlockBreak(BlockPos pos) {
      this.blockBreakPos = pos;
   }

   private void onBlockStopBreaking() {
      this.blockBreakPos = null;
   }

   public BlockPos getBreakingBlockPos() {
      return this.blockBreakPos;
   }

   public boolean isBreakingBlock() {
      return this.blockBreakPos != null;
   }

   public boolean inRange(Entity entity) {
      return this.mod.getPlayer().closerThan(entity, this.mod.getModSettings().getEntityReachRange());
   }

    /**
     * Sends a chat message to all players.
     * @param message the message to send
     */
    public void chat(String message) {
        
        mod.getPlayer().server.getPlayerList().broadcastChatMessage(
                PlayerChatMessage.system(message), mod.getPlayer(),
                ChatType.bind(ChatType.CHAT, mod.getPlayer())
        );
         
    }

   public void attack(Entity entity) {
      if (this.inRange(entity)) {
         this.mod.getPlayer().attack(entity);
         this.mod.getPlayer().swing(InteractionHand.MAIN_HAND);
      }
   }
}
