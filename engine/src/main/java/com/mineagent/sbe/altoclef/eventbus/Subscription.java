// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/eventbus/Subscription.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.eventbus;

import java.util.function.Consumer;

public class Subscription<T> {
   private final Consumer<T> callback;
   private boolean shouldDelete;

   public Subscription(Consumer<T> callback) {
      this.callback = callback;
   }

   public void accept(T event) {
      this.callback.accept(event);
   }

   public void delete() {
      this.shouldDelete = true;
   }

   public boolean shouldDelete() {
      return this.shouldDelete;
   }
}
