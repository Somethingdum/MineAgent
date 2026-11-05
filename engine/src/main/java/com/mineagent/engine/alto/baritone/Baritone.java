package com.mineagent.engine.alto.baritone;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.engine.alto.baritone.altoclef.AltoClefSettings;
import com.mineagent.engine.alto.baritone.api.IBaritone;
import com.mineagent.engine.alto.baritone.api.Settings;
import com.mineagent.engine.alto.baritone.api.pathing.goals.Goal;
import com.mineagent.engine.alto.baritone.api.process.ICustomGoalProcess;
import com.mineagent.engine.alto.baritone.api.schematic.ISchematic;
import com.mineagent.engine.alto.baritone.api.utils.IPlayerContext;
import com.mineagent.engine.alto.baritone.api.utils.IPlayerController;
import com.mineagent.engine.alto.baritone.api.utils.Rotation;
import com.mineagent.engine.alto.baritone.api.utils.input.Input;
import com.mineagent.engine.alto.baritone.utils.BlockStateInterface;
import com.mineagent.engine.entity.CompanionInputDriver;
import com.mineagent.engine.pathing.execute.PathingCore;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.planning.IntentContract;
import com.mineagent.engine.task.TaskContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Baritone, as AltoClef's tasks call it, for one companion (SMH plan 70: AltoClef's tasks on the companion's
 * server-side body). Its processes are Baritone's in what they promise the tasks (a custom goal walked to until it is
 * reached or no way is found, then let go; exploring away from where it set out; a schematic built block by block) and
 * the companion's own pathing does the walking ({@link PlayerNav}). The walks follow Baritone's defaults: breaking and
 * placing blocks allowed, priced (Settings.blockBreakAdditionalPenalty, blockPlacementPenalty), and the companion's
 * own refusals on top (nothing built, nothing placed by a player, nothing in a place its owner protects, no break that
 * floods or drops a stack).
 */
public final class Baritone implements IBaritone {

    /** Baritone's own defaults, break and place allowed; the companion's walk caps on how much of either, generous. */
    public static final IntentContract.TerrainPolicy BARITONE_DEFAULTS = new IntentContract.TerrainPolicy(
            true, true, true, false, 64, 64, 384, IntentContract.CleanupMode.CONTEXTUAL);

    private final AgentPlayer agent;
    private final ServerPlayer player;
    private final Settings settings = new Settings();
    private final AltoClefSettings extra = new AltoClefSettings();
    private final PlayerNav nav;
    private final PlayerContext ctx = new PlayerContext();
    private final PathingBehavior pathingBehavior = new PathingBehavior();
    private final CustomGoalProcess customGoalProcess = new CustomGoalProcess();
    private final ExploreProcess exploreProcess = new ExploreProcess();
    private final BuilderProcess builderProcess = new BuilderProcess();
    private final InputOverrideHandler inputOverrideHandler = new InputOverrideHandler();
    private final LookBehavior lookBehavior = new LookBehavior();
    private final IdleProcess mineProcess = new IdleProcess();
    private final IdleProcess getToBlockProcess = new IdleProcess();
    private final IdleProcess followProcess = new IdleProcess();
    private final IdleProcess farmProcess = new IdleProcess();
    /** The world as Baritone's checks read it (AltoClef's fork makes it public). */
    public final BlockStateInterface bsi;

    private Goal walking;
    private volatile boolean reached, failed;

    public Baritone(AgentPlayer agent, ServerPlayer player) {
        this.agent = agent;
        this.player = player;
        this.nav = new PlayerNav(agent, TaskContext.navCaches(agent), BARITONE_DEFAULTS);
        this.nav.setListener(new PlayerNav.NavListener() {
            @Override public void onGoalReached() { reached = true; }
            @Override public void onNavigationFailed(String reason) { failed = true; }
        });
        this.bsi = new BlockStateInterface(player.serverLevel());
    }

    public Settings settings() {
        return settings;
    }

    public AltoClefSettings getExtraSettings() {
        return extra;
    }

    @Override
    public PathingBehavior getPathingBehavior() {
        return pathingBehavior;
    }

    @Override
    public CustomGoalProcess getCustomGoalProcess() {
        return customGoalProcess;
    }

    @Override
    public ExploreProcess getExploreProcess() {
        return exploreProcess;
    }

    @Override
    public BuilderProcess getBuilderProcess() {
        return builderProcess;
    }

    @Override
    public InputOverrideHandler getInputOverrideHandler() {
        return inputOverrideHandler;
    }

    @Override
    public LookBehavior getLookBehavior() {
        return lookBehavior;
    }

    @Override
    public IPlayerContext getPlayerContext() {
        return ctx;
    }

    public IdleProcess getMineProcess() {
        return mineProcess;
    }

    public IdleProcess getGetToBlockProcess() {
        return getToBlockProcess;
    }

    public IdleProcess getFollowProcess() {
        return followProcess;
    }

    public IdleProcess getFarmProcess() {
        return farmProcess;
    }

