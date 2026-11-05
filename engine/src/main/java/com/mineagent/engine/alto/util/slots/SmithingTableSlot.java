// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/slots/SmithingTableSlot.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.slots;

public class SmithingTableSlot extends Slot {
    public static final SmithingTableSlot INPUT_SLOT_TEMPLATE = new SmithingTableSlot(0);
    public static final SmithingTableSlot INPUT_SLOT_TOOL = new SmithingTableSlot(1);
    public static final SmithingTableSlot INPUT_SLOT_MATERIALS = new SmithingTableSlot(2);
    public static final SmithingTableSlot OUTPUT_SLOT = new SmithingTableSlot(3);

    public SmithingTableSlot(int slot) {
        this(slot, false);
    }

    SmithingTableSlot(int slot, boolean inventory) {
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
        return "Smithing Table";
    }
}
