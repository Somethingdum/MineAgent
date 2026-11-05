// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/main/java/baritone/Baritone.java
// at commit 11ea66c (branch 1.21.1). SMH: one Baritone for each companion's server-side body, with Baritone's own
// behaviours and processes in Baritone's order; no world cache, commands, waypoints, elytra or click GUI. The engine
// ticks it (tick()) each server tick, as a client's tick event does.
package com.mineagent.engine.baritone;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.engine.baritone.api.BaritoneAPI;
import com.mineagent.engine.baritone.api.IBaritone;
import com.mineagent.engine.baritone.api.Settings;
import com.mineagent.engine.baritone.api.behavior.IBehavior;
import com.mineagent.engine.baritone.api.event.events.PlayerUpdateEvent;
import com.mineagent.engine.baritone.api.event.events.SprintStateEvent;
import com.mineagent.engine.baritone.api.event.events.TickEvent;
import com.mineagent.engine.baritone.api.event.events.type.EventState;
import com.mineagent.engine.baritone.api.event.listener.IEventBus;
import com.mineagent.engine.baritone.api.process.IBaritoneProcess;
import com.mineagent.engine.baritone.api.utils.IPlayerContext;
import com.mineagent.engine.baritone.behavior.*;
import com.mineagent.engine.baritone.event.GameEventHandler;
import com.mineagent.engine.baritone.process.*;
import com.mineagent.engine.baritone.selection.SelectionManager;
import com.mineagent.engine.baritone.utils.BlockStateInterface;
import com.mineagent.engine.baritone.utils.InputOverrideHandler;
import com.mineagent.engine.baritone.utils.PathingControlManager;
import com.mineagent.engine.baritone.utils.player.BaritonePlayerContext;

import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author Brady
 * @since 7/31/2018
 */
public class Baritone implements IBaritone {

    private static final ThreadPoolExecutor threadPool;

