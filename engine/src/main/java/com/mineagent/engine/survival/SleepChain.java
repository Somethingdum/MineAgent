package com.mineagent.engine.survival;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.TaskChain;
import com.mineagent.engine.MineAgentEngine;
import com.mineagent.engine.act.Interaction;
import com.mineagent.engine.act.Placement;
import com.mineagent.engine.entity.CompanionEntity;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.task.BlockDigger;
import com.mineagent.engine.task.TaskContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Sleeping like a player (SMH plan 70, MA5.5). In 1.21.1 the night passes only when enough players in the Overworld are
 * asleep (the {@code playersSleepingPercentage} gamerule, 100% by default), and the companion is one of them. So when a
 * player in its world lies down at night, sleeping is its first job after staying alive: its own bed, a free bed near
 * by, or the bed it carries, placed beside it and picked up in the morning. When the game won't let it sleep it says
 * why, once a night. It gets up when nobody else is still in bed. The game itself decides when the night passes.
 */
public final class SleepChain implements TaskChain {
    /** Above work the model gave (5.0), below a fight that matters (5.5 and up). */
    static final float PRIORITY = 5.2f;
    /** Picking up the bed it put down, when nothing else wants the body. */
    static final float PICK_UP_PRIORITY = 1.5f;
    /** How far it looks for a free bed. */
    static final int SEARCH_RADIUS = 24;
    /** How often it looks again, in ticks. */
    static final int SEARCH_EVERY = 100;
    /** After a walk to the bed ends short of it, how long before it tries again, in ticks. */
    static final int RETRY_WALK_AFTER = 40;
    /** Closed in at home: a bed this near is in there with it. */
    static final int HOME_REACH = 4;

    private final AgentPlayer companion;
    private final CompanionBodyLog bodyLog;
    private PlayerNav nav;
    private ServerLevel navLevel;
    private BlockPos bed;
    private long lastSearch = Long.MIN_VALUE;
    private long toldNight = Long.MIN_VALUE;
    /** A bed it placed itself, to pick up again in the morning. */
    private BlockPos placedBed;
    /** Where the walk under way is going, and when it started. */
    private BlockPos walkingTo;
    private long walkStarted = Long.MIN_VALUE;

    public SleepChain(AgentPlayer companion) {
        this.companion = companion;
        this.bodyLog = SurvivalBuiltin.bodyLog(companion);
    }

    @Override
    public String name() {
        return "sleep";
    }

    @Override
    public float getPriority(AgentPlayer companion) {
        try {
            ServerPlayer sp = ((CompanionEntity) companion).serverPlayer();
            if (sp == null || !sp.isAlive()) return Float.NEGATIVE_INFINITY;
            if (sp.isSleeping()) return PRIORITY;
            if (com.mineagent.engine.settings.Switches.sleepWithPlayers() && wanted(sp)) return PRIORITY;
            if (alone(sp, companion) && bedToHand(sp)) return PRIORITY; // with no bed, the night shelter is its job
            if (placedBed != null && sp.serverLevel().isDay() && placedBed.closerThan(sp.blockPosition(), 16)) {
                return PICK_UP_PRIORITY;
            }
            return Float.NEGATIVE_INFINITY;
        } catch (Exception error) {
            return Float.NEGATIVE_INFINITY;
        }
    }

    /**
     * Night, its owner away, nothing asked of it (MA8.3): it sleeps every night it can, so phantoms don't come for it
     * and its spawn is its bed. Not while it works on an order: underground work at night goes on while nobody waits.
     */
    static boolean alone(ServerPlayer sp, AgentPlayer companion) {
        if (!com.mineagent.engine.settings.Switches.sleepWhenAway()) return false;
        ServerLevel level = sp.serverLevel();
        if (!level.dimensionType().bedWorks() || level.isDay()) return false;
        var loop = com.mineagent.engine.task.TaskContext.agentLoop(companion);
        if (loop == null) return false;
        if (level.getDayTime() % 24_000L < 12_542L) return false; // beds take it from dusk
        // On its way home for the night it sleeps at home, not out on the way; closed in there, it lies down (its
        // owner: "A bed if possible").
        if (com.mineagent.engine.task.ShelterTask.goingHome(sp.getUUID())) return false;
        if (com.mineagent.engine.task.ShelterTask.sealedAtHome(sp.getUUID())) return true;
        // On its own, always at some job of its own: two days without lying down and phantoms come every night, so it
        // lies down then, busy or not (that is rest enough for the game), and is back up at its work (SMH plan 70: runs
        // for weeks).
        if (loop.onItsOwn() && restless(sp)) return true;
        return loop.ownerAfk() && !loop.busyWithOrder();
    }

