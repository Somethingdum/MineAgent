// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/ResourceTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.BotBehaviour;
import com.mineagent.engine.alto.multiversion.blockpos.BlockPosVer;
import com.mineagent.engine.alto.tasks.container.PickupFromContainerTask;
import com.mineagent.engine.alto.tasks.movement.DefaultGoToDimensionTask;
import com.mineagent.engine.alto.tasks.movement.PickupDroppedItemTask;
import com.mineagent.engine.alto.tasks.resources.MineAndCollectTask;
import com.mineagent.engine.alto.tasks.slot.EnsureFreePlayerCraftingGridTask;
import com.mineagent.engine.alto.tasks.slot.MoveInaccessibleItemToInventoryTask;
import com.mineagent.engine.alto.tasksystem.ITaskCanForce;
import com.mineagent.engine.alto.tasksystem.ITaskUsesCraftingGrid;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.trackers.storage.ContainerCache;
import com.mineagent.engine.alto.util.Dimension;
import com.mineagent.engine.alto.util.ItemTarget;
import com.mineagent.engine.alto.util.MiningRequirement;
import com.mineagent.engine.alto.util.helpers.ItemHelper;
import com.mineagent.engine.alto.util.helpers.StlHelper;
import com.mineagent.engine.alto.util.helpers.StorageHelper;
import com.mineagent.engine.alto.util.helpers.WorldHelper;
import com.mineagent.engine.alto.util.slots.PlayerSlot;
import com.mineagent.engine.alto.util.slots.Slot;
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The parent for all "collect an item" tasks.
 * <p>
 * If the target item is on the ground or in a chest, will grab from those sources first.
 */
public abstract class ResourceTask extends Task implements ITaskCanForce {

    protected final ItemTarget[] itemTargets;

    private final PickupDroppedItemTask pickupTask;
    private final EnsureFreePlayerCraftingGridTask ensureFreeCraftingGridTask = new EnsureFreePlayerCraftingGridTask();
    private ContainerCache currentContainer;
    // Extra resource parameters
    private Block[] mineIfPresent = null;
    private boolean forceDimension = false;
    private boolean allowContainers = false;
    private Dimension targetDimension;
    private BlockPos mineLastClosest = null;

    public ResourceTask(ItemTarget[] itemTargets) {
        this.itemTargets = itemTargets;
        pickupTask = new PickupDroppedItemTask(this.itemTargets, true);
    }

    public ResourceTask(ItemTarget target) {
        this(new ItemTarget[]{target});
    }

    public ResourceTask(Item item, int targetCount) {
        this(new ItemTarget(item, targetCount));
    }

    @Override
    public boolean isFinished() {
        return StorageHelper.itemTargetsMetInventoryNoCursor(itemTargets);
    }

    @Override
    public boolean shouldForce(Task interruptingCandidate) {
        // We have an important item target in our cursor.
        return StorageHelper.itemTargetsMetInventory(itemTargets) && !isFinished()
                // This _should_ be redundant, but it'll be a guard just to make 100% sure.
                && Arrays.stream(itemTargets).anyMatch(target -> target.matches(StorageHelper.getItemStackInCursorSlot().getItem()));
    }

