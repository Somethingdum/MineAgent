// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/chains/PlayerInteractionFixChain.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.chains;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.Debug;
import com.mineagent.engine.alto.tasksystem.TaskChain;
import com.mineagent.engine.alto.tasksystem.TaskRunner;
import com.mineagent.engine.alto.util.helpers.ItemHelper;
import com.mineagent.engine.alto.util.helpers.LookHelper;
import com.mineagent.engine.alto.util.helpers.StorageHelper;
import com.mineagent.engine.alto.util.slots.PlayerSlot;
import com.mineagent.engine.alto.util.slots.Slot;
import com.mineagent.engine.alto.util.time.TimerGame;
import com.mineagent.sbe.automatone.api.utils.Rotation;
import com.mineagent.sbe.automatone.api.utils.input.Input;
import java.util.Optional;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class PlayerInteractionFixChain extends TaskChain {
    private final TimerGame stackHeldTimeout = new TimerGame(1);
    private final TimerGame generalDuctTapeSwapTimeout = new TimerGame(30);
    private final TimerGame shiftDepressTimeout = new TimerGame(10);
    private final TimerGame betterToolTimer = new TimerGame(0);
    private final TimerGame mouseMovingButScreenOpenTimeout = new TimerGame(1);
    private ItemStack lastHandStack = null;

    private AbstractContainerMenu lastScreen; // SMH: the open menu stands for the screen
    private Rotation lastLookRotation;

    public PlayerInteractionFixChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    protected void onStop() {

    }

    @Override
    public void onInterrupt(TaskChain other) {

    }

    @Override
    protected void onTick() {
    }

    @Override
    public float getPriority() {
        if (!AltoClef.inGame()) return Float.NEGATIVE_INFINITY;

        AltoClef mod = AltoClef.getInstance();

        if (mod.getUserTaskChain().isActive() && betterToolTimer.elapsed()) {
            // Equip the right tool for the job if we're not using one.
            betterToolTimer.reset();
            if (mod.getControllerExtras().isBreakingBlock()) {
                BlockState state = mod.getWorld().getBlockState(mod.getControllerExtras().getBreakingBlockPos());
                Optional<Slot> bestToolSlot = StorageHelper.getBestToolSlot(mod, state);
                Slot currentEquipped = PlayerSlot.getEquipSlot();

                // if baritone is running, only accept tools OUTSIDE OF HOTBAR!
                // Baritone will take care of tools inside the hotbar.
                if (bestToolSlot.isPresent() && !bestToolSlot.get().equals(currentEquipped)) {
                    // ONLY equip if the item class is STRICTLY different (otherwise we swap around a lot)
                    if (StorageHelper.getItemStackInSlot(currentEquipped).getItem() != StorageHelper.getItemStackInSlot(bestToolSlot.get()).getItem()) {
                        boolean isAllowedToManage = (!mod.getClientBaritone().getPathingBehavior().isPathing() ||
                                bestToolSlot.get().getInventorySlot() >= 9) && !mod.getFoodChain().isTryingToEat();
                        if (isAllowedToManage) {
                            Debug.logMessage("Found better tool in inventory, equipping.");
                            ItemStack bestToolItemStack = StorageHelper.getItemStackInSlot(bestToolSlot.get());
                            Item bestToolItem = bestToolItemStack.getItem();
                            mod.getSlotHandler().forceEquipItem(bestToolItem);
                        }
                    }
                }
            }
        }

        // Unpress shift (it gets stuck for some reason???)
        if (mod.getInputControls().isHeldDown(Input.SNEAK)) {
            if (shiftDepressTimeout.elapsed()) {
                mod.getInputControls().release(Input.SNEAK);
            }
        } else {
            shiftDepressTimeout.reset();
        }

        // Refresh inventory
        if (generalDuctTapeSwapTimeout.elapsed()) {
            if (!mod.getControllerExtras().isBreakingBlock()) {
                Debug.logMessage("Refreshed inventory...");
                mod.getSlotHandler().refreshInventory();
                generalDuctTapeSwapTimeout.reset();
                return Float.NEGATIVE_INFINITY;
            }
        }

        ItemStack currentStack = StorageHelper.getItemStackInCursorSlot();

        if (currentStack != null && !currentStack.isEmpty()) {
            //noinspection PointlessNullCheck
            if (lastHandStack == null || !ItemStack.matches(currentStack, lastHandStack)) {
                // We're holding a new item in our stack!
                stackHeldTimeout.reset();
                lastHandStack = currentStack.copy();
            }
        } else {
            stackHeldTimeout.reset();
            lastHandStack = null;
        }

        // If we have something in our hand for a period of time...
        if (lastHandStack != null && stackHeldTimeout.elapsed()) {
            Optional<Slot> moveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(lastHandStack, false);
            if (moveTo.isPresent()) {
                mod.getSlotHandler().clickSlot(moveTo.get(), 0, ClickType.PICKUP);
                return Float.NEGATIVE_INFINITY;
            }
            if (ItemHelper.canThrowAwayStack(mod, StorageHelper.getItemStackInCursorSlot())) {
                mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
                return Float.NEGATIVE_INFINITY;
            }
            Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
            // Try throwing away cursor slot if it's garbage
            if (garbage.isPresent()) {
                mod.getSlotHandler().clickSlot(garbage.get(), 0, ClickType.PICKUP);
                return Float.NEGATIVE_INFINITY;
            }
            mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
            return Float.NEGATIVE_INFINITY;
        }

        if (shouldCloseOpenScreen()) {
            //Debug.logMessage("Closed screen since we changed our look.");
            ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot();
            if (!cursorStack.isEmpty()) {
                Optional<Slot> moveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursorStack, false);
                if (moveTo.isPresent()) {
                    mod.getSlotHandler().clickSlot(moveTo.get(), 0, ClickType.PICKUP);
                    return Float.NEGATIVE_INFINITY;
                }
                if (ItemHelper.canThrowAwayStack(mod, cursorStack)) {
                    mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
                    return Float.NEGATIVE_INFINITY;
                }
                Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
                // Try throwing away cursor slot if it's garbage
                if (garbage.isPresent()) {
                    mod.getSlotHandler().clickSlot(garbage.get(), 0, ClickType.PICKUP);
                    return Float.NEGATIVE_INFINITY;
                }
                mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
            } else {
                StorageHelper.closeScreen();
            }
            return Float.NEGATIVE_INFINITY;
        }

        return Float.NEGATIVE_INFINITY;
    }

    private boolean shouldCloseOpenScreen() {
        if (!AltoClef.getInstance().getModSettings().shouldCloseScreenWhenLookingOrMining())
            return false;

        // Only check look if we've had the same screen open for a while
        // SMH: the companion's open container stands for the client's screen; its inventory is none.
        var player = AltoClef.getInstance().getPlayer();
        AbstractContainerMenu openScreen = player.containerMenu == player.inventoryMenu ? null : player.containerMenu;
        if (openScreen != lastScreen) {
            mouseMovingButScreenOpenTimeout.reset();
        }
        // We're in the player screen/a screen we DON'T want to cancel out of
        if (openScreen == null || player.isDeadOrDying()) {
            mouseMovingButScreenOpenTimeout.reset();
            return false;
        }
        // Check for rotation change
        Rotation look = LookHelper.getLookRotation();
        if (lastLookRotation != null && mouseMovingButScreenOpenTimeout.elapsed()) {
            Rotation delta = look.subtract(lastLookRotation);
            if (Math.abs(delta.getYaw()) > 0.1f || Math.abs(delta.getPitch()) > 0.1f) {
                lastLookRotation = look;
                return true;
            }
            // do NOT update our last look rotation, just because we want to measure long term rotation.
        } else {
            lastLookRotation = look;
        }
        lastScreen = openScreen;
        return false;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public String getName() {
        return "Hand Stack Fix Chain";
    }
}
