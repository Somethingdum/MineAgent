// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/mixins/PlayerCollidesWithEntityMixin.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.mixins;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({Player.class})
public class PlayerCollidesWithEntityMixin {
   @Redirect(
      method = {"touch"}, // SMH: Mojang's name
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/Entity;playerTouch(Lnet/minecraft/world/entity/player/Player;)V" // SMH: Mojang's names
      )
   )
   private void onCollideWithEntity(Entity self, Player player) {
//      if (player instanceof ClientPlayerEntity) {
//         EventBus.publish(new PlayerCollidedWithEntityEvent(player, self));
//      }

      self.playerTouch(player);
   }
}
