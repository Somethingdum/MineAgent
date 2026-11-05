// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/slots/ChestSlot.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.slots;

public class ChestSlot extends Slot {

    private final boolean big;

    public ChestSlot(int slot, boolean big) {
        this(slot, big, false);
    }

    public ChestSlot(int slot, boolean big, boolean inventory) {
        super(slot, inventory);
        this.big = big;
    }

    @Override
    public int inventorySlotToWindowSlot(int inventorySlot) {
        if (inventorySlot < 9) {
            return inventorySlot + (big ? 81 : 54);
        }
        return (inventorySlot - 9) + (big ? 54 : 27);
    }

    @Override
    protected int windowSlotToInventorySlot(int windowSlot) {
        int bottomStart = (big ? 81 : 54);
        if (windowSlot >= bottomStart) {
            return windowSlot - bottomStart;
        }
        return (windowSlot + 9) - (big ? 54 : 27);
    }

    @Override
    protected String getName() {
        return "Chest";
    }
}
