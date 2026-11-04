package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.multiversion.entity.PlayerVer;
import adris.altoclef.multiversion.versionedfields.Blocks;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.movement.GetOutOfWaterTask;
import adris.altoclef.tasks.movement.SafeRandomShimmyTask;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import java.util.LinkedList;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class UnstuckChain extends SingleTaskChain {

    private final LinkedList<Vec3> posHistory = new LinkedList<>();
    private boolean isProbablyStuck = false;
    private int eatingTicks = 0;
    private boolean interruptedEating = false;
    private TimerGame shimmyTaskTimer = new TimerGame(5);
    private boolean startedShimmying = false;

    public UnstuckChain(TaskRunner runner) {
        super(runner);
    }


    private void checkStuckInWater() {
        if (posHistory.size() < 100) return;

        ClientLevel world = AltoClef.getInstance().getWorld();
        LocalPlayer player = AltoClef.getInstance().getPlayer();

        // is not in water
        if (!world.getBlockState(player.getOnPos()).getBlock().equals(Blocks.WATER)
                && !world.getBlockState(player.getOnPos().below()).getBlock().equals(Blocks.WATER))
            return;

        // everything should be fine
        if (player.onGround()) {
            posHistory.clear();
            return;
        }

        // do NOT do anything if underwater
        if (player.getAirSupply() < player.getMaxAirSupply()) {
            return;
        }

        Vec3 pos1 = posHistory.get(0);
        for (int i = 1; i < 100; i++) {
            Vec3 pos2 = posHistory.get(i);
            if (Math.abs(pos1.x() - pos2.x()) > 0.75 || Math.abs(pos1.z() - pos2.z()) > 0.75) {
                return;
            }
        }

        posHistory.clear();
        setTask(new GetOutOfWaterTask());
    }

    private void checkStuckInPowderedSnow() {
        AltoClef mod = AltoClef.getInstance();

        Player player = mod.getPlayer();
        ClientLevel world = mod.getWorld();

        if (PlayerVer.inPowderedSnow(player)) {
            isProbablyStuck = true;
            BlockPos destroyPos = null;

            Optional<BlockPos> nearest = mod.getBlockScanner().getNearestBlock(Blocks.POWDER_SNOW);
            if (nearest.isPresent()) {
                destroyPos = nearest.get();
            }

            BlockPos headPos = WorldHelper.toBlockPos(player.getEyePosition()).below();
            if (world.getBlockState(headPos).getBlock() == Blocks.POWDER_SNOW) {
                destroyPos = headPos;
            } else if (world.getBlockState(player.blockPosition()).getBlock() == Blocks.POWDER_SNOW) {
                destroyPos = player.blockPosition();
            }

            if (destroyPos != null) {
                setTask(new DestroyBlockTask(destroyPos));
            }
        }
    }

    private void checkStuckOnEndPortalFrame(AltoClef mod) {
        BlockState state = mod.getWorld().getBlockState(mod.getPlayer().getOnPos());

        // if we are standing on an end portal frame that is NOT filled, get off otherwise we will get stuck
        if (state.getBlock() == Blocks.END_PORTAL_FRAME && !state.getValue(EndPortalFrameBlock.HAS_EYE)) {
            if (!mod.getFoodChain().isTryingToEat()) {
                isProbablyStuck = true;

                // for now let's just hope the other mechanisms will take care of cases where moving forward will get us in danger
                mod.getInputControls().tryPress(Input.MOVE_FORWARD);
            }
        }
    }

    private void checkEatingGlitch() {
        FoodChain foodChain = AltoClef.getInstance().getFoodChain();

        if (interruptedEating) {
            foodChain.shouldStop(false);
            interruptedEating = false;
        }

        if (foodChain.isTryingToEat()) {
            eatingTicks++;
        } else {
            eatingTicks = 0;
        }

        if (eatingTicks > 7*20) {
            Debug.logMessage("the bot is probably stuck trying to eat... resetting action");
            foodChain.shouldStop(true);

            eatingTicks = 0;
            interruptedEating = true;
            isProbablyStuck = true;
        }
    }

    @Override
    public float getPriority() {
        if (mainTask instanceof GetOutOfWaterTask && mainTask.isActive()) {
            return 55;
        }

        isProbablyStuck = false;

        AltoClef mod = AltoClef.getInstance();

        if (!AltoClef.inGame() || Minecraft.getInstance().isPaused() || !mod.getUserTaskChain().isActive())
            return Float.NEGATIVE_INFINITY;

        if (StorageHelper.isBlastFurnaceOpen() || StorageHelper.isSmokerOpen() || StorageHelper.isChestOpen() || StorageHelper.isBigCraftingOpen()) {
            return Float.NEGATIVE_INFINITY;
        }

        Player player = mod.getPlayer();
        posHistory.addFirst(player.position());
        if (posHistory.size() > 500) {
            posHistory.removeLast();
        }

        checkStuckInWater();
        checkStuckInPowderedSnow();
        checkEatingGlitch();
        checkStuckOnEndPortalFrame(mod);


        if (isProbablyStuck) {
            return 55;
        }

        if (startedShimmying && !shimmyTaskTimer.elapsed()) {
            setTask(new SafeRandomShimmyTask());
            return 55;
        }
        startedShimmying = false;

        return Float.NEGATIVE_INFINITY;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    protected void onTaskFinish(AltoClef mod) {

    }

    @Override
    public String getName() {
        return "Unstuck Chain";
    }
}
