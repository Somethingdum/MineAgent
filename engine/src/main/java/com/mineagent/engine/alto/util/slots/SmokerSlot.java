// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/slots/SmokerSlot.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.slots;

public class SmokerSlot extends Slot {
    public static final SmokerSlot INPUT_SLOT_FUEL = new SmokerSlot(1);
    public static final SmokerSlot INPUT_SLOT_MATERIALS = new SmokerSlot(0);
    public static final SmokerSlot OUTPUT_SLOT = new SmokerSlot(2);

    public SmokerSlot(int windowSlot) {
        this(windowSlot, false);
    }

    protected SmokerSlot(int slot, boolean inventory) {
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
        return "Smoker";
    }
}
