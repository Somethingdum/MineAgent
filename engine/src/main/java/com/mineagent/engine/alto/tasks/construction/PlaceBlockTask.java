// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/construction/PlaceBlockTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.construction;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.Debug;
import com.mineagent.engine.alto.TaskCatalogue;
import com.mineagent.engine.alto.multiversion.versionedfields.Items;
import com.mineagent.engine.alto.tasks.movement.GetToBlockTask;
import com.mineagent.engine.alto.tasks.movement.TimeoutWanderTask;
import com.mineagent.engine.alto.tasksystem.ITaskRequiresGrounded;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.ItemTarget;
import com.mineagent.engine.alto.util.helpers.ItemHelper;
import com.mineagent.engine.alto.util.helpers.WorldHelper;
import com.mineagent.engine.alto.util.progresscheck.MovementProgressChecker;
import com.mineagent.engine.alto.baritone.api.schematic.AbstractSchematic;
import com.mineagent.engine.alto.baritone.api.schematic.ISchematic;
import com.mineagent.engine.alto.baritone.api.utils.BlockOptionalMeta;
import com.mineagent.engine.alto.baritone.api.utils.input.Input;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Place a block type at a position
 */
public class PlaceBlockTask extends Task implements ITaskRequiresGrounded {

    private static final int MIN_MATERIALS = 1;
    private static final int PREFERRED_MATERIALS = 32;
    private final BlockPos target;
    private final Block[] toPlace;
    private final boolean useThrowaways;
    private final boolean autoCollectStructureBlocks;
    private final MovementProgressChecker progressChecker = new MovementProgressChecker();
    private final TimeoutWanderTask wanderTask = new TimeoutWanderTask(5); // This can get stuck forever, so we increase the range.
    private Task materialTask;
    private int failCount = 0;

    public PlaceBlockTask(BlockPos target, Block[] toPlace, boolean useThrowaways, boolean autoCollectStructureBlocks) {
        this.target = target;
        this.toPlace = toPlace;
        this.useThrowaways = useThrowaways;
        this.autoCollectStructureBlocks = autoCollectStructureBlocks;
    }

    public PlaceBlockTask(BlockPos target, Block... toPlace) {
        this(target, toPlace, false, false);
    }

    public int getMaterialCount(AltoClef mod) {
        int count = mod.getItemStorage().getItemCount(ItemHelper.blocksToItems(toPlace));

        if (useThrowaways) {
            count += mod.getItemStorage().getItemCount(mod.getClientBaritoneSettings().acceptableThrowawayItems.value.toArray(new Item[0]));
        }
        return count;
    }

    public static Task getMaterialTask(int count) {
        return TaskCatalogue.getSquashedItemTask(new ItemTarget(Items.DIRT, count), new ItemTarget(Items.COBBLESTONE,
                count), new ItemTarget(Items.NETHERRACK, count), new ItemTarget(Items.COBBLED_DEEPSLATE, count));
    }

    @Override
    protected void onStart() {
        progressChecker.reset();
        // If we get interrupted by another task, this might cause problems...
        //_wanderTask.resetWander();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        if (WorldHelper.isInNetherPortal()) {
            if (!mod.getClientBaritone().getPathingBehavior().isPathing()) {
                setDebugState("Getting out from nether portal");
                mod.getInputControls().hold(Input.SNEAK);
                mod.getInputControls().hold(Input.MOVE_FORWARD);
                return null;
            } else {
                mod.getInputControls().release(Input.SNEAK);
                mod.getInputControls().release(Input.MOVE_BACK);
                mod.getInputControls().release(Input.MOVE_FORWARD);
            }
        } else {
            if (mod.getClientBaritone().getPathingBehavior().isPathing()) {
                mod.getInputControls().release(Input.SNEAK);
                mod.getInputControls().release(Input.MOVE_BACK);
                mod.getInputControls().release(Input.MOVE_FORWARD);
            }
        }
        // Perform timeout wander
        if (wanderTask.isActive() && !wanderTask.isFinished()) {
            setDebugState("Wandering.");
            progressChecker.reset();
            return wanderTask;
        }

