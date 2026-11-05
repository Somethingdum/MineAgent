// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/eventbus/Subscription.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.eventbus;

import java.util.function.Consumer;

// A wrapper object for event subscription
public class Subscription<T> {
    private final Consumer<T> callback;
    private boolean shouldDelete;

    public Subscription(Consumer<T> callback) {
        this.callback = callback;
    }

    public void accept(T event) {
        callback.accept(event);
    }

    public void delete() {
        shouldDelete = true;
    }

    public boolean shouldDelete() {
        return shouldDelete;
    }
}
