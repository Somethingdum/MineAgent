package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.GetToXZTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonLandingApproachPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonLandingPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class KillEnderDragonWithBedsTask extends Task {
    private final WaitForDragonAndPearlTask whenNotPerchingTask;
    TimerGame placeBedTimer = new TimerGame(0.6);
    TimerGame waiTimer = new TimerGame(0.3);
    TimerGame waitBeforePlaceTimer = new TimerGame(0.5);
    boolean waited = false;
    double prevDist = 100;
    private BlockPos endPortalTop;
    private Task freePortalTopTask = null;
    private Task placeObsidianTask = null;
    private boolean dragonDead = false;

    public KillEnderDragonWithBedsTask() {
        whenNotPerchingTask = new WaitForDragonAndPearlTask();
    }

    public static BlockPos locateExitPortalTop(AltoClef mod) {
        if (!mod.getChunkTracker().isChunkLoaded(new BlockPos(0, 64, 0))) return null;
        int height = WorldHelper.getGroundHeight(0, 0, Blocks.BEDROCK);
        if (height != -1) return new BlockPos(0, height, 0);
        return null;
    }

    @Override
    protected void onStart() {
        // do not block our view
        AltoClef.getInstance().getBehaviour().avoidBlockPlacing((pos) -> pos.getZ() == 0 && Math.abs(pos.getX()) < 5);
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        /*
            If dragon is perching:
                If we're not in position (XZ):
                    Get in position (XZ)
                If there's no bed:
                    If we can't "reach" the top of the pillar:
                        Jump
                    Place a bed
                If the dragon's head hitbox is close enough to the bed:
                    Right click the bed
            Else:
                // Perform "Default Wander" mode and avoid dragon breath.
         */
        if (endPortalTop == null) {
            endPortalTop = locateExitPortalTop(mod);
            if (endPortalTop != null) {
                whenNotPerchingTask.setExitPortalTop(endPortalTop);
            }
        }

        if (endPortalTop == null) {
            setDebugState("Searching for end portal top.");
            return new GetToXZTask(0, 0);
        }

        BlockPos obsidianTarget = endPortalTop.above().relative(Direction.NORTH);
        if (!mod.getWorld().getBlockState(obsidianTarget).getBlock().equals(Blocks.OBSIDIAN)) {
            if (WorldHelper.inRangeXZ(mod.getPlayer().position(), new Vec3(0, 0, 0), 10)) {
                if (placeObsidianTask == null) {
                    placeObsidianTask = new PlaceBlockTask(obsidianTarget, Blocks.OBSIDIAN);
                }
                return placeObsidianTask;
            } else {
                return new GetToXZTask(0, 0);
            }
        }
        BlockState stateAtPortal = mod.getWorld().getBlockState(endPortalTop.above());
        if (!stateAtPortal.isAir() && !stateAtPortal.getBlock().equals(Blocks.FIRE) &&
                !Arrays.stream(ItemHelper.itemsToBlocks(ItemHelper.BED)).toList().contains(stateAtPortal.getBlock())) {

            if (freePortalTopTask == null) {
                freePortalTopTask = new DestroyBlockTask(endPortalTop.above());
            }
            return freePortalTopTask;
        }


        if (dragonDead) {
            setDebugState("Waiting for overworld portal to spawn.");
            return new GetToBlockTask(endPortalTop.below(4).west());
        }

        if (!mod.getEntityTracker().entityFound(EnderDragon.class) || dragonDead) {
            setDebugState("No dragon found.");

            if (!WorldHelper.inRangeXZ(mod.getPlayer(), endPortalTop, 1)) {
                setDebugState("Going to end portal top at" + endPortalTop.toString() + ".");
                return new GetToBlockTask(endPortalTop);
            }
        }
        List<EnderDragon> dragons = mod.getEntityTracker().getTrackedEntities(EnderDragon.class);
        for (EnderDragon dragon : dragons) {
            DragonPhaseInstance dragonPhase = dragon.getPhaseManager().getCurrentPhase();

            if (dragonPhase.getPhase() == EnderDragonPhase.DYING) {
                Debug.logMessage("Dragon is dead.");
                if (mod.getPlayer().getXRot() != -90) {
                    mod.getPlayer().setXRot(-90);
                }
                dragonDead = true;
                return null;
            }

            boolean perching = dragonPhase instanceof DragonLandingPhase || dragonPhase instanceof DragonLandingApproachPhase || dragonPhase.isSitting();
            if (dragon.getY() < endPortalTop.getY() + 2) {
                // Dragon is already perched.
                perching = false;
            }

            whenNotPerchingTask.setPerchState(perching);
            // When the dragon is not perching...
            if (whenNotPerchingTask.isActive() && !whenNotPerchingTask.isFinished()) {
                setDebugState("Dragon not perching, performing special behavior...");
                return whenNotPerchingTask;
            }
            if (perching) {
                return performOneCycle(mod, dragon);
            }
        }
        mod.getFoodChain().shouldStop(false);
        // Start our "Not perching task"
        return whenNotPerchingTask;
    }

    private Task performOneCycle(AltoClef mod, EnderDragon dragon) {
        mod.getFoodChain().shouldStop(true);
        if (mod.getInputControls().isHeldDown(Input.SNEAK)) {
            mod.getInputControls().release(Input.SNEAK);
        }
        // do not let shield fuck up our moment :3
        mod.getSlotHandler().forceEquipItemToOffhand(Items.AIR);

        BlockPos endPortalTop = KillEnderDragonWithBedsTask.locateExitPortalTop(mod).above();
        BlockPos obsidian = null;
        Direction dir = null;

        for (Direction direction : new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
            if (mod.getWorld().getBlockState(endPortalTop.relative(direction)).getBlock().equals(Blocks.OBSIDIAN)) {
                obsidian = endPortalTop.relative(direction);
                dir = direction.getOpposite();
                break;
            }
        }

        if (dir == null) {
            mod.log("no obisidan? :(");
            return null;
        }

        Direction offsetDir = dir.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.WEST;
        BlockPos targetBlock = endPortalTop.below(3).relative(offsetDir, 3).relative(dir);

        double d = distanceIgnoreY(WorldHelper.toVec3d(targetBlock), mod.getPlayer().position());
        if (d > 0.7 || mod.getPlayer().blockPosition().below().getY() > endPortalTop.getY() - 4) {
            mod.log(d + "");
            return new GetToBlockTask(targetBlock);
        } else if (!waited) {
            waited = true;
            waitBeforePlaceTimer.reset();
        }
        if (!waitBeforePlaceTimer.elapsed()) {
            mod.log(waitBeforePlaceTimer.getDuration() + " waiting...");
            return null;
        }

        LookHelper.lookAt(mod, obsidian, dir);

        BlockPos bedHead = WorldHelper.getBedHead(endPortalTop);
        mod.getSlotHandler().forceEquipItem(ItemHelper.BED);

        if (bedHead == null) {
            if (placeBedTimer.elapsed() && Math.abs(dragon.getY() - endPortalTop.getY()) < 10) {
                mod.getInputControls().tryPress(Input.CLICK_RIGHT);
                waiTimer.reset();
            }
            return null;
        }
        if (!waiTimer.elapsed()) {
            return null;
        }

        // most of these numbers were arbitrarily added through some testing, its possible not all of these cases need to be tested
        // it seems to work fairly well tho, so I would rather not touch it :p
        Vec3 dragonHeadPos = dragon.head.getBoundingBox().getCenter();
        Vec3 bedHeadPos = WorldHelper.toVec3d(bedHead);

        double dist = dragonHeadPos.distanceTo(bedHeadPos);
        double distXZ = distanceIgnoreY(dragonHeadPos, bedHeadPos);

        EnderDragonPart body = dragon.getSubEntities()[2];

        double destroyDistance = Math.abs(body.getBoundingBox().min(Direction.Axis.Y) - bedHeadPos.y());
        boolean tooClose = destroyDistance < 1.1;
        boolean skip = destroyDistance > 3 && dist > 4.5 && distXZ > 2.5;

        mod.log(destroyDistance + " : " + dist + " : " + distXZ);

        if ((dist < 1.5 || (prevDist < distXZ && destroyDistance < 4 && prevDist < 2.9)) || (destroyDistance < 2 && dist < 4)
                || (destroyDistance < 1.7 && dist < 4.5) || tooClose || (destroyDistance < 2.4 && distXZ < 3.7) || (destroyDistance < 3.5 && distXZ < 2.4)) {

            if (!skip) {
                mod.getInputControls().tryPress(Input.CLICK_RIGHT);
                placeBedTimer.reset();
            }
        }

        prevDist = distXZ;
        return null;
    }

    public double distanceIgnoreY(Vec3 vec, Vec3 vec1) {
        double d = vec.x - vec1.x;
        double f = vec.z - vec1.z;
        return Math.sqrt(d * d + f * f);
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef.getInstance().getFoodChain().shouldStop(false);
    }

    @Override
    public boolean isFinished() {
        return super.isFinished();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof KillEnderDragonWithBedsTask;
    }

    @Override
    protected String toDebugString() {
        return "Bedding the Ender Dragon";
    }
}