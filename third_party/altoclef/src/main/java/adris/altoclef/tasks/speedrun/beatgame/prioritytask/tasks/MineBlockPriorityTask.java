package adris.altoclef.tasks.speedrun.beatgame.prioritytask.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.resources.MineAndCollectTask;
import adris.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators.DistancePriorityCalculator;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.StorageHelper;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * mines a specific block for item
 */
public class MineBlockPriorityTask extends PriorityTask{

    public final Block[] toMine;
    public final Item[] droppedItem;
    public final ItemTarget[] droppedItemTargets;
    private final MiningRequirement miningRequirement;
    private final DistancePriorityCalculator prioritySupplier;

    public MineBlockPriorityTask(Block[] toMine, Item[] droppedItem, MiningRequirement miningRequirement, DistancePriorityCalculator prioritySupplier) {
        this(toMine, droppedItem, miningRequirement, prioritySupplier, false, true, false);
    }

    public MineBlockPriorityTask(Block[] toMine, Item[] droppedItem, MiningRequirement miningRequirement, DistancePriorityCalculator prioritySupplier, Function<AltoClef, Boolean> canCall) {
        this(toMine, droppedItem, miningRequirement, prioritySupplier,canCall , false, true, false);
    }

    public MineBlockPriorityTask(Block[] toMine, Item[] droppedItem, MiningRequirement miningRequirement, DistancePriorityCalculator prioritySupplier, boolean shouldForce, boolean canCache, boolean bypassForceCooldown) {
        this(toMine, droppedItem, miningRequirement, prioritySupplier, mod -> true, shouldForce, canCache, bypassForceCooldown);
    }

    public MineBlockPriorityTask(Block[] toMine, Item[] droppedItem, MiningRequirement miningRequirement, DistancePriorityCalculator prioritySupplier, Function<AltoClef, Boolean> canCall, boolean shouldForce, boolean canCache, boolean bypassForceCooldown) {
        super(canCall, shouldForce, canCache, bypassForceCooldown);
        this.toMine = toMine;
        this.droppedItem = droppedItem;
        this.droppedItemTargets = ItemTarget.of(droppedItem);
        this.miningRequirement = miningRequirement;
        this.prioritySupplier = prioritySupplier;
    }

    @Override
    public Task getTask(AltoClef mod) {
        return new MineAndCollectTask(droppedItemTargets, toMine, miningRequirement);
    }

    @Override
    public String getDebugString() {
        return "Gathering resource: "+ Arrays.toString(droppedItem);
    }

    @Override
    protected double getPriority(AltoClef mod) {
        if (!StorageHelper.miningRequirementMet(miningRequirement)) return Double.NEGATIVE_INFINITY;

        double closestDist = getClosestDist(mod);
        int itemCount = mod.getItemStorage().getItemCount(droppedItem);

        prioritySupplier.update(itemCount);
        return prioritySupplier.getPriority(closestDist);
    }


    private double getClosestDist(AltoClef mod) {
        Vec3 pos = mod.getPlayer().position();

        Tuple<Double, Optional<BlockPos>> closestBlock = MineAndCollectTask.MineOrCollectTask.getClosestBlock(mod,pos,  toMine);
        Tuple<Double, Optional<ItemEntity>> closestDrop = MineAndCollectTask.MineOrCollectTask.getClosestItemDrop(mod,pos, droppedItemTargets);

        return Math.min(closestBlock.getA(), closestDrop.getA());
    }

}
