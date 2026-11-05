// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/CraftGenericManuallyTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasks.slot.MoveItemToSlotFromInventoryTask;
import com.mineagent.engine.alto.tasks.slot.ReceiveCraftingOutputSlotTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.ItemTarget;
import com.mineagent.engine.alto.util.RecipeTarget;
import com.mineagent.engine.alto.util.helpers.ItemHelper;
import com.mineagent.engine.alto.util.helpers.StorageHelper;
import com.mineagent.engine.alto.util.slots.CraftingTableSlot;
import com.mineagent.engine.alto.util.slots.PlayerSlot;
import com.mineagent.engine.alto.util.slots.Slot;
import java.util.Optional;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Assuming a crafting screen is open, crafts a recipe.
 * <p>
 * Not useful for custom tasks.
 */
// SMH: it fills the 2x2 grid, as CraftGenericWithRecipeBooksTask does, so it says so (ITaskUsesCraftingGrid): without it
// the ResourceTask above it took for loose the items it put in the grid, and took them out again each tick.
public class CraftGenericManuallyTask extends Task implements com.mineagent.engine.alto.tasksystem.ITaskUsesCraftingGrid {

    private final RecipeTarget target;

    public CraftGenericManuallyTask(RecipeTarget target) {
        this.target = target;
    }

    @Override
    protected void onStart() {

    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        boolean bigCrafting = StorageHelper.isBigCraftingOpen();

        if (!bigCrafting && !StorageHelper.isPlayerInventoryOpen()) {
            // Make sure we're not in another screen before we craft,
            // otherwise crafting won't work
            ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot();
            if (!cursorStack.isEmpty()) {
                Optional<Slot> moveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursorStack, false);
                if (moveTo.isPresent()) {
                    mod.getSlotHandler().clickSlot(moveTo.get(), 0, ClickType.PICKUP);
                    return null;
                }
                if (ItemHelper.canThrowAwayStack(mod, cursorStack)) {
                    mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
                    return null;
                }
                Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
                // Try throwing away cursor slot if it's garbage
                if (garbage.isPresent()) {
                    mod.getSlotHandler().clickSlot(garbage.get(), 0, ClickType.PICKUP);
                    return null;
                }
                mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
            } else {
                StorageHelper.closeScreen();
            }
            // Just to be safe
        }

        Slot outputSlot = bigCrafting ? CraftingTableSlot.OUTPUT_SLOT : PlayerSlot.CRAFT_OUTPUT_SLOT;

        // Example:
        // We need 9 sticks
        // plank recipe results in 4 sticks
        // this means 3 planks per slot
        int requiredPerSlot = (int) Math.ceil((double) target.getTargetCount() / target.getRecipe().outputCount());

        // For each slot in table
        for (int craftSlot = 0; craftSlot < target.getRecipe().getSlotCount(); ++craftSlot) {
            ItemTarget toFill = target.getRecipe().getSlot(craftSlot);
            Slot currentCraftSlot;
            if (bigCrafting) {
                // Craft in table
                currentCraftSlot = CraftingTableSlot.getInputSlot(craftSlot, target.getRecipe().isBig());
            } else {
                // Craft in window
                currentCraftSlot = PlayerSlot.getCraftInputSlot(craftSlot);
            }
            ItemStack present = StorageHelper.getItemStackInSlot(currentCraftSlot);
            if (toFill == null || toFill.isEmpty()) {
                if (present.getItem() != Items.AIR) {
                    // Move this item OUT if it should be empty
                    setDebugState("Found INVALID slot");
                    mod.getSlotHandler().clickSlot(currentCraftSlot, 0, ClickType.PICKUP);
                }
            } else {
                boolean correctItem = toFill.matches(present.getItem());
                boolean isSatisfied = correctItem && present.getCount() >= requiredPerSlot;
                if (!isSatisfied) {
                    // We have items that satisfy, but we CAN NOT fill in the current slot!
                    // In that case, just grab from the output.
                    if (!mod.getItemStorage().hasItemInventoryOnly(present.getItem())) {
                        if (!StorageHelper.getItemStackInSlot(outputSlot).isEmpty()) {
                            setDebugState("NO MORE to fit: grabbing from output.");
                            return new ReceiveCraftingOutputSlotTask(outputSlot, target.getTargetCount());
                        } else {
                            // Move on to the NEXT slot, we can't fill this one anymore.
                            continue;
                        }
                    }

                    setDebugState("Moving item to slot...");
                    return new MoveItemToSlotFromInventoryTask(new ItemTarget(toFill, requiredPerSlot), currentCraftSlot);
                }
                // We could be OVER satisfied
                boolean oversatisfies = present.getCount() > requiredPerSlot;
                if (oversatisfies) {
                    setDebugState("OVER SATISFIED slot! Right clicking slot to extract half and spread it out more.");
                    mod.getSlotHandler().clickSlot(currentCraftSlot, 0, ClickType.PICKUP);
                }
            }
        }

        // Ensure our cursor is empty/can receive our item
        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
        if (!ItemHelper.canStackTogether(StorageHelper.getItemStackInSlot(outputSlot), cursor)) {
            Optional<Slot> toFit = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false).or(() -> StorageHelper.getGarbageSlot(mod));
            if (toFit.isPresent()) {
                mod.getSlotHandler().clickSlot(toFit.get(), 0, ClickType.PICKUP);
            } else {
                // Eh screw it
                mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
            }
        }

        if (!StorageHelper.getItemStackInSlot(outputSlot).isEmpty()) {
            return new ReceiveCraftingOutputSlotTask(outputSlot, target.getTargetCount());
        } else {
            // Wait
            return null;
        }
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof CraftGenericManuallyTask task) {
            return task.target.equals(target);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Crafting: " + target;
    }
}
