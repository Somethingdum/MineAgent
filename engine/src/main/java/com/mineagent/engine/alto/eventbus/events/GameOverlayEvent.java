// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/eventbus/events/GameOverlayEvent.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.eventbus.events;

public class GameOverlayEvent {
    public String message;

    public GameOverlayEvent(String message) {
        this.message = message;
    }
}
