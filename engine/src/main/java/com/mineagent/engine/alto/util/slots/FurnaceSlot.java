// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/slots/FurnaceSlot.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.slots;

public class FurnaceSlot extends Slot {
    public static final FurnaceSlot INPUT_SLOT_FUEL = new FurnaceSlot(1);
    public static final FurnaceSlot INPUT_SLOT_MATERIALS = new FurnaceSlot(0);
    public static final FurnaceSlot OUTPUT_SLOT = new FurnaceSlot(2);

    public FurnaceSlot(int windowSlot) {
        this(windowSlot, false);
    }

    protected FurnaceSlot(int slot, boolean inventory) {
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
        return "Furnace";
    }
}