    /** Two game days or more since it last lay down (the game's own count, the one phantoms come by). */
    public static boolean restless(ServerPlayer sp) {
        try {
            return sp.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_REST)) >= 48_000;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A bed in its pack, or a free one near (looked for now and then, not every tick). */
    private boolean bedToHand(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).getItem() instanceof BedItem) return true;
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        if (bed != null && freeBed(level, bed)) return true;
        if (now - lastSearch > SEARCH_EVERY) {
            lastSearch = now;
            bed = findBed(sp);
        }
        return bed != null;
    }

    /** Someone else in its world is in bed at a time beds work, so the night waits on it. */
    static boolean wanted(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        if (!level.dimensionType().bedWorks()) return false; // beds explode in the Nether and the End
        if (level.isDay() && !level.isThundering()) return false;
        for (ServerPlayer other : level.players()) {
            if (other != sp && !other.isSpectator() && other.isSleeping()
                    && !MineAgentEngine.isCompanionPlayer(other.getUUID())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick(AgentPlayer companion) {
        ServerPlayer sp = ((CompanionEntity) companion).serverPlayer();
        try {
            boolean want = com.mineagent.engine.settings.Switches.sleepWithPlayers() && wanted(sp) || alone(sp, companion) && bedToHand(sp);
            if (sp.isSleeping()) {
                if (!want) sp.stopSleepInBed(true, true); // everyone else is up (or the switch is off): it gets up too
                return;
            }
            if (!want) {
                pickUpPlacedBed(sp);
                return;
            }
            ServerLevel level = sp.serverLevel();
            long now = level.getGameTime();
            boolean home = com.mineagent.engine.task.ShelterTask.sealedAtHome(sp.getUUID());
            if (home && bed != null && !bed.closerThan(sp.blockPosition(), HOME_REACH)) bed = null;
            if (bed == null || !freeBed(level, bed) || now - lastSearch > SEARCH_EVERY) {
                lastSearch = now;
                // Closed in at home, only a bed in there with it: one outside is past its own walls.
                bed = home ? nearestBed(sp, HOME_REACH) : findBed(sp);
                if (bed == null) bed = home ? putBedDown(sp) : placeCarriedBed(sp);
                if (bed == null) {
                    tellOnce(level, "No bed for me to sleep in, so the night can't pass yet.");
                    return;
                }
            }
            if (!inBedReach(sp, bed)) {
                walkTo(bed, now);
                return;
            }
            stopWalking();
            TaskContext.inputDriver(companion).clear();
            if (monstersNear(level, sp, bed)) {
                tellOnce(level, "Monsters near my bed; dealing with them first.");
                return;
            }
            sp.setShiftKeyDown(false);
            Interaction.interactBlock(sp, bed, InteractionHand.MAIN_HAND);
            if (sp.isSleeping()) {
                bodyLog.report("went to bed so the night can pass");
            } else {
                tellOnce(level, "I can't get into my bed here.");
                bed = null;
            }
        } catch (Exception error) {
            System.err.println("[MineAgent] Sleep tick error: " + error.getMessage());
        }
    }

    @Override
    public void onInterrupt(AgentPlayer companion) {
        stopWalking();
    }

    /**
     * Walk on towards a place. The goal is set once, not every tick: setting it again starts the search over, and a
     * walk restarted every tick never takes a step. A walk that ended short of it is tried again after a pause.
     */
    private void walkTo(BlockPos target, long now) {
        PlayerNav n = nav();
        boolean fresh = !target.equals(walkingTo);
        if (fresh || (!n.isNavigating() && now - walkStarted >= RETRY_WALK_AFTER)) {
            n.navigateNear(target.getX(), target.getY(), target.getZ(), 1);
            walkingTo = target.immutable();
            walkStarted = now;
        }
        n.tick();
    }

    private void stopWalking() {
        if (nav != null) nav.cancel();
        walkingTo = null;
    }

    private PlayerNav nav() {
        ServerPlayer sp = ((CompanionEntity) companion).serverPlayer();
        if (nav == null || navLevel != sp.serverLevel()) {
            nav = new PlayerNav(companion, TaskContext.navCaches(companion));
            navLevel = sp.serverLevel();
        }
        return nav;
    }

    private void tellOnce(ServerLevel level, String line) {
        long night = level.getDayTime() / 24000L;
        if (night == toldNight) return;
        toldNight = night;
        bodyLog.report(line);
        var loop = TaskContext.agentLoop(companion);
        if (loop != null) loop.say(line);
    }

    /** Its spawn bed if that's free and in this world, else the nearest free bed within reach of a walk. */
    private static BlockPos findBed(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        BlockPos spawn = sp.getRespawnPosition();
        if (spawn != null && sp.getRespawnDimension() == level.dimension() && freeBed(level, spawn)) {
            return spawn;
        }
        BlockPos at = sp.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    cursor.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (!level.isLoaded(cursor) || !freeBed(level, cursor)) continue;
                    double d = cursor.distSqr(at);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
    }

    /** A bed's foot or head, nobody in it. */
    static boolean freeBed(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof BedBlock && !state.getValue(BedBlock.OCCUPIED);
    }

    /** The game's own reach for a bed: 3 blocks across, 2 up or down, from the bed's bottom centre. */
    private static boolean inBedReach(ServerPlayer sp, BlockPos bed) {
        return Math.abs(sp.getX() - (bed.getX() + 0.5)) <= 3.0
                && Math.abs(sp.getY() - bed.getY()) <= 2.0
                && Math.abs(sp.getZ() - (bed.getZ() + 0.5)) <= 3.0;
    }

    /** The game's own rule: monsters within 8 blocks across and 5 up or down of the bed keep a player awake. */
    private static boolean monstersNear(ServerLevel level, ServerPlayer sp, BlockPos bed) {
        return !level.getEntitiesOfClass(Monster.class, new AABB(bed).inflate(8.0, 5.0, 8.0),
                m -> m.isPreventingPlayerRest(sp)).isEmpty();
    }

    /** Put down the bed it carries, beside it, on firm ground with room for both halves; where it went, or null. */
    private BlockPos placeCarriedBed(ServerPlayer sp) {
        BlockPos put = putBedDown(sp);
        if (put != null) {
            placedBed = put;
            bodyLog.report("put my bed down for the night");
        }
        return put;
    }

    /** The nearest free bed within {@code reach} of it. */
    private static BlockPos nearestBed(ServerPlayer sp, int reach) {
        ServerLevel level = sp.serverLevel();
        BlockPos at = sp.blockPosition(), best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-reach, -2, -reach), at.offset(reach, 2, reach))) {
            if (!freeBed(level, p)) continue;
            double d = p.distSqr(at);
            if (d < bestDistance) {
                bestDistance = d;
                best = p.immutable();
            }
        }
        return best;
    }

    /**
     * The bed it carries, put down beside it (foot and head on open floor), or null. Its home's bed (put down in its hut
     * for the night) is left there: its bed at home, where it wakes after a death.
     */
    public static BlockPos putBedDown(ServerPlayer sp) {
        return putBedDown(sp, p -> true);
    }

    /** As {@link #putBedDown(ServerPlayer)}, both halves on cells {@code allowed} takes (in its hut, off its doorway). */
    public static BlockPos putBedDown(ServerPlayer sp, java.util.function.Predicate<BlockPos> allowed) {
        var inventory = sp.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).getItem() instanceof BedItem) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            for (int i = 9; i < 36; i++) {
                if (inventory.getItem(i).getItem() instanceof BedItem) {
                    ItemStack held = inventory.getItem(inventory.selected);
                    inventory.setItem(inventory.selected, inventory.getItem(i));
                    inventory.setItem(i, held);
                    TaskContext.syncInventory(sp);
                    slot = inventory.selected;
                    break;
                }
            }
        }
        if (slot < 0) return null;
        inventory.selected = slot;
        ServerLevel level = sp.serverLevel();
        BlockPos at = sp.blockPosition();
        // Straight out from it first, then any cell round it with the head along the floor any way (from the middle of
        // a hut three across, straight out runs into the wall).
        java.util.List<BlockPos[]> ways = new java.util.ArrayList<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) ways.add(new BlockPos[] {at.relative(dir), at.relative(dir).relative(dir)});
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos foot = at.offset(dx, 0, dz);
                for (Direction dir : Direction.Plane.HORIZONTAL) ways.add(new BlockPos[] {foot, foot.relative(dir)});
            }
        }
        for (BlockPos[] w : ways) {
            BlockPos foot = w[0], head = w[1];
            if (head.equals(at) || !allowed.test(foot) || !allowed.test(head) || !roomForBed(level, foot) || !roomForBed(level, head)) continue;
            Direction dir = head.getX() > foot.getX() ? Direction.EAST : head.getX() < foot.getX() ? Direction.WEST
                    : head.getZ() > foot.getZ() ? Direction.SOUTH : Direction.NORTH;
            sp.setYRot(dir.toYRot());
            var attempt = Placement.placeHeldBlock(sp, foot, InteractionHand.MAIN_HAND);
            BlockPos placed = level.getBlockState(foot).getBlock() instanceof BedBlock ? foot
                    : level.getBlockState(head).getBlock() instanceof BedBlock ? head : null;
            if (attempt.placed() && placed != null) return placed;
        }
        return null;
    }

    private static boolean roomForBed(ServerLevel level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());
        return (here.isAir() || here.canBeReplaced()) && level.getBlockState(pos.above()).isAir()
                && below.isFaceSturdy(level, pos.below(), Direction.UP);
    }

    /** In the morning, take back the bed it put down. */
    private void pickUpPlacedBed(ServerPlayer sp) {
        if (placedBed == null) return;
        ServerLevel level = sp.serverLevel();
        if (!level.isLoaded(placedBed) || !(level.getBlockState(placedBed).getBlock() instanceof BedBlock)) {
            placedBed = null; // gone already
            return;
        }
        if (!inBedReach(sp, placedBed)) {
            walkTo(placedBed, level.getGameTime());
            return;
        }
        stopWalking();
        if (BlockDigger.startBreaking(sp, placedBed)) {
            bodyLog.report("picked my bed back up");
            placedBed = null;
        }
    }
}
