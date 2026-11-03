package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.pathing.goals.GoalNearXZ;
import com.mineagent.engine.pathing.util.ExplorePlanner;
import com.mineagent.engine.survival.ReturnReserve;
import com.mineagent.engine.world.ChunkSummaries;
import com.mineagent.engine.world.PlaceKeeper;
import com.mineagent.engine.world.RouteMemory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Looking for something it has never seen (SMH plan 70, MA5.6): "find a village" with no village known walks to the
 * nearest ground it has never seen, chunk by chunk in a widening spiral, while its route memory watches for the place
 * (a village is recognised the moment it walks into one). It stops when it finds one, when it has looked over
 * {@code maxChunks} new chunks, or when the way home would need more food than it has to spare, and then heads home.
 */
public class ExploreTask extends CompanionTask<ExploreTask.ExploreRecord> {
    public static final class ExploreRecord extends TaskRecord {
        /** The kind of place sought ("village"), or null just to explore. */
        public final String lookFor;
        public final int maxChunks;

        public ExploreRecord(String toolCallId, String lookFor, int maxChunks) {
            super(toolCallId);
            this.lookFor = lookFor;
            this.maxChunks = Math.max(1, maxChunks);
        }
    }

    /** How far out it looks, in chunks, and how near the middle of a chunk counts as having looked at it. */
    static final int RADIUS = 48;
    static final int NEAR = 8;

    private PlayerNav nav;
    private volatile boolean reached;
    private volatile boolean failed;
    private int[] target;
    private final Set<Long> tried = new HashSet<>();
    private int looked;
    private int knownAtStart = -1;
    private boolean goingHome;
    private boolean begun;
    private RouteMemory.Point origin;
    private String outcome = "";

    public ExploreTask(AgentPlayer player, ExploreRecord record) {
        super(player, record);
    }

    private PlaceKeeper keeper() {
        var loop = TaskContext.agentLoop(player);
        return loop == null ? null : loop.placeKeeper();
    }

    private int known() {
        PlaceKeeper k = keeper();
        if (k == null || record.lookFor == null) return 0;
        return (int) k.memory().places().stream().filter(p -> record.lookFor.equals(p.kind())).count();
    }

    @Override
    protected void onStart() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        nav = new PlayerNav(player, TaskContext.navCaches(player));
        nav.setListener(new PlayerNav.NavListener() {
            @Override public void onGoalReached() { reached = true; }
            @Override public void onNavigationFailed(String reason) { failed = true; }
        });
        if (!begun) {
            begun = true;
            knownAtStart = known();
            origin = PlaceKeeper.point(sp);
            // Exploring takes longer than a walk: half an hour of game time at most.
            long now = sp.level().getGameTime();
            record.extendDeadlineTo(Math.max(record.deadline(), now + 36_000L));
        }
        reached = false;
        failed = false;
        if (goingHome) headHome(sp);
        else next(sp);
    }

    private void next(ServerPlayer sp) {
        PlaceKeeper k = keeper();
        if (k == null) {
            target = null;
            return;
        }
        String dim = sp.serverLevel().dimension().location().toString();
        target = ExplorePlanner.nextUnseen(k.chunks(), dim, sp.getBlockX() >> 4, sp.getBlockZ() >> 4, RADIUS, tried);
        if (target != null) nav.navigateToGoal(new GoalNearXZ(target[0] * 16 + 8, target[1] * 16 + 8, NEAR));
    }

    /** Home if it has one in this dimension, else where it set out from. */
    private RouteMemory.Point home(ServerPlayer sp) {
        RouteMemory.Point here = PlaceKeeper.point(sp);
        PlaceKeeper k = keeper();
        if (k != null) {
            var h = k.memory().find("home", here);
            if (h.isPresent() && h.get().at().dim().equals(here.dim())) return h.get().at();
        }
        return origin != null && origin.dim().equals(here.dim()) ? origin : here;
    }

    private void headHome(ServerPlayer sp) {
        RouteMemory.Point h = home(sp);
        nav.navigateNear(h.x(), h.y(), h.z(), 3);
    }

    private boolean reserveOk(ServerPlayer sp) {
        RouteMemory.Point here = PlaceKeeper.point(sp);
        double distance = home(sp).distance(here);
        var food = sp.getFoodData();
        double carried = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var props = inv.getItem(i).get(DataComponents.FOOD);
            if (props != null) carried += (props.nutrition() + props.saturation()) * inv.getItem(i).getCount();
        }
        return ReturnReserve.enough(food.getFoodLevel(), food.getSaturationLevel(), carried, distance);
    }

    /** It says why it stopped: no model is asked to put a command's result into words. */
    private TaskState finish(TaskState state) {
        var loop = TaskContext.agentLoop(player);
        if (loop != null && !outcome.isEmpty()) loop.say(Character.toUpperCase(outcome.charAt(0)) + outcome.substring(1) + ".");
        return state;
    }

    @Override
    protected TaskState onTick() {
        TaskState s = step();
        return s == TaskState.RUNNING ? s : finish(s);
    }

    private TaskState step() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        if (!goingHome && known() > knownAtStart) {
            outcome = "found " + (record.lookFor == null ? "it" : "a " + record.lookFor);
            return TaskState.SUCCESS;
        }
        if (!goingHome && !reserveOk(sp)) {
            goingHome = true;
            outcome = "turned back: the way home needs the food I have left";
            reached = failed = false;
            headHome(sp);
        }
        if (goingHome) {
            if (reached) return TaskState.SUCCESS;
            if (failed) {
                outcome = outcome + ", but I couldn't find the way home";
                return TaskState.FAILED;
            }
            nav.tick();
            return TaskState.RUNNING;
        }
        if (target == null) {
            outcome = "no new ground left within " + RADIUS + " chunks";
            return TaskState.SUCCESS;
        }
        if (reached || failed) {
            tried.add(ChunkSummaries.key(target[0], target[1]));
            if (reached) looked++;
            if (looked >= record.maxChunks) {
                outcome = "looked over " + looked + " new chunks; nothing yet";
                return TaskState.SUCCESS;
            }
            reached = failed = false;
            next(sp);
            return TaskState.RUNNING;
        }
        nav.tick();
        return TaskState.RUNNING;
    }

    @Override
    protected void onInterrupt() {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
    }

    /** Why it stopped, for its owner. */
    public String outcome() {
        return outcome;
    }
}
