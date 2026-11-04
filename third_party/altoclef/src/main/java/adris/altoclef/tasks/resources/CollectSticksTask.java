package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CraftInInventoryTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.storage.ItemStorageTracker;
import adris.altoclef.util.CraftingRecipe;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.RecipeTarget;
import adris.altoclef.util.helpers.ItemHelper;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectSticksTask extends ResourceTask {

    private final int _targetCount;

    public CollectSticksTask(int targetCount) {
        super(Items.STICK, targetCount);
        _targetCount = targetCount;
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {
        mod.getBehaviour().push();
    }

    @Override
    protected double getPickupRange(AltoClef mod) {
        ItemStorageTracker storage = mod.getItemStorage();
        if (storage.getItemCount(ItemHelper.PLANKS)*4+storage.getItemCount(ItemHelper.LOG)*4*4 > _targetCount) return 10;

        return 35;
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        // try to craft sticks from bamboo
        if (mod.getItemStorage().getItemCount(Items.BAMBOO) >= 2) {
            return new CraftInInventoryTask(new RecipeTarget(Items.STICK, Math.min(mod.getItemStorage().getItemCount(Items.BAMBOO)/2,_targetCount), CraftingRecipe.newShapedRecipe("sticks", new ItemTarget[]{new ItemTarget("bamboo"), null, new ItemTarget("bamboo"), null}, 1)));
        }

        Optional<BlockPos> nearestBush = mod.getBlockScanner().getNearestBlock(Blocks.DEAD_BUSH);
        // If there's a dead bush within range, go get it
        if (nearestBush.isPresent() && nearestBush.get().closerToCenterThan(mod.getPlayer().position(), 20)) {
            ResourceTask task = new MineAndCollectTask(Items.DEAD_BUSH, 1, new Block[]{Blocks.DEAD_BUSH}, MiningRequirement.HAND);
            task.setAllowContainers(false);

            return task;
        }
        // else craft from wood
        return new CraftInInventoryTask(new RecipeTarget(Items.STICK, _targetCount, CraftingRecipe.newShapedRecipe("sticks", new ItemTarget[]{new ItemTarget("planks"), null, new ItemTarget("planks"), null}, 4)));
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {
        mod.getBehaviour().pop();
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectSticksTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Crafting " + _targetCount + " sticks";
    }
}
