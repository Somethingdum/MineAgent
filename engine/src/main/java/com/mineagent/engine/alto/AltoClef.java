// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/AltoClef.java at
// commit 1f8e038, in Mojang's names (third_party/altoclef). SMH: AltoClef is one client's mod with one player; here there is
// one per companion, built and ticked by the engine (alto/AltoTreeTask) for the companion's server-side body. Its parts are
// AltoClef's own, made in the same order; its tick is AltoClef's client tick. No commands, butler, overlay or chat.
package com.mineagent.engine.alto;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.sbe.automatone.api.utils.input.Input;
import com.mineagent.sbe.automatone.Baritone;
import com.mineagent.sbe.automatone.BaritoneProvider;
import com.mineagent.sbe.automatone.api.BaritoneAPI;
import com.mineagent.engine.rules.BlockRules;
import com.mineagent.engine.rules.SmhRules;
import com.mineagent.sbe.automatone.settings.AltoClefSettings;
import com.mineagent.sbe.automatone.api.Settings;
import com.mineagent.engine.alto.chains.FoodChain;
import com.mineagent.engine.alto.chains.MLGBucketFallChain;
import com.mineagent.engine.alto.chains.MobDefenseChain;
import com.mineagent.engine.alto.chains.UserTaskChain;
import com.mineagent.engine.alto.control.InputControls;
import com.mineagent.engine.alto.control.PlayerExtraController;
import com.mineagent.engine.alto.control.ServerGameMode;
import com.mineagent.engine.alto.control.SlotHandler;
import com.mineagent.engine.alto.eventbus.EventBus;
import com.mineagent.engine.alto.eventbus.events.BlockBreakingCancelEvent;
import com.mineagent.engine.alto.eventbus.events.BlockBreakingEvent;
import com.mineagent.engine.alto.eventbus.events.ScreenOpenEvent;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.tasksystem.TaskChain;
import com.mineagent.engine.alto.tasksystem.TaskRunner;
import com.mineagent.engine.alto.trackers.BlockScanner;
import com.mineagent.engine.alto.trackers.CraftingRecipeTracker;
import com.mineagent.engine.alto.trackers.EntityTracker;
import com.mineagent.engine.alto.trackers.MiscBlockTracker;
import com.mineagent.engine.alto.trackers.SimpleChunkTracker;
import com.mineagent.engine.alto.trackers.TrackerManager;
import com.mineagent.engine.alto.trackers.storage.ContainerSubTracker;
import com.mineagent.engine.alto.trackers.storage.ItemStorageTracker;
import com.mineagent.engine.alto.util.helpers.StorageHelper;
import com.mineagent.engine.entity.CompanionInputDriver;
import com.mineagent.engine.entity.fakeplayer.FakePlayerGameMode;
import com.mineagent.engine.task.TaskContext;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central access point for AltoClef, one per companion.
 */
public class AltoClef {

    /** Every companion's host, by its body's id: Baritone's "baritone for this player". */
    private static final Map<UUID, AltoClef> HOSTS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger BARITONE_LINES = new java.util.concurrent.atomic.AtomicInteger();
    /**
     * SMH: the host this thread is working for (its tick, its construction, a call into it): AltoClef's static
     * getInstance(), per companion and per thread, failing loudly outside any scope (SMH plan 71, §6.4, HostScope).
     */
    private static final HostScope<AltoClef> SCOPE = new HostScope<>("AltoClef");

    private final AgentPlayer agent;
    private ServerPlayer player;
    private final EventBus eventBus = new EventBus();

    // Central Managers
    private final TaskRunner taskRunner;
    private final TrackerManager trackerManager;
    private final BotBehaviour botBehaviour;
    private final PlayerExtraController extraController;
    // Task chains
    private final UserTaskChain userTaskChain;
    private final FoodChain foodChain;
    private final MobDefenseChain mobDefenseChain;
    private final MLGBucketFallChain mlgBucketChain;
    // Trackers
    private final ItemStorageTracker storageTracker;
    private ContainerSubTracker containerSubTracker;
    private final EntityTracker entityTracker;
    private final BlockScanner blockScanner;
    private final SimpleChunkTracker chunkTracker;
    private final MiscBlockTracker miscBlockTracker;
    private final CraftingRecipeTracker craftingRecipeTracker;
    // Settings
    private com.mineagent.engine.alto.Settings settings;
    // Misc managers/input
    private final InputControls inputControls;
    private final SlotHandler slotHandler;
    private final ServerGameMode controller = new ServerGameMode();
    private Baritone baritone;
    // SMH: what the companion's body was showing last tick, for AltoClef's screen events.
    private AbstractContainerMenu lastMenu;
    private boolean wasBreaking;

