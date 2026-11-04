package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.MLGBucketTask;
import adris.altoclef.tasksystem.ITaskOverridesGrounded;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;

@SuppressWarnings("UnnecessaryLocalVariable")
public class MLGBucketFallChain extends SingleTaskChain implements ITaskOverridesGrounded {

    private final TimerGame tryCollectWaterTimer = new TimerGame(4);
    private final TimerGame pickupRepeatTimer = new TimerGame(0.25);
    private MLGBucketTask lastMLG = null;
    private boolean wasPickingUp = false;
    private boolean doingChorusFruit = false;

    public MLGBucketFallChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    protected void onTaskFinish(AltoClef mod) {
        //_lastMLG = null;
    }

    @Override
    public float getPriority() {
        if (!AltoClef.inGame()) return Float.NEGATIVE_INFINITY;

        AltoClef mod = AltoClef.getInstance();

        if (isFalling(mod)) {
            tryCollectWaterTimer.reset();
            setTask(new MLGBucketTask());
            lastMLG = (MLGBucketTask) mainTask;
            return 100;
        } else if (!tryCollectWaterTimer.elapsed()) { // Why -0.5? Cause it's slower than -0.7.
            // We just placed water, try to collect it.
            if (mod.getItemStorage().hasItem(Items.BUCKET) && !mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
                if (lastMLG != null) {
                    BlockPos placed = lastMLG.getWaterPlacedPos();
                    boolean isPlacedWater;
                    try {
                        isPlacedWater = mod.getWorld().getBlockState(placed).getBlock() == Blocks.WATER;
                    } catch (Exception e) {
                        isPlacedWater = false;
                    }
                    //Debug.logInternal("PLACED: " + placed);
                    if (placed != null && placed.closerToCenterThan(mod.getPlayer().position(), 5.5) && isPlacedWater) {
                        BlockPos toInteract = placed;
                        // Allow looking at fluids
                        mod.getBehaviour().push();
                        mod.getBehaviour().setRayTracingFluidHandling(ClipContext.Fluid.SOURCE_ONLY);
                        Optional<Rotation> reach = LookHelper.getReach(toInteract, Direction.UP);
                        if (reach.isPresent()) {
                            mod.getClientBaritone().getLookBehavior().updateTarget(reach.get(), true);
                            if (mod.getClientBaritone().getPlayerContext().isLookingAt(toInteract)) {
                                if (mod.getSlotHandler().forceEquipItem(Items.BUCKET)) {
                                    if (pickupRepeatTimer.elapsed()) {
                                        // Pick up
                                        pickupRepeatTimer.reset();
                                        mod.getInputControls().tryPress(Input.CLICK_RIGHT);
                                        wasPickingUp = true;
                                    } else if (wasPickingUp) {
                                        // Stop picking up, wait and try again.
                                        wasPickingUp = false;
                                    }
                                }
                            }
                        } else {
                            // Eh just try collecting water the regular way if all else fails.
                            setTask(TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1));
                        }
                        mod.getBehaviour().pop();
                        return 60;
                    }
                }
            }
        }
        if (wasPickingUp) {
            wasPickingUp = false;
            lastMLG = null;
        }
        if (mod.getPlayer().hasEffect(MobEffects.LEVITATION) &&
                !mod.getPlayer().getCooldowns().isOnCooldown(Items.CHORUS_FRUIT) &&
                mod.getPlayer().getActiveEffectsMap().get(MobEffects.LEVITATION).getDuration() <= 70 &&
                mod.getItemStorage().hasItemInventoryOnly(Items.CHORUS_FRUIT) &&
                !mod.getItemStorage().hasItemInventoryOnly(Items.WATER_BUCKET)) {
            doingChorusFruit = true;
            mod.getSlotHandler().forceEquipItem(Items.CHORUS_FRUIT);
            mod.getInputControls().hold(Input.CLICK_RIGHT);
            mod.getExtraBaritoneSettings().setInteractionPaused(true);
        } else if (doingChorusFruit) {
            doingChorusFruit = false;
            mod.getInputControls().release(Input.CLICK_RIGHT);
            mod.getExtraBaritoneSettings().setInteractionPaused(false);
        }
        lastMLG = null;
        return Float.NEGATIVE_INFINITY;
    }

    @Override
    public String getName() {
        return "MLG Water Bucket Fall Chain";
    }

    @Override
    public boolean isActive() {
        // We're always checking for mlg.
        return true;
    }

    public boolean doneMLG() {
        return lastMLG == null;
    }

    public boolean isChorusFruiting() {
        return doingChorusFruit;
    }

    public boolean isFalling(AltoClef mod) {
        if (!mod.getModSettings().shouldAutoMLGBucket()) {
            return false;
        }
        if (mod.getPlayer().isSwimming() || mod.getPlayer().isInWater() || mod.getPlayer().onGround() || mod.getPlayer().onClimbable()) {
            // We're grounded.
            return false;
        }
        double ySpeed = mod.getPlayer().getDeltaMovement().y;
        return ySpeed < -0.7;
    }
}
