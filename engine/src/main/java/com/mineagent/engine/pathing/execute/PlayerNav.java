package com.mineagent.engine.pathing.execute;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.engine.pathing.astar.PathBase;
import com.mineagent.engine.pathing.astar.PathCalcResult;
import com.mineagent.engine.pathing.cache.PathCaches;
import com.mineagent.engine.pathing.goals.*;
import com.mineagent.engine.task.TaskContext;
import com.mineagent.engine.planning.IntentContract;

/**
 * High-level navigation manager. Provides a simple API for navigating
 * the companion to various targets, managing the underlying PathingCore
 * state machine.
 *
 * <p>Usage:
 * <pre>
 *   PlayerNav nav = new PlayerNav(player, caches);
 *   nav.navigateTo(100, 64, 200);  // Go to block
 *   nav.navigateToXZ(100, 200);    // Go to XZ
 *   nav.tick();                     // Call every tick
 * </pre>
 */
public class PlayerNav {

    private PathingCore core;
    private final AgentPlayer player;
    private net.minecraft.server.level.ServerLevel coreLevel;
    private final IntentContract.TerrainPolicy terrainPolicy;

    /** Listener for path events. */
    public interface NavListener {
        /** Called when navigation reaches the goal. */
        default void onGoalReached() {}
        /** Called when navigation fails. */
        default void onNavigationFailed(String reason) {}
        /** Called when a path is calculated. */
        default void onPathCalculated(PathCalcResult result) {}
    }

    private NavListener listener;
    private long lastDebugPushTick = Long.MIN_VALUE;

    public PlayerNav(AgentPlayer player, PathCaches caches) {
        this(player, caches, IntentContract.TerrainPolicy.CONSERVATIVE);
    }

    public PlayerNav(AgentPlayer player, PathCaches caches,
                     IntentContract.TerrainPolicy terrainPolicy) {
        this.player = player;
        this.terrainPolicy = terrainPolicy == null
                ? IntentContract.TerrainPolicy.CONSERVATIVE : terrainPolicy;
        this.coreLevel = caches.level();
        this.core = new PathingCore(player, caches,
                com.mineagent.engine.MineAgentEngine.getConfig().pathfinding(),
                this.terrainPolicy);
    }

    /**
     * Navigate to an exact block position.
     */
    public void navigateTo(int x, int y, int z) {
        ensureCurrentLevel(false);
        core.setGoal(new GoalBlock(x, y, z));
    }

    /**
     * Navigate to be adjacent to a block.
     */
    public void navigateToBlock(int x, int y, int z) {
        ensureCurrentLevel(false);
        core.setGoal(new GoalGetToBlock(x, y, z));
    }

    /**
     * Navigate to a collision-safe cell for placing a solid block at target.
     * This is intentionally separate from navigateToBlock: mining may stand
     * directly below a target, while placement cannot put a block through the
     * fake player's body.
     */
    public void navigateForPlacement(int x, int y, int z) {
        ensureCurrentLevel(false);
        core.setGoal(new GoalPlaceBlock(x, y, z));
    }

    /**
     * Navigate to an XZ coordinate at any Y level.
     */
    public void navigateToXZ(int x, int z) {
        ensureCurrentLevel(false);
        core.setGoal(new GoalXZ(x, z));
    }

    /**
     * Navigate to a specific Y altitude.
     */
    public void navigateToYLevel(int y) {
        ensureCurrentLevel(false);
        core.setGoal(new GoalYLevel(y));
    }

    /**
     * Navigate to be within a radius of a position.
     */
    public void navigateNear(int x, int y, int z, int radius) {
        ensureCurrentLevel(false);
        core.setGoal(new GoalNear(x, y, z, radius));
    }

    /**
     * Run away from a position.
     */
    public void runAway(int x, int y, int z, double distance) {
        ensureCurrentLevel(false);
        core.setGoal(new GoalRunAway(x, y, z, distance));
    }

    /**
     * Navigate to a custom goal.
     */
    public void navigateToGoal(Goal goal) {
        ensureCurrentLevel(false);
        core.setGoal(goal);
    }

    /**
     * Cancel the current navigation.
     */
    public void cancel() {
        core.cancel();
    }

    /**
     * Tick the navigation system. Call this every server tick.
     */
    public void tick() {
        if (!ensureCurrentLevel(true)) return;
        openDoorwayItStandsIn();
        PathingCore.State prevState = core.state();
        core.tick();
        PathingCore.State newState = core.state();

        pushPathDebug(prevState != newState);

        // Detect state transitions
        if (prevState != newState && listener != null) {
            if (prevState != PathingCore.State.IDLE && newState == PathingCore.State.IDLE) {
                // Navigation ended
                if (core.currentGoal() != null) {
                    var pos = com.mineagent.engine.pathing.util.BlockHelper.bodyCell(TaskContext.serverPlayer(player));
                    if (core.currentGoal().isInGoal(pos.getX(), pos.getY(), pos.getZ())) {
                        listener.onGoalReached();
                    } else {
                        PathCalcResult result = core.lastResult();
                        String reason = core.lastFailureDetail() != null
                                ? core.lastFailureDetail()
                                : result != null && !result.foundPath()
                                ? result.failureReason().name()
                                : "EXECUTION_FAILED";
                        listener.onNavigationFailed(reason);
                    }
                }
            }

            if (newState == PathingCore.State.EXECUTE && core.lastResult() != null) {
                listener.onPathCalculated(core.lastResult());
            }
        }
    }