    private AltoClef(AgentPlayer agent, ServerPlayer player) {
        this.agent = agent;
        this.player = player;
        AltoClef before = SCOPE.swap(this);
        try {
            // Baritone's engine (engine/.../baritone, AltoClef's own fork of it), one for each companion's body.
            this.baritone = (Baritone) com.mineagent.engine.agent.Agents.bodyBaritone(agent);
            // SMH plan 71, §6.2 rule 2: this host applies the body's keys (tick), Automatone's forced keys over its own.
            com.mineagent.sbe.common.ServerHooks.keysByHost(this.baritone);
            settings = new com.mineagent.engine.alto.Settings();
            initializeBaritoneSettings();
            taskRunner = new TaskRunner(this);
            trackerManager = new TrackerManager(this);
            botBehaviour = new BotBehaviour(this);
            extraController = new PlayerExtraController(this);
            userTaskChain = new UserTaskChain(taskRunner);
            // AltoClef's hand fixes: the best tool equipped while it breaks a block, a stack left on the cursor put
            // away, a container shut when it looks away (a chain that works out its priority and never runs).
            new com.mineagent.engine.alto.chains.PlayerInteractionFixChain(taskRunner);
            // AltoClef's own survival chains, in its order (behaviour.altoSurvival): mob defence, the MLG bucket, getting
            // unstuck, equipping ahead, lava, fire and drowning, eating; each by priority over the task tree, as AltoClef
            // runs them. Off, they are made on a runner that never runs (its tasks still ask them what they know), and the
            // companion's own chains look after it instead.
            TaskRunner survival = com.mineagent.engine.settings.Switches.altoSurvival() ? taskRunner : new TaskRunner(this);
            mobDefenseChain = new MobDefenseChain(survival);
            mlgBucketChain = new MLGBucketFallChain(survival);
            new com.mineagent.engine.alto.chains.UnstuckChain(survival);
            new com.mineagent.engine.alto.chains.PreEquipItemChain(survival);
            new com.mineagent.engine.alto.chains.WorldSurvivalChain(survival);
            foodChain = new FoodChain(survival);
            storageTracker = new ItemStorageTracker(this, trackerManager, container -> containerSubTracker = container);
            entityTracker = new EntityTracker(trackerManager);
            blockScanner = new BlockScanner(this);
            chunkTracker = new SimpleChunkTracker(this);
            miscBlockTracker = new MiscBlockTracker(this);
            craftingRecipeTracker = new CraftingRecipeTracker(trackerManager);
            inputControls = new InputControls(player);
            knowAllRecipes(player);
            slotHandler = new SlotHandler(this);
            // AltoClef's settings load: its throwaway blocks are blocks Baritone may place, its protected areas are never
            // broken or built on, and the tools it saves are not spent.
            List<Item> baritoneCanPlace = Arrays.stream(settings.getThrowawayItems(true))
                    .filter(item -> item != Items.SOUL_SAND && item != Items.MAGMA_BLOCK && item != Items.SAND && item
                            != Items.GRAVEL).toList();
            getClientBaritoneSettings().acceptableThrowawayItems.get().addAll(baritoneCanPlace);
            // SMH: this host's, given: Baritone asks these from its path threads (plan 71, step 1.5).
            getExtraBaritoneSettings().avoidBlockBreak(blockPos -> settings.isPositionExplicitlyProtected(this, blockPos));
            getExtraBaritoneSettings().avoidBlockPlace(blockPos -> settings.isPositionExplicitlyProtected(this, blockPos));
            getExtraBaritoneSettings().getForceSaveToolPredicates().add((state, item) -> StorageHelper.shouldSaveStack(this, state.getBlock(), item));
            // SMH plan 70, MA5.2 and MA5.4: what a person placed is never broken, and nothing is broken or placed at a
            // farm whose player is AFK (the companion's own rules; the kinds of block it never breaks to get
            // somewhere, SmhRules.neverBreakToPass, are not yet asked on Automatone's path: SMH plan 71, step 1.8).
            // SMH plan 71, §6.2 rule 4: one predicate, registered here once, asked again by the body's game mode.
            getExtraBaritoneSettings().avoidBlockBreak(blockPos -> !BlockRules.mayBreak(this.player.serverLevel(), blockPos));
            getExtraBaritoneSettings().avoidBlockPlace(blockPos -> !BlockRules.mayPlace(this.player.serverLevel(), blockPos));
            TaskCatalogue.init();
            if (Boolean.getBoolean("mineagent.traceWork")) {
                // The scenes: Baritone's own messages (its debug ones too), the first 120 of them.
                getClientBaritoneSettings().chatDebug.set(true);
                // SMH: Automatone has no logger setting (sailex's wrote to a client's chat window); its log is routed to
                // the server's at SMH plan 71 step 1.7.
            }
            taskRunner.enable();
        } finally {
            SCOPE.restore(before);
        }
    }

