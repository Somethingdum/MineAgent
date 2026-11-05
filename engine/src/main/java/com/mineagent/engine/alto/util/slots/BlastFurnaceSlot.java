// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/slots/BlastFurnaceSlot.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.slots;

public class BlastFurnaceSlot extends Slot {
    public static final BlastFurnaceSlot INPUT_SLOT_FUEL = new BlastFurnaceSlot(1);
    public static final BlastFurnaceSlot INPUT_SLOT_MATERIALS = new BlastFurnaceSlot(0);
    public static final BlastFurnaceSlot OUTPUT_SLOT = new BlastFurnaceSlot(2);

    public BlastFurnaceSlot(int windowSlot) {
        this(windowSlot, false);
    }

    protected BlastFurnaceSlot(int slot, boolean inventory) {
        super(slot, inventory);
    }

    @Override
    public int inventorySlotToWindowSlot(int inventorySlot) {
        if (inventorySlot < 9) {
            return inventorySlot + 30;
        }
        return inventorySlot - 6;
    }

    @Override
    protected int windowSlotToInventorySlot(int windowSlot) {
        if (windowSlot >= 30) {
            return windowSlot - 30;
        }
        return windowSlot + 6;
    }

    @Override
    protected String getName() {
        return "Blast Furnace";
    }
}
