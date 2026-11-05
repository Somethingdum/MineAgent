// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/slot/EnsureFreePlayerCraftingGridTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.slot;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.helpers.StorageHelper;
import com.mineagent.engine.alto.util.slots.PlayerSlot;
import com.mineagent.engine.alto.util.slots.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public class EnsureFreePlayerCraftingGridTask extends Task {
    @Override
    protected void onStart() {

    }

    @Override
    protected Task onTick() {
        setDebugState("Clearing the 2x2 crafting grid");
        for (Slot slot : PlayerSlot.CRAFT_INPUT_SLOTS) {
            ItemStack items = StorageHelper.getItemStackInSlot(slot);
            ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
            if (!cursor.isEmpty()) {
                return new EnsureFreeCursorSlotTask();
            }
            if (!items.isEmpty()) {
                AltoClef.getInstance().getSlotHandler().clickSlot(slot, 0, ClickType.PICKUP);
                return null;
            }
        }
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof EnsureFreePlayerCraftingGridTask;
    }

    @Override
    protected String toDebugString() {
        return "Breaking the crafting grid";
    }
}