        if (autoCollectStructureBlocks) {
            if (materialTask != null && materialTask.isActive() && !materialTask.isFinished()) {
                setDebugState("No structure items, collecting cobblestone + dirt as default.");
                if (getMaterialCount(mod) < PREFERRED_MATERIALS) {
                    return materialTask;
                } else {
                    materialTask = null;
                }
            }

            //Item[] items = Util.toArray(Item.class, mod.getClientBaritoneSettings().acceptableThrowawayItems.value);
            if (getMaterialCount(mod) < MIN_MATERIALS) {
                // TODO: Mine items, extract their resource key somehow.
                materialTask = getMaterialTask(PREFERRED_MATERIALS);
                progressChecker.reset();
                return materialTask;
            }
        }


        // Check if we're approaching our point. If we fail, wander for a bit.
        if (!progressChecker.check(mod)) {
            failCount++;
            if (!tryingAlternativeWay()) {
                Debug.logMessage("Failed to place, wandering timeout.");
                return wanderTask;
            } else {
                Debug.logMessage("Trying alternative way of placing block...");
            }
        }


        // Place block
        if (tryingAlternativeWay()) {
            setDebugState("Alternative way: Trying to go above block to place block.");
            return new GetToBlockTask(target.above(), false);
        } else {
            setDebugState("Letting baritone place a block.");
            // Perform baritone placement
            if (!mod.getClientBaritone().getBuilderProcess().isActive()) {
                Debug.logInternal("Run Structure Build");
                ISchematic schematic = new PlaceStructureSchematic(mod);
                mod.getClientBaritone().getBuilderProcess().build("structure", schematic, target);
            }
        }
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef.getInstance().getClientBaritone().getBuilderProcess().onLostControl();
    }

    //TODO: Place structure where a leaf block was???? Might need to delete the block first if it's not empty/air/water.

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof PlaceBlockTask task) {
            return task.target.equals(target) && task.useThrowaways == useThrowaways && Arrays.equals(task.toPlace, toPlace);
        }
        return false;
    }

    @Override
    public boolean isFinished() {
        assert AltoClef.getInstance().getWorld() != null;
        if (useThrowaways) {
            return WorldHelper.isSolidBlock(target);
        }
        BlockState state = AltoClef.getInstance().getWorld().getBlockState(target);
        return ArrayUtils.contains(toPlace, state.getBlock());
    }

    @Override
    protected String toDebugString() {
        return "Place structure" + ArrayUtils.toString(toPlace) + " at " + target.toShortString();
    }

    private boolean tryingAlternativeWay() {
        return failCount % 4 == 3;
    }

    private class PlaceStructureSchematic extends AbstractSchematic {

        private final AltoClef _mod;

        public PlaceStructureSchematic(AltoClef mod) {
            super(1, 1, 1);
            _mod = mod;
        }

        @Override
        public BlockState desiredState(int x, int y, int z, BlockState blockState, List<BlockState> available) {
            if (x == 0 && y == 0 && z == 0) {
                // Place!!
                if (!available.isEmpty()) {
                    for (BlockState possible : available) {
                        if (possible == null) continue;
                        if (useThrowaways && _mod.getClientBaritoneSettings().acceptableThrowawayItems.value.contains(possible.getBlock().asItem())) {
                            return possible;
                        }
                        if (Arrays.asList(toPlace).contains(possible.getBlock())) {
                            return possible;
                        }
                    }
                }
                Debug.logInternal("Failed to find throwaway block");
                // No throwaways available!!
                return new BlockOptionalMeta(Blocks.COBBLESTONE).getAnyBlockState();
            }
            // Don't care.
            return blockState;
        }
    }
}