    /**
     * Once a tick, after the task tree: the process in control (the builder, else the custom goal, else exploring, as
     * Baritone's process priorities have it) sets the walk's goal; the walk ticks; then what the input override forces
     * is put on the body with the task tree's own keys.
     */
    public void tick(com.mineagent.engine.alto.control.InputControls controls, CompanionInputDriver driver) {
        Goal want = null;
        if (builderProcess.isActive()) {
            want = builderProcess.tick();
        } else if (customGoalProcess.isActive()) {
            want = customGoalProcess.goal;
        } else if (exploreProcess.isActive()) {
            want = exploreProcess.tick();
        }
        if (want == null) {
            if (walking != null) {
                nav.cancel();
                walking = null;
            }
        } else if (want != walking) {
            walking = want;
            reached = false;
            failed = false;
            nav.navigateToGoal(new GoalAdapter(want));
        }
        if (walking != null) {
            nav.tick();
            boolean in = walking.isInGoal(player.blockPosition());
            if (reached || in || failed || !nav.isNavigating()) {
                // Baritone lets a custom goal go once it is in it, or when no path is found (CALC_FAILED).
                if (walking == customGoalProcess.goal) customGoalProcess.onLostControl();
                if (walking == exploreProcess.target) exploreProcess.arrived(in || reached);
                nav.cancel();
                walking = null;
            }
        }
        controls.apply(driver, inputOverrideHandler.forced, walking != null);
    }

    /** Everything let go: no process in control, no walk, no forced keys (the task tree stopped). */
    public void stopAll() {
        customGoalProcess.onLostControl();
        exploreProcess.onLostControl();
        builderProcess.onLostControl();
        inputOverrideHandler.clearAllKeys();
        nav.cancel();
        walking = null;
    }

    public PlayerNav nav() {
        return nav;
    }

    // ------------------------------------------------------------------------------------------------------------

    /** The companion's body as Baritone's player context. */
    public final class PlayerContext implements IPlayerContext {
        private final IPlayerController controller = () -> com.mineagent.engine.act.BlockTargeting.interactionReach(player);

        @Override public ServerPlayer player() { return player; }
        @Override public IPlayerController playerController() { return controller; }
        @Override public ServerLevel world() { return player.serverLevel(); }

        @Override
        public HitResult objectMouseOver() {
            var driver = TaskContext.inputDriver(agent);
            return driver instanceof CompanionInputDriver d ? d.pick() : null;
        }
    }

    /** Baritone's PathingBehavior: what AltoClef asks of it. */
    public final class PathingBehavior {
        public boolean isPathing() {
            return walking != null && nav.isNavigating();
        }

        /** Mid-air, mid-pillar or in a parkour jump a walk is not safely stopped; on the ground or swimming it is. */
        public boolean isSafeToCancel() {
            if (walking == null) return true;
            return player.onGround() || player.isInWater() || player.onClimbable();
        }

        public void forceCancel() {
            cancelEverything();
        }

        public boolean cancelEverything() {
            customGoalProcess.onLostControl();
            exploreProcess.onLostControl();
            builderProcess.onLostControl();
            nav.cancel();
            walking = null;
            return true;
        }

        /** Baritone's estimate of the ticks left in the walk's current segment; unknown here. */
        public Optional<Double> ticksRemainingInSegment() {
            return Optional.empty();
        }

        public Goal getGoal() {
            return walking;
        }

        public PathingCore.State state() {
            return nav.state();
        }
    }

    /** Baritone's CustomGoalProcess: walk to a goal until in it, or until no way to it is found. */
    public final class CustomGoalProcess implements ICustomGoalProcess {
        private Goal goal;
        private boolean active;

        @Override
        public void setGoal(Goal goal) {
            this.goal = goal;
        }

        @Override
        public void path() {
            if (goal != null) active = true;
        }

        @Override
        public Goal getGoal() {
            return goal;
        }

        @Override
        public boolean isActive() {
            return active && goal != null;
        }

        @Override
        public void onLostControl() {
            active = false;
            goal = null;
        }
    }

    /**
     * Baritone's ExploreProcess: out from where it was told to explore from, to ground it has not walked, a leg at a
     * time (Baritone picks the nearest chunks it has no cache of; this body remembers the ground it has seen, so each
     * leg goes to the farthest of eight headings from where it has been).
     */
    public final class ExploreProcess {
        private BlockPos origin;
        private Goal target;
        private int legs;
        private final java.util.List<BlockPos> been = new java.util.ArrayList<>();
        static final int LEG = 48;

        public void explore(int centerX, int centerZ) {
            origin = new BlockPos(centerX, player.getBlockY(), centerZ);
            target = null;
            legs = 0;
            been.clear();
        }

        public boolean isActive() {
            return origin != null;
        }

        public void onLostControl() {
            origin = null;
            target = null;
        }

