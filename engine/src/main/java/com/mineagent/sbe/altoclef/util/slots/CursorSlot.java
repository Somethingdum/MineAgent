// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/slots/CursorSlot.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.slots;

public final class CursorSlot extends Slot {
   public static final CursorSlot SLOT = new CursorSlot();

   private CursorSlot() {
      super(null, -1);
   }

   @Override
   protected String getName() {
      return "Cursor Slot";
   }
}
