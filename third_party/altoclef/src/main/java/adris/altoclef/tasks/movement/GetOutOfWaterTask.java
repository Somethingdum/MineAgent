package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.pathing.goals.Goal;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.MovementHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;

public class GetOutOfWaterTask extends CustomBaritoneGoalTask{

    private boolean startedShimmying = false;
    private final TimerGame shimmyTaskTimer = new TimerGame(5);

    @Override
    protected void onStart() {

    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        // get on the surface first
        if (mod.getPlayer().getAirSupply() < mod.getPlayer().getMaxAirSupply() || mod.getPlayer().isUnderWater()) {
            return super.onTick();
        }

        boolean hasBlockBelow = false;
        for (int i = 0; i < 3; i++) {
            if (mod.getWorld().getBlockState(mod.getPlayer().getOnPos().below(i)).getBlock() != Blocks.WATER) {
                hasBlockBelow = true;
            }
        }
        boolean hasAirAbove = mod.getWorld().getBlockState(mod.getPlayer().blockPosition().above(2)).getBlock().equals(Blocks.AIR);

        if (hasAirAbove && hasBlockBelow && StorageHelper.getNumberOfThrowawayBlocks(mod) > 0) {
            mod.getInputControls().tryPress(Input.JUMP);
            if (mod.getPlayer().onGround()) {

                if (!startedShimmying) {
                    startedShimmying = true;
                    shimmyTaskTimer.reset();
                }
                return new SafeRandomShimmyTask();
            }

            mod.getSlotHandler().forceEquipItem(mod.getClientBaritoneSettings().acceptableThrowawayItems.value.toArray(new Item[0]));
            LookHelper.lookAt(mod, mod.getPlayer().getOnPos().below());
            mod.getInputControls().tryPress(Input.CLICK_RIGHT);
        }

        return super.onTick();
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    @Override
    protected Goal newGoal(AltoClef mod) {
        return new EscapeFromWaterGoal();
    }

    @Override
    protected boolean isEqual(Task other) {
        return false;
    }

    @Override
    protected String toDebugString() {
        return "";
    }

    @Override
    public boolean isFinished() {
        return !AltoClef.getInstance().getPlayer().isInWater() && AltoClef.getInstance().getPlayer().onGround();
    }

    private static class EscapeFromWaterGoal implements Goal {

        private static boolean isWater(int x, int y, int z) {
            if (Minecraft.getInstance().level == null) return false;
            return MovementHelper.isWater(Minecraft.getInstance().level.getBlockState(new BlockPos(x, y, z)));
        }

        private static boolean isWaterAdjacent(int x, int y, int z) {
            return isWater(x + 1, y, z) || isWater(x - 1, y, z) || isWater(x, y, z + 1) || isWater(x, y, z - 1)
                    || isWater(x + 1, y, z - 1) || isWater(x + 1, y, z + 1) || isWater(x - 1, y, z - 1)
                    || isWater(x - 1, y, z + 1);
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            return !isWater(x, y, z) && !isWaterAdjacent(x, y, z);
        }

        @Override
        public double heuristic(int x, int y, int z) {
            if (isWater(x, y, z)) {
                return 1;
            } else if (isWaterAdjacent(x, y, z)) {
                return 0.5f;
            }

            return 0;
        }
    }
}