    /** The host for a companion, made the first time it is asked for. */
    public static AltoClef of(AgentPlayer agent) {
        ServerPlayer sp = TaskContext.serverPlayer(agent);
        AltoClef host = HOSTS.computeIfAbsent(agent.companionId(), id -> new AltoClef(agent, sp));
        if (host.player != sp) { // respawned: a new body, the same companion
            host.player = sp;
            knowAllRecipes(sp);
            host.baritone = (Baritone) com.mineagent.engine.agent.Agents.bodyBaritone(host.agent);
            com.mineagent.sbe.common.ServerHooks.keysByHost(host.baritone);
            host.inputControls.setPlayer(sp);
        }
        return host;
    }

    /** A companion's host when it has one (Baritone's getBaritoneForPlayer). */
    public static AltoClef forPlayer(Entity entity) {
        if (entity == null) return null;
        for (AltoClef host : HOSTS.values()) {
            if (host.player == entity || host.player.getUUID().equals(entity.getUUID())) return host;
        }
        return null;
    }

    /**
     * SMH: every recipe in the companion's recipe book, so AltoClef places a recipe from it as it does by default
     * (CraftGenericWithRecipeBooksTask; the server places a recipe only from a book that holds it). It changes nothing it
     * can make: any recipe can be laid out by hand in the grid.
     */
    private static void knowAllRecipes(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        player.awardRecipes(player.getServer().getRecipeManager().getRecipes());
    }

    /** The companion's host let go of (it left). */
    public static void remove(UUID companionId) {
        AltoClef host = HOSTS.remove(companionId);
        if (host != null) {
            host.baritone.getPathingBehavior().forceCancel();
            com.mineagent.sbe.automatone.BaritoneProvider.INSTANCE.playerToBaritone.remove(host.player);
        }
    }

    /** Something of AltoClef's done as this host's (its tasks reach for AltoClef.getInstance() when made and asked). */
    public <T> T within(java.util.function.Supplier<T> what) {
        AltoClef before = SCOPE.swap(this);
        try {
            // Its Baritone is the primary one meanwhile (AltoClef's helpers ask BaritoneAPI for it).
            return what.get();
        } finally {
            SCOPE.restore(before);
        }
    }

    /** The host ticking now, or null. */
    public static AltoClef current() {
        return SCOPE.peek();
    }

    /** AltoClef's static access, as its tasks use it: the host ticking now. */
    public static AltoClef getInstance() {
        return SCOPE.get();
    }

    // Are we in game (playing in a server/world)
    public static boolean inGame() {
        AltoClef current = SCOPE.peek();
        return current != null && current.player != null && current.player.isAlive() && !current.player.hasDisconnected();
    }

