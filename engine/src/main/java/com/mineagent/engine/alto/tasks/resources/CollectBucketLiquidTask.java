// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/resources/CollectBucketLiquidTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.resources;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.Debug;
import com.mineagent.engine.alto.TaskCatalogue;
import com.mineagent.engine.alto.tasks.DoToClosestBlockTask;
import com.mineagent.engine.alto.tasks.InteractWithBlockTask;
import com.mineagent.engine.alto.tasks.ResourceTask;
import com.mineagent.engine.alto.tasks.construction.DestroyBlockTask;
import com.mineagent.engine.alto.tasks.movement.DefaultGoToDimensionTask;
import com.mineagent.engine.alto.tasks.movement.GetCloseToBlockTask;
import com.mineagent.engine.alto.tasks.movement.TimeoutWanderTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.Dimension;
import com.mineagent.engine.alto.util.ItemTarget;
import com.mineagent.engine.alto.util.helpers.LookHelper;
import com.mineagent.engine.alto.util.helpers.WorldHelper;
import com.mineagent.engine.alto.util.progresscheck.MovementProgressChecker;
import com.mineagent.engine.alto.util.time.TimerGame;
import com.mineagent.engine.alto.baritone.api.utils.input.Input;
import java.util.HashSet;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectBucketLiquidTask extends ResourceTask {

    private final HashSet<BlockPos> blacklist = new HashSet<>();
    private final TimerGame tryImmediatePickupTimer = new TimerGame(3);
    private final TimerGame pickedUpTimer = new TimerGame(0.5);
    private final int count;

    private final Item target;
    private final Block toCollect;
    private final String liquidName;
    private final MovementProgressChecker progressChecker = new MovementProgressChecker();

    private boolean wasWandering = false;

    public CollectBucketLiquidTask(String liquidName, Item filledBucket, int targetCount, Block toCollect) {
        super(filledBucket, targetCount);
        this.liquidName = liquidName;
        target = filledBucket;
        count = targetCount;
        this.toCollect = toCollect;
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void onResourceStart(AltoClef mod) {
        // Track fluids
        mod.getBehaviour().push();
        mod.getBehaviour().setRayTracingFluidHandling(ClipContext.Fluid.SOURCE_ONLY);

        // Avoid breaking / placing blocks at our liquid
        mod.getBehaviour().avoidBlockBreaking((pos) -> AltoClef.getInstance().getWorld().getBlockState(pos).getBlock() == toCollect);
        mod.getBehaviour().avoidBlockPlacing((pos) -> AltoClef.getInstance().getWorld().getBlockState(pos).getBlock() == toCollect);

        mod.getClientBaritoneSettings().avoidUpdatingFallingBlocks.value = true;
        //_blacklist.clear();

        progressChecker.reset();
    }


    @Override
    protected Task onTick() {
        Task result = super.onTick();
        // Reset our "first time" timeout/wander flag.
        if (!thisOrChildAreTimedOut()) {
            wasWandering = false;
        }
        return result;
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        if (mod.getClientBaritone().getPathingBehavior().isPathing()) {
            progressChecker.reset();
        }
        // If we're standing inside a liquid, go pick it up.
        if (tryImmediatePickupTimer.elapsed() && !mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
            Block standingInside = mod.getWorld().getBlockState(mod.getPlayer().blockPosition()).getBlock();
            if (standingInside == toCollect && WorldHelper.isSourceBlock(mod.getPlayer().blockPosition(), false)) {
                setDebugState("Trying to collect (we are in it)");
                mod.getInputControls().forceLook(0, 90);
                //mod.getClientBaritone().getLookBehavior().updateTarget(new Rotation(0, 90), true);
                //Debug.logMessage("Looking at " + _toCollect + ", picking up right away.");
                tryImmediatePickupTimer.reset();
                if (mod.getSlotHandler().forceEquipItem(Items.BUCKET)) {
                    mod.getInputControls().tryPress(Input.CLICK_RIGHT);
                    mod.getExtraBaritoneSettings().setInteractionPaused(true);
                    pickedUpTimer.reset();
                    progressChecker.reset();
                }
                return null;
            }
        }

        if (!pickedUpTimer.elapsed()) {
            mod.getExtraBaritoneSettings().setInteractionPaused(false);
            progressChecker.reset();
            // Wait for force pickup
            return null;
        }

        // Get buckets if we need em
        int bucketsNeeded = count - mod.getItemStorage().getItemCount(Items.BUCKET) - mod.getItemStorage().getItemCount(target);
        if (bucketsNeeded > 0) {
            setDebugState("Getting bucket...");
            return TaskCatalogue.getItemTask(Items.BUCKET, bucketsNeeded);
        }

        Predicate<BlockPos> isSafeSourceLiquid = blockPos -> {
            if (blacklist.contains(blockPos)) return false;
            if (!WorldHelper.canReach(blockPos)) return false;
            if (!WorldHelper.canReach(blockPos.above())) return false; // We may try reaching the block above.
            assert AltoClef.getInstance().getWorld() != null;

            Block above = mod.getWorld().getBlockState(blockPos.above()).getBlock();
            // We break the block above. If it's bedrock, ignore.
            if (above == Blocks.BEDROCK || above == Blocks.WATER) {
                return false;
            }

            // check if surrounding blocks are not water, so it doesn't spill everywhere
            for (Direction direction : Direction.values()) {
                if (direction.getAxis().isVertical()) continue;

                if (mod.getWorld().getBlockState(blockPos.above().relative(direction)).getBlock() == Blocks.WATER) {
                    return false;
                }
            }

            return WorldHelper.isSourceBlock(blockPos, false);
        };

        // Find nearest water and right click it
        if (mod.getBlockScanner().anyFound(isSafeSourceLiquid, toCollect)) {
            // We want to MINIMIZE this distance to liquid.
            setDebugState("Trying to collect...");
            //Debug.logMessage("TEST: " + RayTraceUtils.fluidHandling);

            return new DoToClosestBlockTask(blockPos -> {
                // Clear above if lava because we can't enter.
                // but NOT if we're standing right above.
                if (mod.getWorld().getBlockState(blockPos.above()).isSolid()) {
                    if (!progressChecker.check(mod)) {
                        mod.getClientBaritone().getPathingBehavior().cancelEverything();
                        mod.getClientBaritone().getPathingBehavior().forceCancel();
                        mod.getClientBaritone().getExploreProcess().onLostControl();
                        mod.getClientBaritone().getCustomGoalProcess().onLostControl();
                        Debug.logMessage("Failed to break, blacklisting.");
                        mod.getBlockScanner().requestBlockUnreachable(blockPos);
                        blacklist.add(blockPos);
                    }
                    return new DestroyBlockTask(blockPos.above());
                }

                if (tries > 75) {
                    if (timeoutTimer.elapsed()) {
                        tries = 0;
                    }
                    mod.log("trying to wander "+timeoutTimer.getDuration());
                    return new TimeoutWanderTask();
                }
                timeoutTimer.reset();

                // We can reach the block.
                if (LookHelper.getReach(blockPos).isPresent() &&
                        mod.getClientBaritone().getPathingBehavior().isSafeToCancel()) {
                    tries++;
                    return new InteractWithBlockTask(new ItemTarget(Items.BUCKET, 1), blockPos, toCollect != Blocks.LAVA, new Vec3i(0, 1, 0));
                }
                // Get close enough.
                // up because if we go below we'll try to move next to the liquid (for lava, not a good move)
                if (this.thisOrChildAreTimedOut() && !wasWandering) {
                    mod.getBlockScanner().requestBlockUnreachable(blockPos.above());
                    wasWandering = true;
                }
                return new GetCloseToBlockTask(blockPos.above());
            }, isSafeSourceLiquid, toCollect);
        }

        // Dimension
        if (toCollect == Blocks.WATER && WorldHelper.getCurrentDimension() == Dimension.NETHER) {
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        // Oof, no liquid found.
        setDebugState("Searching for liquid by wandering around aimlessly");

        return new TimeoutWanderTask();
    }
    int tries = 0;
    TimerGame timeoutTimer = new TimerGame(2);

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {
        mod.getBehaviour().pop();
        //mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
        mod.getExtraBaritoneSettings().setInteractionPaused(false);

        mod.getClientBaritoneSettings().avoidUpdatingFallingBlocks.value = false;
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        if (other instanceof CollectBucketLiquidTask task) {
            if (task.count != count) return false;
            return task.toCollect == toCollect;
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Collect " + count + " " + liquidName + " buckets";
    }

    public static class CollectWaterBucketTask extends CollectBucketLiquidTask {
        public CollectWaterBucketTask(int targetCount) {
            super("water", Items.WATER_BUCKET, targetCount, Blocks.WATER);
        }
    }

    public static class CollectLavaBucketTask extends CollectBucketLiquidTask {
        public CollectLavaBucketTask(int targetCount) {
            super("lava", Items.LAVA_BUCKET, targetCount, Blocks.LAVA);
        }
    }

}
