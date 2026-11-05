// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/slots/CursorSlot.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.slots;

public class CursorSlot extends Slot {

    public static final CursorSlot SLOT = new CursorSlot();

    public CursorSlot() {
        super(Slot.CURSOR_SLOT_INDEX, true);
    }

    @Override
    protected int inventorySlotToWindowSlot(int inventorySlot) {
        return Slot.CURSOR_SLOT_INDEX;
    }

    @Override
    protected int windowSlotToInventorySlot(int windowSlot) {
        return Slot.CURSOR_SLOT_INDEX;
    }

    @Override
    protected String getName() {
        return "Cursor Slot";
    }
}
