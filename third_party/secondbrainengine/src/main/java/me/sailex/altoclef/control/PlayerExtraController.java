package me.sailex.altoclef.control;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.eventbus.EventBus;
import me.sailex.altoclef.eventbus.events.BlockBreakingCancelEvent;
import me.sailex.altoclef.eventbus.events.BlockBreakingEvent;
import me.sailex.altoclef.multiversion.ServerPlayerEntityVer;
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
