package me.sailex.mixins;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({Player.class})
public class PlayerCollidesWithEntityMixin {
   @Redirect(
      method = {"collideWithEntity"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/Entity;onPlayerCollision(Lnet/minecraft/entity/player/PlayerEntity;)V"
      )
   )
   private void onCollideWithEntity(Entity self, Player player) {
//      if (player instanceof ClientPlayerEntity) {
//         EventBus.publish(new PlayerCollidedWithEntityEvent(player, self));
//      }

      self.playerTouch(player);
   }
}
