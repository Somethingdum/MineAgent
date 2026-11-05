// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/eventbus/events/ScreenOpenEvent.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.eventbus.events;

import net.minecraft.world.inventory.AbstractContainerMenu;

/** SMH: a server-side body has no screens; the container menu it opened (null when it closed one) stands for it. */
public class ScreenOpenEvent {
    public AbstractContainerMenu screen;
    public boolean preOpen;

    public ScreenOpenEvent(AbstractContainerMenu screen, boolean preOpen) {
        this.screen = screen;
        this.preOpen = preOpen;
    }
}
