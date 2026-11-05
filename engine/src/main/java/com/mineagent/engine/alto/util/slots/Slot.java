// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/slots/Slot.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.slots;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.Debug;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import java.util.Iterator;
import java.util.Objects;

// Very helpful links
// Container Window Slots (used to move stuff around all containers, including player):
//      https://wiki.vg/Inventory
// Player Inventory Slots (used to grab inventory items only):
//      https://minecraft.gamepedia.com/Inventory
public abstract class Slot {

    // -1 means cursor slot, the slot of the cursor when it holds an item.
    public static final int CURSOR_SLOT_INDEX = -1;
    private static final int UNDEFINED_SLOT_INDEX = -999;
    @SuppressWarnings("StaticInitializerReferencesSubClass")
    public static Slot UNDEFINED = new PlayerSlot(UNDEFINED_SLOT_INDEX);
    private final int _inventorySlot;
    private final int _windowSlot;
    private final boolean _isInventory;

    public Slot(int slot, boolean inventory) {
        _isInventory = inventory;
        if (inventory) {
            _inventorySlot = slot;
            _windowSlot = UNDEFINED_SLOT_INDEX;
            //_windowSlot = inventorySlotToWindowSlot(slot);
        } else {
            //_inventorySlot = windowSlotToInventorySlot(slot);
            _inventorySlot = UNDEFINED_SLOT_INDEX;
            _windowSlot = slot;
        }
    }

    private static Slot getFromCurrentScreenAbstract(int slot, boolean inventory) {
        switch (getCurrentType()) {
            case PLAYER:
                return new PlayerSlot(slot, inventory);
            case CRAFTING_TABLE:
                return new CraftingTableSlot(slot, inventory);
            case FURNACE_OR_SMITH_OR_SMOKER_OR_BLAST:
                return new FurnaceSlot(slot, inventory);
            case CHEST_LARGE:
                return new ChestSlot(slot, true, inventory);
            case CHEST_SMALL:
                return new ChestSlot(slot, false, inventory);
            default:
                Debug.logWarning("Unhandled slot for inventory check: " + getCurrentType());
                return null;
        }
    }

    public static Slot getFromCurrentScreen(int windowSlot) {
        return getFromCurrentScreenAbstract(windowSlot, false);
    }

    public static Slot getFromCurrentScreenInventory(int inventorySlot) {
        return getFromCurrentScreenAbstract(inventorySlot, true);
    }

    private static ContainerType getCurrentType() {
        // SMH: the companion's open menu stands for the client's screen.
        ServerPlayer player = AltoClef.getInstance().getPlayer();
        AbstractContainerMenu menu = player != null ? player.containerMenu : null;
        if (menu instanceof AbstractFurnaceMenu || menu instanceof SmithingMenu) {
            return ContainerType.FURNACE_OR_SMITH_OR_SMOKER_OR_BLAST;
        }
        if (menu instanceof ChestMenu handler) {
            boolean big = (handler.getRowCount() == 6);
            return big ? ContainerType.CHEST_LARGE : ContainerType.CHEST_SMALL;
        }
        if (menu instanceof CraftingMenu) {
            return ContainerType.CRAFTING_TABLE;
        }
        return ContainerType.PLAYER;
    }

    public static boolean isCursor(Slot slot) {
        return slot instanceof CursorSlot;
    }

    public static Iterable<Slot> getCurrentScreenSlots() {
        return () -> new Iterator<>() {
            final ServerPlayer player = AltoClef.getInstance().getPlayer();
            final AbstractContainerMenu handler = player != null ? player.containerMenu : null;
            final int MAX = handler != null ? handler.slots.size() : 0;
            int i = -1;

            @Override
            public boolean hasNext() {
                return i < MAX;
            }

            @Override
            public Slot next() {
                if (i == -1) {
                    ++i;
                    return new CursorSlot();
                }
                return Slot.getFromCurrentScreen(i++);
            }
        };
    }

    public int getInventorySlot() {
        if (!_isInventory) {
            return windowSlotToInventorySlot(_windowSlot);
        }
        return _inventorySlot;
    }

    public int getWindowSlot() {
        if (_isInventory) {
            return inventorySlotToWindowSlot(_inventorySlot);
        }
        return _windowSlot;
    }

    protected abstract int inventorySlotToWindowSlot(int inventorySlot);

    protected abstract int windowSlotToInventorySlot(int windowSlot);

    protected abstract String getName();

    @Override
    public String toString() {
        return getName() + (_isInventory ? "InventorySlot" : "Slot") + "{" +
                "inventory slot = " + getInventorySlot() +
                ", window slot = " + getWindowSlot() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (o instanceof Slot slot) {
            return getInventorySlot() == slot.getInventorySlot() && getWindowSlot() == slot.getWindowSlot();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getInventorySlot(), getWindowSlot());
    }

    /**
     * @return Whether this slot exists within the player's inventory or in a container that's disconnected from the player's inventory.
     */
    public boolean isSlotInPlayerInventory() {
        AbstractContainerMenu handler = AltoClef.getInstance().getPlayer() != null ? AltoClef.getInstance().getPlayer().containerMenu : null;
        int windowSlot = getWindowSlot();
        if (handler instanceof InventoryMenu) {
            // Everything visible is player inventory.
            return true;
        }
        int slotCount = handler != null ? handler.slots.size() : 0;
        return windowSlot >= (slotCount - (4 * 9));
    }

    enum ContainerType {
        PLAYER,
        CRAFTING_TABLE,
        CHEST_SMALL,
        CHEST_LARGE,
        FURNACE_OR_SMITH_OR_SMOKER_OR_BLAST
    }
}
