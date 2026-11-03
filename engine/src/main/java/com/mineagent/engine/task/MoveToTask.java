package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskState;
import com.mineagent.api.task.TaskSnapshot;
import com.mineagent.engine.pathing.cache.PathCaches;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.planning.IntentAwareTask;
import com.mineagent.engine.planning.IntentContract;
import com.mineagent.tools.movement.MoveToTool;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Executes a movement task - navigates the companion to a target position
 * using A* pathfinding. Supports four goal modes:
 * <ul>
 *   <li>xz - walk to horizontal coordinates at current Y</li>
 *   <li>xzy - walk to exact block position</li>
 *   <li>y - climb/descend to altitude</li>
 *   <li>block - walk adjacent to a block</li>
 * </ul>
 *
 * <p><b>Progress Lease</b> (borrowed from numen):
 * Uses {@link TaskLease} instead of a single hard deadline. The lease
 * renews when the companion makes progress (moves &gt; 1 block), so
 * healthy long journeys don't time out. The lease stops renewing after
 * a grace period without progress, catching genuinely stuck tasks.
 */
public class MoveToTask extends CompanionTask<MoveToTool.MoveToTaskRecord>
        implements IntentAwareTask {

    private PlayerNav nav;
    private volatile boolean goalReached;
    private volatile boolean navFailed;
    private String failReason;

    /** Progress lease — replaces the single hard deadline. */
    private final TaskLease lease = new TaskLease();

    /** Last recorded position — used to detect progress. */
    private Vec3 lastProgressPos = null;

    /** How near a waypoint counts as passing it. */
    static final int LEG_RADIUS = 2;
    /** Waypoints it may give up on (a block in the way since) before the trip fails. */
    static final int MAX_SKIPS = 3;
    /** The waypoint being walked to (SMH plan 70, MA5.6); -1 once on the way to the goal itself. */
    private int leg = -1;
    private int skipped;
    /** The waypoints: the record's, or for a long trip the coarse plan's (MA5.10). */
    private java.util.List<int[]> legs = java.util.List.of();
    /** Coarse legs are reached anywhere within {@link #COARSE_RADIUS} across, at any height. */
    private boolean coarseLegs;
    /** Set on the first start: a resume after a survival chain carries on from the leg it was on. */
    private boolean begun;
    /** A trip this long, with no waypoints given, is planned over chunks first. */
    static final int LONG_TRIP = 192;
    static final int COARSE_RADIUS = 8;

    public MoveToTask(AgentPlayer player, MoveToTool.MoveToTaskRecord record) {
        super(player, record);
    }

    @Override
    public void onStart() {
        PathCaches caches = TaskContext.navCaches(player);
        goalReached = false;
        navFailed = false;
        failReason = null;
        nav = new PlayerNav(player, caches, intentContract().terrainPolicy());

        nav.setListener(new PlayerNav.NavListener() {
            @Override
            public void onGoalReached() {
                goalReached = true;
            }

            @Override
            public void onNavigationFailed(String reason) {
                navFailed = true;
                failReason = reason;
            }
        });

        if (!begun) {
            begun = true;
            legs = record.waypoints;
            coarseLegs = false;
            if (legs.isEmpty()) planLongTrip();
            leg = legs.isEmpty() ? -1 : 0;
            skipped = 0;
            allowTimeFor(TaskContext.serverPlayer(player));
        }
        startLegOrGoal();

        // Start the progress lease
        ServerPlayer sp = TaskContext.serverPlayer(player);
        long gameTime = sp.level().getGameTime();
        lease.start(gameTime);
        lastProgressPos = sp.position();
    }

    /**
     * A long trip with no waypoints given: legs from the chunks it has seen (MA5.10), so the pathfinder plans each
     * leg on its own and the trip goes round the lakes and lava it knows of.
     */
    private void planLongTrip() {
        if (!com.mineagent.engine.settings.Switches.longTrips()) return;
        if (!"xz".equals(record.goalMode) && !"xzy".equals(record.goalMode) && !"block".equals(record.goalMode)) return;
        ServerPlayer sp = TaskContext.serverPlayer(player);
        int tz = record.z != null ? record.z : sp.blockPosition().getZ();
        double dx = record.x - sp.getX(), dz = tz - sp.getZ();
        if (dx * dx + dz * dz < (double) LONG_TRIP * LONG_TRIP) return;
        var loop = TaskContext.agentLoop(player);
        var chunks = loop == null ? new com.mineagent.engine.world.ChunkSummaries() : loop.placeKeeper().chunks();
        var way = new java.util.ArrayList<>(com.mineagent.engine.pathing.util.CoarsePlanner.plan(chunks,
                sp.serverLevel().dimension().location().toString(), sp.getBlockX(), sp.getBlockY(), sp.getBlockZ(),
                record.x, tz));
        // The last one is the goal's own chunk: the goal itself is walked to after.
        if (!way.isEmpty()) way.remove(way.size() - 1);
        legs = way;
        coarseLegs = !way.isEmpty();
    }

    /**
     * A long trip, or one by waypoints, gets the time its length needs (MA5.10): at a slow 2 blocks a second, twice
     * over, never less than the 5 minutes every task has.
     */
    private void allowTimeFor(ServerPlayer sp) {
        double length = 0;
        double px = sp.getX(), py = sp.getY(), pz = sp.getZ();
        for (int[] p : legs) {
            length += Math.sqrt(Math.pow(p[0] - px, 2) + Math.pow(p[1] - py, 2) + Math.pow(p[2] - pz, 2));
            px = p[0];
            py = p[1];
            pz = p[2];
        }
        int tz = record.z != null ? record.z : (int) pz;
        length += Math.sqrt(Math.pow(record.x - px, 2) + Math.pow(tz - pz, 2));
        long need = (long) (length / 2.0 * 20.0 * 2.0);
        long now = sp.level().getGameTime();
        if (record.deadline() > 0L && now + need > record.deadline()) record.extendDeadlineTo(now + need);
    }

    /** The next waypoint not already passed, or the goal itself once they're all passed. */
    private void startLegOrGoal() {
        ServerPlayer sp0 = TaskContext.serverPlayer(player);
        while (leg >= 0 && leg < legs.size()) {
            int[] p = legs.get(leg);
            double dx = sp0.getX() - (p[0] + 0.5), dy = sp0.getY() - p[1], dz = sp0.getZ() - (p[2] + 0.5);
            if (coarseLegs) {
                if (dx * dx + dz * dz > (COARSE_RADIUS + 1) * (COARSE_RADIUS + 1)) {
                    nav.navigateToGoal(new com.mineagent.engine.pathing.goals.GoalNearXZ(p[0], p[2], COARSE_RADIUS));
                    return;
                }
            } else if (dx * dx + dy * dy + dz * dz > (LEG_RADIUS + 1) * (LEG_RADIUS + 1)) {
                nav.navigateNear(p[0], p[1], p[2], LEG_RADIUS);
                return;
            }
            leg++;
        }
        leg = -1;
        startGoal(sp0);
    }

    private void startGoal(ServerPlayer sp0) {
        // Set goal based on mode
        // Use safe defaults for null y/z (shouldn't happen after MoveToTool
        // validation, but defensive against LLM-provided bad data)
        int safeY = record.y != null ? record.y : sp0.blockPosition().getY();
        int safeZ = record.z != null ? record.z : sp0.blockPosition().getZ();

        switch (record.goalMode) {
            case "xz" -> nav.navigateToXZ(record.x, safeZ);
            case "xzy" -> nav.navigateTo(record.x, safeY, safeZ);
            case "y" -> nav.navigateToYLevel(safeY);
            case "block" -> nav.navigateToBlock(record.x, safeY, safeZ);
            default -> {
                navFailed = true;
                failReason = "Invalid goal_mode: " + record.goalMode;
            }
        }
    }

    /** At a waypoint or the goal: on to the next, or done. False once the trip is over. */
    private boolean arrived() {
        if (record.stopInOpen && inOpen()) return false;
        if (leg < 0) return false;
        goalReached = false;
        leg++;
        startLegOrGoal();
        return true;
    }

    /** A waypoint it can't reach is passed over for the next, a few times a trip. */
    private boolean skipLeg() {
        if (leg < 0 || skipped >= MAX_SKIPS) return false;
        skipped++;
        navFailed = false;
        failReason = null;
        leg++;
        startLegOrGoal();
        return true;
    }

    private boolean inOpen() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        return !com.mineagent.engine.world.PlaceKeeper.underground(sp.serverLevel(), sp.blockPosition());
    }

    /** A failed walk, with why, in the log (counts and reasons, nothing of anyone's). */
    private TaskState failed() {
        System.out.println("[MineAgent] MoveTo failed" + (leg >= 0 ? " on leg " + (leg + 1) + " of " + legs.size() : "")
                + ": " + failReason);
        return TaskState.FAILED;
    }

    @Override
    public TaskState onTick() {
        if (navFailed && !skipLeg()) {
            // PathingCore already performs bounded replanning. Relaxing an
            // exact xzy/block goal to a three-block radius here reported
            // success at a position that did not satisfy the tool request.
            return failed();
        }
        if (goalReached && !arrived()) return TaskState.SUCCESS;

        // Tick the navigation
        nav.tick();

        // Re-check after tick (listener may have fired)
        if (goalReached && !arrived()) return TaskState.SUCCESS;
        if (navFailed && !skipLeg()) {
            return failed();
        }

        // Progress lease management
        ServerPlayer sp = TaskContext.serverPlayer(player);
        long gameTime = sp.level().getGameTime();

        // Vertical-only goals must renew while climbing as well as walking.
        Vec3 currentPos = sp.position();
        if (lastProgressPos != null && currentPos.distanceTo(lastProgressPos) > 1.0) {
            lease.onProgress(gameTime);
            lastProgressPos = currentPos;
        }

        // Tick the lease (checks grace period)
        lease.tick(gameTime);

        // Check if the lease has expired
        if (lease.isExpired(gameTime)) {
            String reason = lease.expirationReason(gameTime);
            System.out.println("[MineAgent] MoveTo lease expired: " + reason);
            nav.cancel();
            TaskContext.inputDriver(player).clear();
            failReason = "Navigation lease expired: " + reason;
            return TaskState.FAILED;
        }

        return TaskState.RUNNING;
    }

    @Override
    public void onInterrupt() {
        if (nav != null) {
            nav.cancel();
        }
        TaskContext.inputDriver(player).clear();
    }

    @Override
    public TaskSnapshot snapshot() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        int completed = 0;
        int total = -1;
        String stage = nav == null ? "initializing"
                : nav.state().name().toLowerCase(java.util.Locale.ROOT);
        StringBuilder evidence = new StringBuilder("player=")
                .append(sp.blockPosition().toShortString());
        long executorVersion = 0L;
        if (nav != null && nav.core().executor() != null) {
            var executor = nav.core().executor();
            completed = executor.currentMovementIndex();
            total = executor.path().length();
            executorVersion = executor.progressVersion();
            evidence.append(" movement=").append(completed).append('/').append(total)
                    .append(" movement_ticks=").append(executor.ticksOnCurrentMovement())
                    .append(" movement_stagnant_ticks=")
                    .append(executor.ticksWithoutProgress());
        }
        if (nav != null && nav.core().lastFailureDetail() != null) {
            evidence.append(" last_path_failure=").append(nav.core().lastFailureDetail());
        }
        Integer targetY = "xz".equals(record.goalMode) ? null : record.y;
        Integer targetZ = "y".equals(record.goalMode) ? null : record.z;
        Integer targetX = "y".equals(record.goalMode) ? null : record.x;
        long version = ((long) Math.max(0, completed) << 32)
                ^ executorVersion ^ (goalReached ? 2L : 0L) ^ (navFailed ? 1L : 0L);
        return TaskSnapshot.progress(stage,
                goalReached ? "Navigation goal reached"
                        : navFailed ? "Navigation failed" : "Navigating to requested goal",
                completed, total, targetX, targetY, targetZ,
                navFailed ? failReason : null,
                evidence.toString(), version);
    }

    @Override
    public IntentContract intentContract() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        int currentY = sp.blockPosition().getY();
        int targetY = record.y == null ? currentY : record.y;
        int horizontalDistance = record.z == null ? 0
                : Math.abs(record.x - sp.blockPosition().getX())
                + Math.abs(record.z - sp.blockPosition().getZ());
        int placementBudget = Math.max(4, Math.min(32, horizontalDistance / 8 + 4));
        IntentContract.TerrainPolicy policy = new IntentContract.TerrainPolicy(
                true, true, true, true, placementBudget, 16,
                Math.max(4, targetY - currentY + 4),
                IntentContract.CleanupMode.CONTEXTUAL);
        return new IntentContract("Reach the requested navigation goal",
                "The executor verifies the exact selected goal predicate",
                "y".equals(record.goalMode) ? null : record.x,
                "xz".equals(record.goalMode) ? null : record.y,
                "y".equals(record.goalMode) ? null : record.z,
                policy, java.util.List.of(
                new IntentContract.Constraint("goal_exactness",
                        IntentContract.ConstraintKind.HARD,
                        "Do not report success unless the requested goal predicate is true",
                        "navigation")));
    }

    @Override
    protected String successMessage() {
        var pos = TaskContext.serverPlayer(player).blockPosition();
        return "Arrived at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    @Override
    protected String timeoutMessage() {
        var pos = TaskContext.serverPlayer(player).blockPosition();
        long gameTime = TaskContext.serverPlayer(player).level().getGameTime();
        String reason = lease.expirationReason(gameTime);
        return "Navigation timed out at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + ") heading for mode=" + record.goalMode
                + " target=(" + record.x + ", " + (record.y != null ? record.y : "null")
                + ", " + (record.z != null ? record.z : "null") + ")"
                + " reason=" + reason;
    }

    @Override
    protected String failureMessage() {
        String yStr = record.y != null ? record.y.toString() : "null";
        String zStr = record.z != null ? record.z.toString() : "null";
        if (failReason != null) {
            return "Navigation failed: " + failReason
                    + " (mode=" + record.goalMode
                    + " target=(" + record.x + ", " + yStr + ", " + zStr + "))";
        }
        return "Navigation failed (mode=" + record.goalMode
                + " target=(" + record.x + ", " + yStr + ", " + zStr + ")";
    }
}
