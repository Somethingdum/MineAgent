// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/slots/CraftingTableSlot.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.slots;

import java.util.stream.IntStream;

public class CraftingTableSlot extends Slot {
    public static final CraftingTableSlot OUTPUT_SLOT = new CraftingTableSlot(0);

    public static final CraftingTableSlot[] INPUT_SLOTS = IntStream.range(0, 9).mapToObj(ind -> getInputSlot(ind, true)).toArray(CraftingTableSlot[]::new);

    public CraftingTableSlot(int windowSlot) {
        this(windowSlot, false);
    }

    protected CraftingTableSlot(int slot, boolean inventory) {
        super(slot, inventory);
    }

    public static CraftingTableSlot getInputSlot(int x, int y) {
        return getInputSlot(y * 3 + x, true);
    }

    public static CraftingTableSlot getInputSlot(int index, boolean big) {
        index += 1;
        if (big) {
            // Default
            return new CraftingTableSlot(index);
        } else {
            // Small recipe in big window
            int x = index % 2;
            int y = index / 2;
            return getInputSlot(x, y);
        }
    }

    @Override
    public int inventorySlotToWindowSlot(int inventorySlot) {
        if (inventorySlot < 9) {
            return inventorySlot + 37;
        }
        return inventorySlot + 1;
    }

    @Override
    protected int windowSlotToInventorySlot(int windowSlot) {
        if (windowSlot >= 37) {
            return windowSlot - 37;
        }
        return windowSlot - 1;
    }

    @Override
    protected String getName() {
        return "CraftingTable";
    }
}