    /** Whether the navigation system is currently active. */
    public boolean isNavigating() {
        return core.isActive();
    }

    /** Return whether the player's current block position still satisfies the goal. */
    public boolean isAtGoal() {
        Goal goal = core.currentGoal();
        if (goal == null) return false;
        var pos = com.mineagent.engine.pathing.util.BlockHelper.bodyCell(TaskContext.serverPlayer(player));
        return goal.isInGoal(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Get the current navigation state. */
    public PathingCore.State state() {
        return core.state();
    }

    /** Set the navigation listener. */
    public void setListener(NavListener listener) {
        this.listener = listener;
    }

    /** Get the pathing core (for advanced usage). */
    public PathingCore core() {
        return core;
    }

    /** Get the companion player. */
    /**
     * Standing in a doorway with its door shut (it stepped into the door's block from the side the door isn't on): the
     * door opened, as a player would, before it walks; else every way out runs into the door and nowhere can be got to.
     * A door or a gate only: a trapdoor it stands on is a floor. Shut again behind it (MA5.3).
     */
    private void openDoorwayItStandsIn() {
        var sp = com.mineagent.engine.task.TaskContext.serverPlayer(player);
        if (sp == null) return;
        var level = sp.level();
        for (net.minecraft.core.BlockPos at : new net.minecraft.core.BlockPos[] {sp.blockPosition(), sp.blockPosition().above()}) {
            var st = level.getBlockState(at);
            if (!(st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
                    && !(st.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock)) continue;
            if (!com.mineagent.engine.pathing.util.BlockHelper.canOpenByHand(st)) continue;
            sp.setShiftKeyDown(false);
            com.mineagent.engine.act.Interaction.interactBlock(sp, at, net.minecraft.world.InteractionHand.MAIN_HAND);
            if (com.mineagent.engine.pathing.util.BlockHelper.isPassable(level.getBlockState(at))) {
                var loop = com.mineagent.engine.task.TaskContext.agentLoop(player);
                if (loop != null) loop.doorKeeper().opened(level, at, level.getGameTime());
            }
            return;
        }
    }

    public AgentPlayer player() {
        return player;
    }

    /** Rebind world-owned path state after a dimension transition. */
    private boolean ensureCurrentLevel(boolean notifyActiveFailure) {
        var currentLevel = TaskContext.serverPlayer(player).serverLevel();
        if (coreLevel == currentLevel) return true;
        boolean wasActive = core != null && core.isActive();
        if (core != null) core.cancel();
        coreLevel = currentLevel;
        core = new PathingCore(player, TaskContext.navCaches(player),
                com.mineagent.engine.MineAgentEngine.getConfig().pathfinding(),
                terrainPolicy);
        if (notifyActiveFailure && wasActive && listener != null) {
            // Coordinates do not imply the same target in another dimension.
            // Report a deterministic failure instead of silently reusing them.
            listener.onNavigationFailed("DIMENSION_CHANGED");
        }
        return !notifyActiveFailure || !wasActive;
    }

    /** Push a bounded path snapshot to the owner at most four times/second. */
    private void pushPathDebug(boolean stateChanged) {
        var sp = TaskContext.serverPlayer(player);
        long now = sp.level().getGameTime();
        if (!stateChanged && now - lastDebugPushTick < 5) return;
        lastDebugPushTick = now;

        java.util.List<double[]> nodes = new java.util.ArrayList<>();
        int currentNode = -1;
        String status = core.state().name().toLowerCase(java.util.Locale.ROOT);
        PathExecutor executor = core.executor();
        if (executor != null && !executor.path().isEmpty()) {
            var path = executor.path();
            var first = path.get(0);
            nodes.add(new double[]{first.srcX() + 0.5, first.srcY(), first.srcZ() + 0.5});
            int movementLimit = Math.min(path.length(), 4095);
            for (int i = 0; i < movementLimit; i++) {
                var move = path.get(i);
                nodes.add(new double[]{move.dstX() + 0.5, move.dstY(), move.dstZ() + 0.5});
            }
            currentNode = Math.min(executor.currentMovementIndex(), nodes.size() - 1);
            if (executor.isFailed()) status = "failed";
        } else if (core.state() == PathingCore.State.IDLE
                && core.lastResult() != null && !core.lastResult().foundPath()) {
            status = "failed";
        }

        if (player instanceof com.mineagent.engine.entity.CompanionEntity companion) {
            var owner = companion.serverPlayerOwner();
            if (owner != null) {
                com.mineagent.engine.network.MineAgentNetwork.sendPathDebugTo(owner,
                        new com.mineagent.api.network.payload.PathDebugPayload(
                                player.companionId(), nodes, currentNode, status));
            }
        }
    }
}
