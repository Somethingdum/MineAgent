// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/eventbus/events/PlayerCollidedWithEntityEvent.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.eventbus.events;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class PlayerCollidedWithEntityEvent {
    public Player player;
    public Entity other;

    public PlayerCollidedWithEntityEvent(Player player, Entity other) {
        this.player = player;
        this.other = other;
    }
}