    private void initializeBaritoneSettings() {
        getExtraBaritoneSettings().canWalkOnEndPortal(false);
        getClientBaritoneSettings().freeLook.set(false);
        getClientBaritoneSettings().overshootTraverse.set(false);
        getClientBaritoneSettings().allowOvershootDiagonalDescend.set(true);
        getClientBaritoneSettings().allowInventory.set(true);
        getClientBaritoneSettings().allowParkour.set(false);
        getClientBaritoneSettings().allowParkourAscend.set(false);
        getClientBaritoneSettings().allowParkourPlace.set(false);
        getClientBaritoneSettings().allowDiagonalDescend.set(false);
        getClientBaritoneSettings().allowDiagonalAscend.set(false);
        getClientBaritoneSettings().blocksToAvoid.set(new LinkedList<>(List.of(Blocks.FLOWERING_AZALEA, Blocks.AZALEA,
                Blocks.POWDER_SNOW, Blocks.BIG_DRIPLEAF, Blocks.BIG_DRIPLEAF_STEM, Blocks.CAVE_VINES,
                Blocks.CAVE_VINES_PLANT, Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.SWEET_BERRY_BUSH,
                Blocks.WARPED_ROOTS, Blocks.VINE, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.TALL_GRASS, Blocks.LARGE_FERN,
                Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD,
                Blocks.AMETHYST_CLUSTER, Blocks.SCULK, Blocks.SCULK_VEIN)));
        // dont try to break nether portal block
        getClientBaritoneSettings().blocksToAvoidBreaking.get().add(Blocks.NETHER_PORTAL);
        getClientBaritoneSettings().blocksToDisallowBreaking.get().add(Blocks.NETHER_PORTAL);
        getClientBaritoneSettings().fadePath.set(true);
        getClientBaritoneSettings().mineScanDroppedItems.set(false);
        getClientBaritoneSettings().mineDropLoiterDurationMSThanksLouca.set(0L);
        getExtraBaritoneSettings().configurePlaceBucketButDontFall(true);
        getClientBaritoneSettings().randomLooking.set(0.0);
        getClientBaritoneSettings().failureTimeoutMS.reset();
        getClientBaritoneSettings().planAheadFailureTimeoutMS.reset();
        getClientBaritoneSettings().movementTimeoutTicks.reset();
    }

    /**
     * One tick of AltoClef for this companion, with {@code root} as its user task (AltoClef's onClientTick): its inputs
     * readied, its trackers brought up to date, the task tree ticked, then Baritone's processes and the walk, and the keys
     * put on the body.
     */
    public void tick(Task root) {
        within(() -> {
            tickInner(root);
            return null;
        });
    }

    private void tickInner(Task root) {
        {
            SmhRules.prime(player.serverLevel());
            inputControls.onTickPre();
            publishBodyEvents();
            storageTracker.setDirty();
            containerSubTracker.onServerTick();
            miscBlockTracker.tick();
            trackerManager.tick();
            blockScanner.tick();
            userTaskChain.setTask(root);
            taskRunner.tick();
            // Baritone's tick, as its client mixins drive it: its processes, the path, what it presses and where it
            // looks; then the task tree's own keys (its movement keys only while Baritone isn't steering).
            baritone.serverTick();
            CompanionInputDriver driver = driver();
            if (driver != null) {
                // SMH plan 71, §6.2 rule 2: one writer per key. Automatone's forced keys over AltoClef's, as Baritone's
                // over a client's keyboard; Automatone's input handler leaves this body's keys to this (ServerHooks).
                var ioh = baritone.getInputOverrideHandler();
                java.util.Set<Input> forced = java.util.EnumSet.noneOf(Input.class);
                for (Input key : new Input[] {Input.MOVE_FORWARD, Input.MOVE_BACK, Input.MOVE_LEFT, Input.MOVE_RIGHT, Input.JUMP, Input.SNEAK}) {
                    if (ioh.isInputForcedDown(key)) forced.add(key);
                }
                boolean clicks = ioh.isInputForcedDown(Input.CLICK_LEFT) || ioh.isInputForcedDown(Input.CLICK_RIGHT);
                boolean walks = baritone.getPathingBehavior().isPathing();
                com.mineagent.engine.entity.KeyWriters.as("altoclef", () -> inputControls.apply(driver, forced, walks, clicks));
            }
            inputControls.onTickPost();
        }
    }

    /** The task tree and every key and walk let go (its order ended, or another took the body). */
    public void stop(Task root, Task interruptedBy) {
        within(() -> {
            if (root != null) {
                if (interruptedBy == null) root.stop();
                else root.interrupt(interruptedBy);
            }
            userTaskChain.setTask(null);
            // Baritone let go: every process, the path now, and the keys it forces.
            baritone.getPathingBehavior().cancelEverything();
            baritone.getPathingBehavior().forceCancel();
            baritone.getInputOverrideHandler().clearAllKeys();
            CompanionInputDriver driver = driver();
            inputControls.releaseAll(driver);
            if (driver != null) driver.clear(); // what Baritone's walk left on it too
            if (player.containerMenu != player.inventoryMenu) player.closeContainer();
            return null;
        });
    }