        Goal tick() {
            if (target != null) return target;
            BlockPos here = player.blockPosition();
            been.add(here);
            BlockPos best = null;
            double bestScore = -1;
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i + legs * 0.3;
                BlockPos p = new BlockPos(here.getX() + (int) Math.round(Math.cos(a) * LEG), here.getY(),
                        here.getZ() + (int) Math.round(Math.sin(a) * LEG));
                double score = Double.MAX_VALUE;
                for (BlockPos b : been) score = Math.min(score, b.distSqr(p));
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
            legs++;
            target = new com.mineagent.engine.alto.baritone.api.pathing.goals.GoalXZ(best.getX(), best.getZ());
            return target;
        }

        void arrived(boolean ok) {
            target = null;
        }
    }

    /**
     * Baritone's BuilderProcess, as AltoClef uses it: a schematic built block by block, each wrong block broken, each
     * missing one placed from what it carries; walked to where it can reach each one.
     */
    public final class BuilderProcess {
        private ISchematic schematic;
        private Vec3i origin;
        private BlockPos current;

        public void build(String name, ISchematic schematic, Vec3i origin) {
            this.schematic = schematic;
            this.origin = origin;
            this.current = null;
        }

        public boolean isActive() {
            return schematic != null;
        }

        public void onLostControl() {
            schematic = null;
            current = null;
        }

        /** The next block to put right; null when all are right (the build is done and let go). */
        Goal tick() {
            BlockPos todo = null;
            BlockState want = null;
            double best = Double.MAX_VALUE;
            for (int x = 0; x < schematic.widthX(); x++) {
                for (int y = 0; y < schematic.heightY(); y++) {
                    for (int z = 0; z < schematic.lengthZ(); z++) {
                        BlockPos p = new BlockPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        BlockState now = player.serverLevel().getBlockState(p);
                        if (!schematic.inSchematic(x, y, z, now)) continue;
                        BlockState desired = schematic.desiredState(x, y, z, now, java.util.List.of());
                        if (desired == null || desired.equals(now)) continue;
                        double d = p.distToCenterSqr(player.position());
                        if (d < best) {
                            best = d;
                            todo = p;
                            want = desired;
                        }
                    }
                }
            }
            if (todo == null) {
                onLostControl();
                return null;
            }
            current = todo;
            double reach = com.mineagent.engine.act.BlockTargeting.interactionReach(player);
            if (todo.distToCenterSqr(player.getEyePosition()) <= reach * reach) {
                BlockState now = player.serverLevel().getBlockState(todo);
                if (!now.canBeReplaced()) {
                    com.mineagent.engine.task.BlockDigger.startBreaking(player, todo);
                } else if (!com.mineagent.engine.act.Placement.placeBlock(player, todo, want)) {
                    com.mineagent.engine.act.Placement.placeAnySupportBlock(player, todo);
                }
                return null;
            }
            return new com.mineagent.engine.alto.baritone.api.pathing.goals.GoalNear(todo, 2);
        }
    }

    /** Baritone's InputOverrideHandler: keys forced down whatever else the body does. */
    public static final class InputOverrideHandler {
        final Set<Input> forced = EnumSet.noneOf(Input.class);

        public void setInputForceState(Input input, boolean forced) {
            if (forced) this.forced.add(input);
            else this.forced.remove(input);
        }

        public boolean isInputForcedDown(Input input) {
            return forced.contains(input);
        }

        public void clearAllKeys() {
            forced.clear();
        }
    }

    /** Baritone's LookBehavior: the body turned to look where it is told (it has no camera to keep apart). */
    public final class LookBehavior {
        private final AimProcessor aim = new AimProcessor();

        public void updateTarget(Rotation rotation, boolean blockInteract) {
            player.setYRot(rotation.getYaw());
            player.setXRot(rotation.getPitch());
            player.setYHeadRot(rotation.getYaw());
        }

        public AimProcessor getAimProcessor() {
            return aim;
        }
    }

    /** Baritone's aim processor: a server-side body turns exactly as told. */
    public static final class AimProcessor {
        public Rotation peekRotation(Rotation rotation) {
            return rotation;
        }
    }

    /** A process AltoClef only ever lets go of (Baritone's mine, get-to-block, follow and farm processes). */
    public static final class IdleProcess {
        public boolean isActive() {
            return false;
        }

        public void onLostControl() {
        }
    }

    /**
     * A Baritone goal as the companion's pathfinder takes one. Baritone's heuristics are in ticks (a walked block is
     * WALK_ONE_BLOCK_COST, about 4.63); the companion's walk costs a walked block 1, so they are scaled to blocks.
     */
    static final class GoalAdapter implements com.mineagent.engine.pathing.goals.Goal {
        final Goal goal;

        GoalAdapter(Goal goal) {
            this.goal = goal;
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            return goal.isInGoal(x, y, z);
        }

        @Override
        public double heuristic(int x, int y, int z) {
            return goal.heuristic(x, y, z) / com.mineagent.engine.alto.baritone.api.pathing.movement.ActionCosts.WALK_ONE_BLOCK_COST;
        }

        @Override
        public String toString() {
            return goal.toString();
        }
    }
}