    static {
        // SMH: daemon threads. A client's game ends its process however it stops; a server's ends only when no other
        // thread is left, and Baritone's four core threads never time out, so a stopped server never exited.
        java.util.concurrent.atomic.AtomicInteger made = new java.util.concurrent.atomic.AtomicInteger();
        threadPool = new ThreadPoolExecutor(4, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), run -> {
            Thread t = new Thread(run, "MineAgent Baritone " + made.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    private final AgentPlayer agent;
    private final GameEventHandler gameEventHandler;

    private final PathingBehavior pathingBehavior;
    private final LookBehavior lookBehavior;
    private final InventoryBehavior inventoryBehavior;
    private final InputOverrideHandler inputOverrideHandler;

    private final FollowProcess followProcess;
    private final MineProcess mineProcess;
    private final GetToBlockProcess getToBlockProcess;
    private final CustomGoalProcess customGoalProcess;
    private final BuilderProcess builderProcess;
    private final ExploreProcess exploreProcess;
    private final FarmProcess farmProcess;
    private final InventoryPauserProcess inventoryPauserProcess;

    private final PathingControlManager pathingControlManager;
    private final SelectionManager selectionManager;

    private final BaritonePlayerContext playerContext;

    public BlockStateInterface bsi;

    private final com.mineagent.engine.baritone.altoclef.AltoClefSettings altoClefSettings = new com.mineagent.engine.baritone.altoclef.AltoClefSettings();

    public Baritone(AgentPlayer agent, ServerPlayer player) {
        this.agent = agent;
        this.gameEventHandler = new GameEventHandler(this);

        // Define this before behaviors try and get it, or else it will be null and the builds will fail!
        this.playerContext = new BaritonePlayerContext(this, player);

        {
            this.lookBehavior         = this.registerBehavior(LookBehavior::new);
            this.pathingBehavior      = this.registerBehavior(PathingBehavior::new);
            this.inventoryBehavior    = this.registerBehavior(InventoryBehavior::new);
            this.inputOverrideHandler = this.registerBehavior(InputOverrideHandler::new);
        }

        this.pathingControlManager = new PathingControlManager(this);
        {
            this.followProcess           = this.registerProcess(FollowProcess::new);
            this.mineProcess             = this.registerProcess(MineProcess::new);
            this.customGoalProcess       = this.registerProcess(CustomGoalProcess::new); // very high iq
            this.getToBlockProcess       = this.registerProcess(GetToBlockProcess::new);
            this.builderProcess          = this.registerProcess(BuilderProcess::new);
            this.exploreProcess          = this.registerProcess(ExploreProcess::new);
            this.farmProcess             = this.registerProcess(FarmProcess::new);
            this.inventoryPauserProcess  = this.registerProcess(InventoryPauserProcess::new);
            this.registerProcess(BackfillProcess::new);
        }

        this.selectionManager = new SelectionManager(this);
    }

    /**
     * SMH: one server tick, as a client's mixins drive Baritone: the tick (PRE), the player's update (PRE: where it looks
     * is set), whether it sprints, the update (POST), then the tick's end (POST).
     */
    public void tick() {
        BaritoneProvider.as(this, () -> {
            tickInner();
            return null;
        });
    }

    private void tickInner() {
        var tickProvider = TickEvent.createNextProvider();
        this.gameEventHandler.onTick(tickProvider.apply(EventState.PRE, TickEvent.Type.IN));
        this.gameEventHandler.onPlayerUpdate(new PlayerUpdateEvent(EventState.PRE));
        SprintStateEvent sprint = new SprintStateEvent();
        this.gameEventHandler.onPlayerSprintState(sprint);
        if (sprint.getState() != null) {
            var driver = com.mineagent.engine.task.TaskContext.inputDriver(agent);
            if (driver != null) driver.setSprinting(sprint.getState());
        }
        this.gameEventHandler.onPlayerUpdate(new PlayerUpdateEvent(EventState.POST));
        this.gameEventHandler.onPostTick(tickProvider.apply(EventState.POST, TickEvent.Type.IN));
    }

    /** SMH: the companion's new body after a respawn. */
    public void setPlayer(ServerPlayer player) {
        this.playerContext.setPlayer(player);
    }

    public AgentPlayer getAgent() {
        return agent;
    }

    /** SMH: the Baritone settings (one set for every companion). */
    public Settings getSettings() {
        return BaritoneAPI.getSettings();
    }

    public void registerBehavior(IBehavior behavior) {
        this.gameEventHandler.registerEventListener(behavior);
    }

    public <T extends IBehavior> T registerBehavior(Function<Baritone, T> constructor) {
        final T behavior = constructor.apply(this);
        this.registerBehavior(behavior);
        return behavior;
    }

    public <T extends IBaritoneProcess> T registerProcess(Function<Baritone, T> constructor) {
        final T behavior = constructor.apply(this);
        this.pathingControlManager.registerProcess(behavior);
        return behavior;
    }

    @Override
    public PathingControlManager getPathingControlManager() {
        return this.pathingControlManager;
    }

    @Override
    public InputOverrideHandler getInputOverrideHandler() {
        return this.inputOverrideHandler;
    }

    @Override
    public CustomGoalProcess getCustomGoalProcess() {
        return this.customGoalProcess;
    }

    @Override
    public GetToBlockProcess getGetToBlockProcess() {
        return this.getToBlockProcess;
    }

    @Override
    public IPlayerContext getPlayerContext() {
        return this.playerContext;
    }

    @Override
    public FollowProcess getFollowProcess() {
        return this.followProcess;
    }

    @Override
    public BuilderProcess getBuilderProcess() {
        return this.builderProcess;
    }

    public InventoryBehavior getInventoryBehavior() {
        return this.inventoryBehavior;
    }

    @Override
    public LookBehavior getLookBehavior() {
        return this.lookBehavior;
    }

    @Override
    public ExploreProcess getExploreProcess() {
        return this.exploreProcess;
    }

    @Override
    public MineProcess getMineProcess() {
        return this.mineProcess;
    }

    @Override
    public FarmProcess getFarmProcess() {
        return this.farmProcess;
    }

    public InventoryPauserProcess getInventoryPauserProcess() {
        return this.inventoryPauserProcess;
    }

    @Override
    public PathingBehavior getPathingBehavior() {
        return this.pathingBehavior;
    }

    @Override
    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    @Override
    public IEventBus getGameEventHandler() {
        return this.gameEventHandler;
    }

    /** SMH: the settings of the companion whose Baritone is ticking, else Baritone's defaults. */
    public static Settings settings() {
        return BaritoneAPI.getSettings();
    }

    public static Executor getExecutor() {
        // SMH: a search or scan runs as the companion's Baritone that started it (its own AltoClef settings and rules).
        Baritone owner = BaritoneProvider.current();
        if (owner == null) return threadPool;
        return run -> threadPool.execute(() -> BaritoneProvider.as(owner, () -> {
            run.run();
            return null;
        }));
    }

    /** SMH: this companion's own AltoClef settings (its fork's hooks: what not to break or place, tools to save). */
    public com.mineagent.engine.baritone.altoclef.AltoClefSettings getAltoClefSettings() {
        return altoClefSettings;
    }
}