    /** SMH: the client's events, from what the body did (a screen opened or shut; a block being broken or let go). */
    private void publishBodyEvents() {
        AbstractContainerMenu menu = player.containerMenu == player.inventoryMenu ? null : player.containerMenu;
        if (menu != lastMenu) {
            EventBus.publish(new ScreenOpenEvent(menu, true));
            EventBus.publish(new ScreenOpenEvent(menu, false));
            lastMenu = menu;
        }
        // What it bumps into (a client mixin on Player.touch posts these): entities touching its body.
        for (Entity e : getWorld().getEntities(player, player.getBoundingBox().inflate(0.05), e -> !e.isSpectator())) {
            EventBus.publish(new com.mineagent.engine.alto.eventbus.events.PlayerCollidedWithEntityEvent(player, e));
        }
        if (player.gameMode instanceof FakePlayerGameMode fake && fake.automaticDestroyPos() != null) {
            EventBus.publish(new BlockBreakingEvent(fake.automaticDestroyPos(), fake.automaticDestroyProgress()));
            wasBreaking = true;
        } else if (wasBreaking) {
            EventBus.publish(new BlockBreakingCancelEvent());
            wasBreaking = false;
        }
    }

    private CompanionInputDriver driver() {
        return TaskContext.inputDriver(agent) instanceof CompanionInputDriver d ? d : null;
    }

    /**
     * SMH: the entities a client in the companion's place would have: within each one's tracking range of it (the
     * server sends a client only those; AltoClef's world is the client's), no further than the server's view distance.
     */
    public Iterable<Entity> visibleEntities() {
        ServerLevel level = getWorld();
        int view = Math.max(2, level.getServer().getPlayerList().getViewDistance()) * 16;
        var box = player.getBoundingBox().inflate(view, level.getHeight(), view);
        List<Entity> out = new java.util.ArrayList<>();
        for (Entity e : level.getEntities((Entity) null, box, e -> true)) {
            int range = Math.min(view, e.getType().clientTrackingRange() * 16);
            double dx = e.getX() - player.getX(), dz = e.getZ() - player.getZ();
            if (dx * dx + dz * dz <= (double) range * range) out.add(e);
        }
        return out;
    }

    /** SMH: whether the companion's own food chain has the body (it eats; the task tree waits). */
    public boolean companionEating() {
        return false;
    }

    /** SMH: whether the companion's own fall chain has the body (it is catching a fall). */
    public boolean companionCatchingFall() {
        return false;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public AgentPlayer getAgent() {
        return agent;
    }

    public TaskRunner getTaskRunner() {
        return taskRunner;
    }

    public UserTaskChain getUserTaskChain() {
        return userTaskChain;
    }

    public BotBehaviour getBehaviour() {
        return botBehaviour;
    }

    public ItemStorageTracker getItemStorage() {
        return storageTracker;
    }

    public EntityTracker getEntityTracker() {
        return entityTracker;
    }

    public CraftingRecipeTracker getCraftingRecipeTracker() {
        return craftingRecipeTracker;
    }

    public BlockScanner getBlockScanner() {
        return blockScanner;
    }

    public SimpleChunkTracker getChunkTracker() {
        return chunkTracker;
    }

    public MiscBlockTracker getMiscBlockTracker() {
        return miscBlockTracker;
    }

    public Baritone getClientBaritone() {
        return baritone;
    }

    public Settings getClientBaritoneSettings() {
        return baritone.settings();
    }

    public AltoClefSettings getExtraBaritoneSettings() {
        return baritone.getExtraBaritoneSettings();
    }

    public com.mineagent.engine.alto.Settings getModSettings() {
        return settings;
    }

    public SlotHandler getSlotHandler() {
        return slotHandler;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ServerLevel getWorld() {
        return player.serverLevel();
    }

    public ServerGameMode getController() {
        return controller;
    }

    public PlayerExtraController getControllerExtras() {
        return extraController;
    }

    public InputControls getInputControls() {
        return inputControls;
    }

    public FoodChain getFoodChain() {
        return foodChain;
    }

    public MobDefenseChain getMobDefenseChain() {
        return mobDefenseChain;
    }

    public MLGBucketFallChain getMLGBucketChain() {
        return mlgBucketChain;
    }

    public void log(String message) {
        Debug.logMessage(message);
    }

    public void logWarning(String message) {
        Debug.logWarning(message);
    }

}