    @Override
    protected void onStart() {
        BotBehaviour botBehaviour = AltoClef.getInstance().getBehaviour();

        botBehaviour.push();
        //removeThrowawayItems(_itemTargets);
        botBehaviour.addProtectedItems(ItemTarget.getMatches(itemTargets));

        onResourceStart(AltoClef.getInstance());
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        // If we have an item in an INACCESSIBLE inventory slot
        if (!(thisOrChildSatisfies(task -> task instanceof ITaskUsesCraftingGrid)) || ensureFreeCraftingGridTask.isActive()) {
            for (ItemTarget target : itemTargets) {
                if (StorageHelper.isItemInaccessibleToContainer(mod, target)) {
                    setDebugState("Moving from SPECIAL inventory slot");
                    return new MoveInaccessibleItemToInventoryTask(target);
                }
            }
        }
        // We have enough items COUNTING the cursor slot, we just need to move an item from our cursor.
        if (StorageHelper.itemTargetsMetInventory(itemTargets) && Arrays.stream(itemTargets).anyMatch(target -> target.matches(StorageHelper.getItemStackInCursorSlot().getItem()))) {
            setDebugState("Moving from cursor");
            Optional<Slot> moveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(StorageHelper.getItemStackInCursorSlot(), false);
            if (moveTo.isPresent()) {
                mod.getSlotHandler().clickSlot(moveTo.get(), 0, ClickType.PICKUP);
                return null;
            }
            if (ItemHelper.canThrowAwayStack(mod, StorageHelper.getItemStackInCursorSlot())) {
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
            return null;
        }

        if (!shouldAvoidPickingUp(mod)) {
            // Check if items are on the floor. If so, pick em up.
            if (mod.getEntityTracker().itemDropped(itemTargets)) {

                // If we're picking up a pickaxe (we can't go far underground or mine much)
                if (PickupDroppedItemTask.isIsGettingPickaxeFirst(mod)) {
                    if (pickupTask.isCollectingPickaxeForThis()) {
                        setDebugState("Picking up (pickaxe first!)");
                        // Our pickup task is the one collecting the pickaxe, keep it going.
                        return pickupTask;
                    }
                    // Only get items that are CLOSE to us.
                    Optional<ItemEntity> closest = mod.getEntityTracker().getClosestItemDrop(mod.getPlayer().position(), itemTargets);
                    if (closest.isPresent() && !closest.get().closerThan(mod.getPlayer(), 10)) {
                        return onResourceTick(mod);
                    }
                }

                double range = getPickupRange(mod);
                Optional<ItemEntity> closest = mod.getEntityTracker().getClosestItemDrop(mod.getPlayer().position(), itemTargets);
                if (range < 0 || (closest.isPresent() && closest.get().closerThan(mod.getPlayer(), range)) || (pickupTask.isActive() && !pickupTask.isFinished())) {
                    setDebugState("Picking up");
                    return pickupTask;
                }
            }
        }

        // Check for chests and grab resources from them.
        if (currentContainer == null && allowContainers) {
            List<ContainerCache> containersWithItem = mod.getItemStorage().getContainersWithItem(Arrays.stream(itemTargets).reduce(new Item[0], (items, target) -> ArrayUtils.addAll(items, target.getMatches()), ArrayUtils::addAll));
            if (!containersWithItem.isEmpty()) {
                ContainerCache closest = containersWithItem.stream().min(StlHelper.compareValues(container -> BlockPosVer.getSquaredDistance(container.getBlockPos(),mod.getPlayer().position()))).get();
                if (closest.getBlockPos().closerToCenterThan(mod.getPlayer().position(), mod.getModSettings().getResourceChestLocateRange())) {
                    currentContainer = closest;
                }
            }
        }
        if (currentContainer != null) {
            Optional<ContainerCache> container = mod.getItemStorage().getContainerAtPosition(currentContainer.getBlockPos());
            if (container.isPresent()) {
                if (Arrays.stream(itemTargets).noneMatch(target -> container.get().hasItem(target.getMatches()))) {
                    currentContainer = null;
                } else {
                    // We have a current chest, grab from it.
                    setDebugState("Picking up from container");
                    return new PickupFromContainerTask(currentContainer.getBlockPos(), itemTargets);
                }
            } else {
                currentContainer = null;
            }
        }

        // We may just mine if a block is found.
        if (mineIfPresent != null) {
            ArrayList<Block> satisfiedReqs = new ArrayList<>(Arrays.asList(mineIfPresent));
            satisfiedReqs.removeIf(block -> !StorageHelper.miningRequirementMet(MiningRequirement.getMinimumRequirementForBlock(block)));
            if (!satisfiedReqs.isEmpty()) {
                if (mod.getBlockScanner().anyFound(satisfiedReqs.toArray(Block[]::new))) {
                    Optional<BlockPos> closest = mod.getBlockScanner().getNearestBlock(mineIfPresent);
                    if (closest.isPresent() && closest.get().closerToCenterThan(mod.getPlayer().position(), mod.getModSettings().getResourceMineRange())) {
                        mineLastClosest = closest.get();
                    }
                    if (mineLastClosest != null) {
                        if (mineLastClosest.closerToCenterThan(mod.getPlayer().position(), mod.getModSettings().getResourceMineRange() * 1.5 + 20)) {
                            return new MineAndCollectTask(itemTargets, mineIfPresent, MiningRequirement.HAND);
                        }
                    }
                }
            }
        }
        // Make sure that items don't get stuck in the player crafting grid. May be an issue if a future task isn't a resource task.
        if (StorageHelper.isPlayerInventoryOpen()) {
            if (!(thisOrChildSatisfies(task -> task instanceof ITaskUsesCraftingGrid)) || ensureFreeCraftingGridTask.isActive()) {
                for (Slot slot : PlayerSlot.CRAFT_INPUT_SLOTS) {
                    if (!StorageHelper.getItemStackInSlot(slot).isEmpty()) {
                        return ensureFreeCraftingGridTask;
                    }
                }
            }
        }
        return onResourceTick(mod);
    }

    protected double getPickupRange(AltoClef mod) {
        return mod.getModSettings().getResourcePickupRange();
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef.getInstance().getBehaviour().pop();
        onResourceStop(AltoClef.getInstance(), interruptTask);
    }

    @Override
    protected boolean isEqual(Task other) {
        // Same target items
        if (other instanceof ResourceTask t) {
            if (!isEqualResource(t)) return false;
            return Arrays.equals(t.itemTargets, itemTargets);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        StringBuilder result = new StringBuilder();
        result.append(toDebugStringName()).append(": [");
        int c = 0;
        if (itemTargets != null) {
            for (ItemTarget target : itemTargets) {
                result.append(target != null ? target.toString() : "(null)");
                if (++c != itemTargets.length) {
                    result.append(", ");
                }
            }
        }
        result.append("]");
        return result.toString();
    }

    protected boolean isInWrongDimension(AltoClef mod) {
        if (forceDimension) {
            return WorldHelper.getCurrentDimension() != targetDimension;
        }
        return false;
    }

    protected Task getToCorrectDimensionTask(AltoClef mod) {
        return new DefaultGoToDimensionTask(targetDimension);
    }

    public ResourceTask mineIfPresent(Block[] toMine) {
        mineIfPresent = toMine;
        return this;
    }

    public ResourceTask forceDimension(Dimension dimension) {
        forceDimension = true;
        targetDimension = dimension;
        return this;
    }

    public void setAllowContainers(boolean value) {
        this.allowContainers = value;
    }

    public boolean getAllowContainers() {
        return allowContainers;
    }

    protected abstract boolean shouldAvoidPickingUp(AltoClef mod);

    protected abstract void onResourceStart(AltoClef mod);

    protected abstract Task onResourceTick(AltoClef mod);

    protected abstract void onResourceStop(AltoClef mod, Task interruptTask);

    protected abstract boolean isEqualResource(ResourceTask other);

    protected abstract String toDebugStringName();

    public ItemTarget[] getItemTargets() {
        return itemTargets;
    }
}
