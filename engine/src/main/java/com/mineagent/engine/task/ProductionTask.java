package com.mineagent.engine.task;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Placement;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.pathing.util.BlockHelper;
import com.mineagent.engine.planning.production.GameKnowledge;
import com.mineagent.engine.planning.production.Production;
import com.mineagent.engine.planning.production.ProductionPlanner;
import com.mineagent.engine.planning.production.ProductionPlanner.Action;
import com.mineagent.engine.planning.production.ProductionPlanner.Step;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes an order the most efficient way and puts it where it was asked (SMH plan 70; the owner, Nov 3: "If I ask for
 * an entire chest of stone bricks, it should find the most efficient way to do that. And be able to do that."). The
 * {@link ProductionPlanner} decides the way from the game's own recipes and drops ({@link GameKnowledge}); this does it,
 * in rounds that fit its pack (at most 8 stacks of the order a round, the plan made again each round from what it has
 * then): fetch from its chests, mine (its mining task; a worn pickaxe is replaced from the plan as it goes), craft (a
 * table put down where one is needed), smelt (furnaces set round it, two blocks out, so it reaches every one, loaded and
 * fuelled as a hopper would, emptied as they finish), and put the round in the chest. It says the plan when it starts,
 * how far it is each round, and what it took when it is done.
 */
public class ProductionTask extends CompanionTask<ProductionTask.ProduceRecord> {
    public static final class ProduceRecord extends TaskRecord {
        /** The item, as the game names it ("minecraft:stone_bricks"). */
        public final String item;
        /** How many; 0: as many as fill the chest. */
        public final int count;
        /** Where they go; null: it keeps them (or puts down a chest of its own, {@link #placeChest}). */
        public final BlockPos chest;
        /** "Fill up a chest", none near: it makes one, puts it down where it works, and fills that. */
        public final boolean placeChest;
        /** Only from what it carries or has seen: no searching (an errand on the way to something else). */
        public final boolean onlySeen;

        public ProduceRecord(String toolCallId, String item, int count, BlockPos chest) {
            this(toolCallId, item, count, chest, false, false);
        }

        public ProduceRecord(String toolCallId, String item, int count, BlockPos chest, boolean placeChest) {
            this(toolCallId, item, count, chest, placeChest, false);
        }

        public ProduceRecord(String toolCallId, String item, int count, BlockPos chest, boolean placeChest, boolean onlySeen) {
            super(toolCallId);
            this.item = item;
            this.count = Math.max(0, count);
            this.chest = chest == null ? null : chest.immutable();
            this.placeChest = placeChest && chest == null;
            this.onlySeen = onlySeen;
        }
    }

    /**
     * The order "food": the cheapest good food it can make here, priced per point of hunger it fills; when the
     * animals for one run out, the next (a chest of food is cooked beef, then porkchops, then chicken...).
     */
    public static final String FOOD = "food";

    /** The most of the order it makes a round: 8 stacks and what goes into them fit its pack. */
    static final int ROUND = 8 * 64;
    /** Up to 16 hours of game time for one order (a chest of stone bricks is about one). */
    static final long LONGEST = 16 * 72_000L;

    private enum Phase { START, ROUND, STEPS, DEPOSIT, DONE, FAILED }

    private Phase phase = Phase.START;
    private boolean begun;
    /** What it is making now (the order's item; for "food", the food it chose). */
    private String itemId;
    private BlockPos chestPos;
    /** First making the chest it fills. */
    private boolean makingChest;
    /** Sources it found no more of: the plan is made again without them. */
    private final java.util.Set<String> without = new java.util.LinkedHashSet<>();
    /** "Hunt for orders" off: every animal left out of its plans (the planner reads this mark). */
    private static final String NO_HUNTING = Production.ENTITY + "*";
    { if (!com.mineagent.engine.settings.Switches.huntForOrders()) without.add(NO_HUNTING); }
    /** Why the last food stopped, for a "food" order whose next food has no room. */
    private String lastWhy = "";
    /** Foods tried and given up on, for a "food" order. */
    private final java.util.Set<String> foodsDone = new java.util.HashSet<>();
    /** What went in, by item (a "food" order may be several). */
    private final Map<String, Integer> deliveredBy = new LinkedHashMap<>();
    /** Animals it hunted, by kind. */
    private final Map<String, Integer> hunted = new LinkedHashMap<>();
    /** Why the order stopped short, said at the end. */
    private String shortBecause = "";
    /** The trip food and torches it had when the order came, kept for itself (MA7.4): -1 until counted. */
    private int keepFood = -1, keepTorches;

    /** What it has for this job: its own trip food and torches, and what other jobs have promised themselves, left out. */
    private com.mineagent.engine.planning.production.Production.Have have(ServerPlayer sp) {
        if (keepFood < 0) {
            keepFood = 0;
            keepTorches = 0;
            if (com.mineagent.engine.settings.Switches.readyCheck()) {
                int food = 0;
                var foods = GameKnowledge.foods().keySet();
                var inv = sp.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (foods.contains(BuiltInRegistries.ITEM.getKey(inv.getItem(i).getItem()).toString())) food += inv.getItem(i).getCount();
                }
                keepFood = Math.min(com.mineagent.engine.settings.Switches.readyFood(), food);
                keepTorches = Math.min(com.mineagent.engine.settings.Switches.readyTorches(), count(sp, net.minecraft.world.item.Items.TORCH));
            }
        }
        return GameKnowledge.have(sp, TaskContext.agentLoop(player), chestPos, record.toolCallId(), keepFood, keepTorches);
    }

    /** What it may put in the chest of this item: what it carries less the trip food and torches it keeps. */
    private int spare(ServerPlayer sp, Item it) {
        if (keepFood <= 0 && keepTorches <= 0) return count(sp, it);
        Map<String, Integer> carried = new LinkedHashMap<>();
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) carried.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        return com.mineagent.engine.planning.production.Reserves.afterFloors(carried, GameKnowledge.foods().keySet(), keepFood, keepTorches)
                .getOrDefault(BuiltInRegistries.ITEM.getKey(it).toString(), 0);
    }

    private void release() {
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.itemReservations().release(record.toolCallId());
    }
    private int target;
    private int delivered;
    private int roundCount;
    private final Deque<Step> todo = new ArrayDeque<>();
    /** Tools the round's plan makes, crafted when the one in hand wears out. */
    private final Map<String, Integer> toolsToMake = new LinkedHashMap<>();
    private Step step;
    private CompanionTask<?> sub;
    private PlayerNav nav;
    private volatile boolean arrived;
    private volatile boolean navFailed;
    private BlockPos workSpot;
    private final List<BlockPos> furnaces = new ArrayList<>();
    private int itemBefore;
    private int stalls;
    private int wait;
    private long stepStarted;
    private String outcome = "";
    private final Map<String, Integer> used = new LinkedHashMap<>();
    private long startedAt;

    public ProductionTask(AgentPlayer player, ProduceRecord record) {
        super(player, record);
    }

    private ServerPlayer sp() {
        return TaskContext.serverPlayer(player);
    }

    private void say(String line) {
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(line);
    }

    @Override
    protected void onStart() {
        ServerPlayer sp = sp();
        if (nav == null) {
            nav = new PlayerNav(player, TaskContext.navCaches(player));
            nav.setListener(new PlayerNav.NavListener() {
                @Override public void onGoalReached() { arrived = true; }
                @Override public void onNavigationFailed(String reason) { navFailed = true; }
            });
        }
        if (!begun) {
            begun = true;
            startedAt = sp.level().getGameTime();
            record.extendDeadlineTo(Math.max(record.deadline(), startedAt + LONGEST));
        }
        if (sub != null) sub.resume();
    }

    @Override
    protected TaskState onTick() {
        if (wait > 0) {
            wait--;
            return TaskState.RUNNING;
        }
        try {
            return switch (phase) {
                case START -> begin();
                case ROUND -> round();
                case STEPS -> steps();
                case DEPOSIT -> deposit();
                case DONE -> TaskState.SUCCESS;
                case FAILED -> TaskState.FAILED;
            };
        } catch (RuntimeException e) {
            return fail("something went wrong: " + e.getMessage());
        }
    }

    private TaskState fail(String why) {
        if (sub != null) sub.interrupt();
        sub = null;
        nav.cancel();
        TaskContext.inputDriver(player).clear();
        outcome = why;
        release();
        say(capital(why) + (delivered > 0 ? String.format(" (%,d %s in so far).", delivered, ProductionPlanner.plural(itemId, delivered)) : "."));
        phase = Phase.FAILED;
        return TaskState.FAILED;
    }

    // ── The order and its rounds ────────────────────────────────────

    private TaskState begin() {
        ServerPlayer sp = sp();
        if (itemId == null) {
            itemId = record.item;
            chestPos = record.chest;
            if (record.onlySeen) without.add(Production.UNSEEN);
            if (record.placeChest && chestPos == null) {
                // A chest of its own, first: made if it has none, put down where it starts.
                if (count(sp, net.minecraft.world.item.Items.CHEST) == 0) {
                    var plan = ProductionPlanner.plan(GameKnowledge.book(sp), GameKnowledge.have(sp, TaskContext.agentLoop(player), null),
                            "minecraft:chest", 1);
                    if (!plan.possible()) return fail("I have no chest to fill and can't make one: " + plan.summary().replaceAll("[.]$", ""));
                    todo.clear();
                    todo.addAll(plan.steps());
                    makingChest = true;
                    phase = Phase.STEPS;
                    step = null;
                    return TaskState.RUNNING;
                }
                return putChestDown();
            }
        }
        if (FOOD.equals(itemId)) {
            String food = chooseFood(sp);
            if (food == null) return fail("I can't see a way to make food here: no animals I may hunt near, and nothing to cook");
            itemId = food;
        }
        if (itemId.startsWith(GameKnowledge.ANY)) {
            String kind = itemId.substring(GameKnowledge.ANY.length()).replace('_', ' ');
            String one = cheapestOf(sp, GameKnowledge.family(itemId));
            if (one == null) return fail("I can't see a way to make any " + kind + " here");
            itemId = one;
        }
        if (BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(itemId)).isEmpty()) {
            return fail("I don't know an item called " + ProductionPlanner.name(itemId));
        }
        target = record.count == 0 ? 0 : record.count - delivered;
        if (chestPos != null) {
            Container c = HopperBlockEntity.getContainerAt(sp.serverLevel(), chestPos);
            if (c == null) return fail("there is no chest at " + chestPos.toShortString());
            int room = room(c, item(itemId));
            if (target == 0) target = room;
            else target = Math.min(target, room);
            if (target == 0) {
                if (delivered > 0) {
                    shortBecause = lastWhy; // no room for the next food; the last one ran out
                    return finish();
                }
                return fail("that chest is already full");
            }
        } else if (target == 0) {
            target = 64;
        }
        // From here, target counts what goes in all told, this item and any before it.
        target += delivered;
        var plan = ProductionPlanner.plan(GameKnowledge.book(sp), have(sp),
                itemId, target - delivered, without);
        if (!plan.possible()) {
            int most = ProductionPlanner.most(GameKnowledge.book(sp), have(sp),
                    itemId, Math.min(ROUND, target - delivered), without);
            if (most <= 0) return fail(plan.summary().replaceAll("[.]$", ""));
            plan = ProductionPlanner.plan(GameKnowledge.book(sp), have(sp),
                    itemId, most, without);
            say("On it, as far as I can: " + plan.summary());
        } else {
            say((delivered > 0 ? "Now " : "On it. ") + plan.summary());
        }
        phase = Phase.ROUND;
        return TaskState.RUNNING;
    }

    /** The chest it made, put down beside where it stands; then the order begins. */
    private TaskState putChestDown() {
        ServerPlayer sp = sp();
        makingChest = false;
        if (count(sp, net.minecraft.world.item.Items.CHEST) == 0) return fail("I made no chest to fill");
        BlockPos at = sp.blockPosition();
        for (int r = 1; r <= 2; r++) {
            for (BlockPos p : ring(at, r)) {
                var level = sp.serverLevel();
                var st = level.getBlockState(p);
                if (!BlockHelper.isPassable(st) || !st.getFluidState().isEmpty() || !BlockHelper.canStandOn(level.getBlockState(p.below()))) continue;
                if (Placement.placeBlock(sp, p.immutable(), Blocks.CHEST.defaultBlockState())) {
                    chestPos = p.immutable();
                    say("I've put a chest down here, at " + chestPos.toShortString() + ", to fill.");
                    var loop = TaskContext.agentLoop(player);
                    if (loop != null) loop.noteChest(level.dimension().location().toString(), p.getX(), p.getY(), p.getZ(), Map.of(), level.getGameTime());
                    wait = 2;
                    phase = Phase.START;
                    return TaskState.RUNNING;
                }
            }
        }
        return fail("I have a chest but nowhere here to put it");
    }

    /** Of a kind's items ("any bed"), the one cheapest to make here (a white bed with white sheep about); null if none. */
    private String cheapestOf(ServerPlayer sp, List<String> items) {
        var book = GameKnowledge.book(sp);
        var have = have(sp);
        String best = null;
        double bestTicks = Double.POSITIVE_INFINITY;
        int n = Math.max(1, Math.min(16, record.count == 0 ? 16 : record.count));
        for (String item : items) {
            var plan = ProductionPlanner.plan(book, have, item, n, without);
            if (plan.possible() && plan.ticks() < bestTicks) {
                bestTicks = plan.ticks();
                best = item;
            }
        }
        return best;
    }

    /**
     * Of the foods the game has, the one it can make here for the least work per point of hunger it fills (meat it
     * can hunt and cook, bread from wheat it has...); null if none.
     */
    private String chooseFood(ServerPlayer sp) {
        var book = GameKnowledge.book(sp);
        var have = have(sp);
        String best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (var e : GameKnowledge.foods().entrySet()) {
            if (foodsDone.contains(e.getKey())) continue;
            var plan = ProductionPlanner.plan(book, have, e.getKey(), 16, without);
            if (!plan.possible()) continue;
            double cost = plan.ticks() / (16.0 * Math.max(1f, e.getValue()));
            if (cost < bestCost) {
                bestCost = cost;
                best = e.getKey();
            }
        }
        return best;
    }

    private TaskState round() {
        ServerPlayer sp = sp();
        int left = target - delivered;
        if (left <= 0) return finish();
        int carried = count(sp, item(itemId));
        roundCount = Math.min(left, ROUND);
        var book = GameKnowledge.book(sp);
        var have = have(sp);
        var plan = ProductionPlanner.plan(book, have, itemId, roundCount, without);
        if (!plan.possible()) {
            if (without.stream().allMatch(NO_HUNTING::equals)) return fail(plan.summary().replaceAll("[.]$", ""));
            // It ran out of something on the way (the cows near here): what it can still make, then the rest.
            int most = ProductionPlanner.most(book, have, itemId, roundCount, without);
            if (most <= 0) return ranOut();
            roundCount = most;
            plan = ProductionPlanner.plan(book, have, itemId, most, without);
        }
        todo.clear();
        toolsToMake.clear();
        // What this round takes from its chests is promised to it (MA7.4).
        var loop = TaskContext.agentLoop(player);
        if (loop != null) {
            loop.itemReservations().release(record.toolCallId());
            Map<String, Integer> fetch = new LinkedHashMap<>();
            for (Step s : plan.steps()) if (s.action() == Action.FETCH) fetch.merge(s.item(), s.count(), Integer::sum);
            loop.itemReservations().reserve(record.toolCallId(), fetch);
        }
        for (Step s : plan.steps()) {
            boolean tool = s.action() == Action.CRAFT && plan.toolsMade().containsKey(s.item());
            if (tool) toolsToMake.merge(s.item(), s.count(), Integer::sum);
            else todo.add(s);
        }
        // What lies on the ground near that the plan counted on: picked up first.
        wanted.clear();
        wanted.add(itemId);
        for (Step st : plan.steps()) {
            if (st.item() != null) wanted.add(st.item());
            if (st.input() != null) wanted.add(st.input());
            if (st.fuel() != null) wanted.add(st.fuel());
            if (st.recipe() != null) {
                for (var r : book.recipes(st.item())) {
                    if (!r.id().equals(st.recipe())) continue;
                    for (var n : r.needs()) wanted.addAll(n.anyOf());
                }
            }
        }
        gleaning = true;
        if (carried >= roundCount && todo.isEmpty()) {
            phase = Phase.STEPS;
            step = null;
            return TaskState.RUNNING;
        }
        phase = Phase.STEPS;
        step = null;
        return TaskState.RUNNING;
    }

    /**
     * No more of what it was using within reach: for "food", the next food; otherwise done with what it has made,
     * saying why.
     */
    private TaskState ranOut() {
        // The first few it ran out of, animals first, in the order it found them gone.
        List<String> gone = new ArrayList<>();
        List<String> ran = new ArrayList<>(without);
        ran.removeIf(w -> w.endsWith("*"));
        for (String w : ran) if (Production.animal(w) && gone.size() < 3) gone.add(ProductionPlanner.plural(w, 2));
        for (String w : ran) if (!Production.animal(w) && gone.size() < 3) gone.add(ProductionPlanner.plural(w, 2));
        String list = gone.size() <= 1 ? String.join("", gone)
                : String.join(", ", gone.subList(0, gone.size() - 1)) + " or " + gone.get(gone.size() - 1);
        String why = "no more " + list + (ran.size() > gone.size() ? " (or anything else to make it from)" : "") + " near here"
                + (ran.stream().anyMatch(Production::animal) ? "; I leave two of each kind to breed" : "");
        lastWhy = why;
        if (FOOD.equals(record.item)) {
            foodsDone.add(itemId);
            String next = chooseFood(sp());
            if (next != null) {
                itemId = next;
                phase = Phase.START;
                return TaskState.RUNNING;
            }
        }
        if (delivered == 0) return fail("I couldn't make any " + ProductionPlanner.name(itemId) + ": " + why);
        shortBecause = why;
        return finish();
    }

    private TaskState finish() {
        ServerPlayer sp = sp();
        long minutes = Math.max(1, (sp.level().getGameTime() - startedAt) / 1_200L);
        List<String> parts0 = new ArrayList<>();
        deliveredBy.forEach((k, v) -> parts0.add(String.format("%,d %s", v, ProductionPlanner.plural(k, v))));
        String what = parts0.isEmpty() ? String.format("%,d %s", delivered, ProductionPlanner.plural(itemId, delivered)) : String.join(", ", parts0);
        boolean full = shortBecause.isEmpty();
        StringBuilder line = new StringBuilder(chestPos != null && record.count == 0 && full ? "Your chest is full: " + what
                : chestPos != null ? "Done: " + what + " in your chest" : "Done: " + what);
        if (!full) line.append(", as much as I could: ").append(shortBecause);
        line.append(". About ").append(minutes).append(minutes == 1 ? " minute" : " minutes");
        if (!used.isEmpty()) {
            List<String> parts = new ArrayList<>();
            used.forEach((k, v) -> parts.add(String.format("%,d %s", v, ProductionPlanner.plural(k, v))));
            line.append("; ").append(String.join(", ", parts)).append(" used");
        }
        List<String> killed = new ArrayList<>(), shorn = new ArrayList<>();
        hunted.forEach((k, v) -> (Production.shearing(k) ? shorn : killed).add(String.format("%,d %s", v, ProductionPlanner.plural(k, v))));
        if (!killed.isEmpty()) line.append("; hunted ").append(String.join(", ", killed));
        if (!shorn.isEmpty()) line.append("; sheared ").append(String.join(", ", shorn));
        say(line.append('.').toString());
        release();
        outcome = what;
        phase = Phase.DONE;
        return TaskState.SUCCESS;
    }

    // ── Steps ───────────────────────────────────────────────────────

    private TaskState steps() {
        if (gleaning) {
            if (glean(sp())) return TaskState.RUNNING;
            gleaning = false;
        }
        if (step == null) {
            if (todo.isEmpty()) {
                if (makingChest) return putChestDown();
                phase = Phase.DEPOSIT;
                return TaskState.RUNNING;
            }
            step = todo.poll();
            stalls = 0;
            stepStarted = sp().level().getGameTime();
            itemBefore = count(sp(), item(step.item()));
            if (step.action() == Action.SMELT) smeltStage = 0;
        }
        TaskState s = switch (step.action()) {
            case FETCH -> fetch();
            case MINE -> mine();
            case HUNT -> hunt();
            case SHEAR -> shear();
            case HARVEST -> harvest();
            case CRAFT -> craft();
            case SMELT -> smelt();
        };
        if (s == TaskState.SUCCESS) {
            step = null;
            return TaskState.RUNNING;
        }
        return s;
    }

    /** From the nearest chest it knows that holds it. */
    private TaskState fetch() {
        ServerPlayer sp = sp();
        var loop = TaskContext.agentLoop(player);
        int have = count(sp, item(step.item())) - itemBefore;
        if (have >= step.count() || loop == null) return TaskState.SUCCESS;
        String dim = sp.serverLevel().dimension().location().toString();
        String path = step.item().substring(step.item().indexOf(':') + 1);
        BlockPos chest = null;
        for (var c : loop.chestMemory().near(dim, sp.getBlockX(), sp.getBlockY(), sp.getBlockZ(), 64.0)) {
            BlockPos p = new BlockPos(c.x(), c.y(), c.z());
            if (!p.equals(chestPos) && c.items().getOrDefault(path, 0) > 0) {
                chest = p;
                break;
            }
        }
        if (chest == null) return TaskState.SUCCESS; // the next round's plan makes what is short
        if (!sp.canInteractWithBlock(chest, 0.0)) return walkTo(chest);
        Container c = HopperBlockEntity.getContainerAt(sp.serverLevel(), chest);
        if (c == null) {
            loop.chestMemory().gone(dim, chest.getX(), chest.getY(), chest.getZ());
            return TaskState.SUCCESS;
        }
        int want = step.count() - have;
        Item it = item(step.item());
        for (int i = 0; i < c.getContainerSize() && want > 0; i++) {
            ItemStack s = c.getItem(i);
            if (!s.is(it)) continue;
            ItemStack taken = c.removeItem(i, Math.min(want, s.getCount()));
            int n = taken.getCount();
            sp.getInventory().add(taken);
            if (!taken.isEmpty()) c.setItem(i, taken); // no room in its pack: put back
            want -= n - taken.getCount();
        }
        c.setChanged();
        TaskContext.syncInventory(sp);
        loop.noteChest(dim, chest.getX(), chest.getY(), chest.getZ(), contents(c), sp.level().getGameTime());
        return TaskState.SUCCESS;
    }

    /** Mining by its mining task, 64 at a time within 32 blocks; a new tool from the plan when the old one is worn. */
    private TaskState mine() {
        ServerPlayer sp = sp();
        int got = count(sp, item(step.item())) - itemBefore;
        if (sub != null) {
            TaskState st = sub.tick();
            if (st == TaskState.RUNNING) return TaskState.RUNNING;
            sub = null;
            int after = count(sp, item(step.item())) - itemBefore;
            if (st != TaskState.SUCCESS && after <= lastGot) {
                if (++stalls >= 3) return outOf(step.block());
            } else {
                stalls = 0;
            }
            lastGot = after;
            return TaskState.RUNNING;
        }
        if (got >= step.count()) {
            used.merge(step.block(), step.blocks(), (a, b) -> a + b);
            return TaskState.SUCCESS;
        }
        if (step.tool() != null && !step.tool().isEmpty() && !hasTool(sp, toolKind(step.tool()))) {
            String made = makeTool(sp, toolKind(step.tool()));
            if (made == null) return fail("my " + toolKind(step.tool()) + " is worn out and I can't make another");
            if (made.isEmpty()) return TaskState.RUNNING; // walking to a table, or one is going down
        }
        int n = Math.min(64, step.count() - got);
        sub = new MineBlockTask(player, new com.mineagent.tools.block.AutoMineTool.MineBlockTaskRecord(
                record.toolCallId() + ":mine", step.block(), n, 32));
        sub.start();
        lastGot = got;
        return TaskState.RUNNING;
    }

    private int lastGot;

    // ── Closest source first (MA7.6) ────────────────────────────────

    /** Items the round's plan uses: what lies near of these is picked up before anything is mined or made. */
    private final java.util.Set<String> wanted = new java.util.HashSet<>();
    private boolean gleaning;
    private final java.util.Set<java.util.UUID> ignoredDrops = new java.util.HashSet<>();
    private java.util.UUID gleanTarget;
    private int gleanTicks;

    /** The nearest wanted thing on the ground, walked to (it is picked up on arrival); false when there is none left. */
    private boolean glean(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        String dim = level.dimension().location().toString();
        net.minecraft.world.entity.item.ItemEntity best = null;
        for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(sp.blockPosition()).inflate(GameKnowledge.GROUND, 6, GameKnowledge.GROUND),
                x -> x.isAlive() && !ignoredDrops.contains(x.getUUID())
                        && wanted.contains(BuiltInRegistries.ITEM.getKey(x.getItem().getItem()).toString())
                        && !com.mineagent.engine.world.AfkFarms.protects(dim, x.getBlockX(), x.getBlockY(), x.getBlockZ()))) {
            if (best == null || e.distanceToSqr(sp) < best.distanceToSqr(sp)) best = e;
        }
        if (best == null) {
            gleanTarget = null;
            chasing = null;
            return false;
        }
        if (!best.getUUID().equals(gleanTarget)) {
            gleanTarget = best.getUUID();
            gleanTicks = 0;
            chasing = null;
            navFailed = false;
        }
        if (navFailed || ++gleanTicks > 300) {
            // Out of its reach (in lava, up a cliff): left.
            ignoredDrops.add(best.getUUID());
            navFailed = false;
            chasing = null;
            return true;
        }
        chase(best.blockPosition(), 0);
        return true;
    }

    /** None of this source left within reach: the round is planned again without it. */
    private TaskState outOf(String source) {
        if (sub != null) sub.interrupt();
        sub = null;
        nav.cancel();
        walkingTo = null;
        TaskContext.inputDriver(player).clear();
        without.add(source);
        without.add(Production.UNSEEN); // it looked round and found none: what it has never seen isn't near either
        todo.clear();
        step = null;
        prey = null;
        lootAt = null;
        phase = Phase.ROUND;
        return TaskState.RUNNING;
    }

    // ── Hunting ─────────────────────────────────────────────────────

    /** How far it looks for animals round where it is, and how far it goes to a herd it has seen. */
    static final int HUNT_RADIUS = 48;
    static final int HERD_SEEN = 256;

    private net.minecraft.world.entity.Entity prey;
    private int preyTicks;
    private BlockPos lootAt;
    private int lootWait;
    private BlockPos chasing;
    private int chaseTicks;
    private BlockPos herdSeen;
    private int herdTrips;
    private final java.util.Set<java.util.UUID> unreachable = new java.util.HashSet<>();

    /**
     * Animals hunted for what they drop (SMH plan 70, MA7.1), as a player does: the nearest grown one of the kind,
     * never a pet, a horse, one that fights back or one with a name, never in your AFK farm, and never the last two of
     * a kind near here, so they breed back; walked up to, hit when the swing is fully charged, its drops picked up.
     * When none are near, it goes to where it last saw some; when there are none there either, the round is planned
     * again without them.
     */
    private TaskState hunt() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        Item it = item(step.item());
        int got = count(sp, it) - itemBefore;
        if (pickingUp(sp)) return TaskState.RUNNING;
        if (got >= step.count()) {
            nav.cancel();
            TaskContext.inputDriver(player).clear();
            return TaskState.SUCCESS;
        }
        String kind = step.block().substring(Production.ENTITY.length());
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(kind)).orElse(null);
        if (type == null) return outOf(step.block());
        if (prey != null && !prey.isAlive()) {
            hunted.merge(step.block(), 1, Integer::sum);
            lootAt = prey.blockPosition().immutable();
            lootWait = 100;
            prey = null;
            chasing = null;
            navFailed = false;
            wait = 12; // its drops can be picked up after 10 ticks
            return TaskState.RUNNING;
        }
        if (prey != null && (prey.isRemoved() || !GameKnowledge.huntable(prey) || ++preyTicks > 900)) {
            unreachable.add(prey.getUUID());
            prey = null;
        }
        if (prey == null) {
            prey = choosePrey(sp, type);
            preyTicks = 0;
            chasing = null;
            navFailed = false;
            if (prey == null) {
                // None to take here: where it last saw a herd of them, once or twice; then it is out of them.
                var loop = TaskContext.agentLoop(player);
                if (herdSeen == null && loop != null && herdTrips < 2) {
                    String dim = level.dimension().location().toString();
                    for (var f : loop.placeKeeper().sightings().nearest(ResourceLocation.tryParse(kind).getPath(), dim,
                            sp.getBlockX(), sp.getBlockY(), sp.getBlockZ(), 4, HERD_SEEN)) {
                        BlockPos at = new BlockPos(f.sighting().x(), f.sighting().y(), f.sighting().z());
                        if (f.sighting().kind() == com.mineagent.engine.memory.Sightings.Kind.ANIMAL && at.distSqr(sp.blockPosition()) > 24 * 24) {
                            herdSeen = at;
                            herdTrips++;
                            break;
                        }
                    }
                }
                if (herdSeen == null) return outOf(step.block());
                if (sp.blockPosition().distSqr(herdSeen) <= 8 * 8 || navFailed) {
                    herdSeen = null;
                    chasing = null;
                    navFailed = false;
                    if (herdTrips >= 2) return outOf(step.block());
                    return TaskState.RUNNING;
                }
                chase(herdSeen, 4);
                return TaskState.RUNNING;
            }
        }
        if (!com.mineagent.engine.act.Interaction.inEntityReach(sp, prey) || !sp.hasLineOfSight(prey)) {
            chase(prey.blockPosition(), 1);
            if (navFailed) {
                unreachable.add(prey.getUUID());
                prey = null;
                navFailed = false;
            }
            return TaskState.RUNNING;
        }
        nav.cancel();
        chasing = null;
        TaskContext.inputDriver(player).clear();
        com.mineagent.engine.survival.MobDefenseChain.equipWeapon(sp);
        var to = prey.position().add(0, prey.getBbHeight() * 0.5, 0).subtract(sp.getEyePosition());
        sp.setYRot((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
        sp.setXRot((float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z))));
        sp.setYHeadRot(sp.getYRot());
        if (sp.getAttackStrengthScale(0.5f) >= 0.9f) com.mineagent.engine.act.Interaction.attackEntity(sp, prey);
        return TaskState.RUNNING;
    }

    /** Its drops first, after a kill or a shearing: they go in a few seconds' walk. True while it is picking them up. */
    private boolean pickingUp(ServerPlayer sp) {
        if (lootAt == null) return false;
        var drops = sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(lootAt).inflate(5, 3, 5), e -> e.isAlive());
        if (drops.isEmpty() || --lootWait <= 0 || navFailed) {
            lootAt = null;
            chasing = null;
            nav.cancel();
            return false;
        }
        net.minecraft.world.entity.item.ItemEntity near = drops.get(0);
        for (var d : drops) if (d.distanceToSqr(sp) < near.distanceToSqr(sp)) near = d;
        chase(near.blockPosition(), 0);
        return true;
    }

    /**
     * Wool by shearing (SMH plan 70, MA7.3): the nearest sheep of the colour with its wool grown, walked up to, shears
     * in hand, sheared and left to grow it back; the wool picked up. Shears are made first if the plan made them.
     */
    private TaskState shear() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        int got = count(sp, item(step.item())) - itemBefore;
        if (pickingUp(sp)) return TaskState.RUNNING;
        if (got >= step.count()) {
            nav.cancel();
            TaskContext.inputDriver(player).clear();
            return TaskState.SUCCESS;
        }
        if (!hasTool(sp, "shears")) {
            String made = makeTool(sp, "shears");
            if (made == null) return fail("I have no shears and can't make any");
            if (made.isEmpty()) return TaskState.RUNNING;
        }
        String[] parts = step.block().split(":");
        String colour = parts.length > 3 ? parts[3] : "white";
        String dim = level.dimension().location().toString();
        if (prey == null || !prey.isAlive() || !(prey instanceof net.minecraft.world.entity.animal.Sheep sh) || !sh.readyForShearing()
                || ++preyTicks > 900) {
            if (prey != null && preyTicks > 900) unreachable.add(prey.getUUID());
            prey = null;
            preyTicks = 0;
            chasing = null;
            navFailed = false;
            net.minecraft.world.entity.Entity best = null;
            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Sheep.class,
                    new net.minecraft.world.phys.AABB(sp.blockPosition()).inflate(HUNT_RADIUS, 24, HUNT_RADIUS),
                    x -> x.isAlive() && x.readyForShearing() && x.getColor().getName().equals(colour) && !unreachable.contains(x.getUUID())
                            && !com.mineagent.engine.world.AfkFarms.protects(dim, x.getBlockX(), x.getBlockY(), x.getBlockZ()))) {
                if (best == null || e.distanceToSqr(sp) < best.distanceToSqr(sp)) best = e;
            }
            if (best == null) return outOf(step.block());
            prey = best;
        }
        if (!com.mineagent.engine.act.Interaction.inEntityReach(sp, prey) || !sp.hasLineOfSight(prey)) {
            chase(prey.blockPosition(), 1);
            if (navFailed) {
                unreachable.add(prey.getUUID());
                prey = null;
                navFailed = false;
            }
            return TaskState.RUNNING;
        }
        nav.cancel();
        chasing = null;
        TaskContext.inputDriver(player).clear();
        hold(sp, net.minecraft.world.item.Items.SHEARS);
        var result = com.mineagent.engine.act.Interaction.interactEntity(sp, prey, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (result.consumesAction()) {
            hunted.merge(step.block(), 1, Integer::sum);
            lootAt = prey.blockPosition().immutable();
            lootWait = 80;
            prey = null;
            wait = 12;
        } else {
            unreachable.add(prey.getUUID());
            prey = null;
        }
        TaskContext.syncInventory(sp);
        return TaskState.RUNNING;
    }

    // ── Harvesting (MA8.5) ──────────────────────────────────────────

    private BlockPos crop;
    private BlockPos replant;

    /**
     * Ripe crops harvested and planted again (SMH plan 70, MA8.5): the nearest ripe one of the kind, broken by hand,
     * its drops picked up, and a seed of it put back in the farmland, so the field grows again. Never a green one, and
     * never in your AFK farm.
     */
    private TaskState harvest() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        int got = count(sp, item(step.item())) - itemBefore;
        if (replant != null) {
            replantAt(sp);
            return TaskState.RUNNING;
        }
        if (pickingUp(sp)) return TaskState.RUNNING;
        if (got >= step.count()) {
            nav.cancel();
            TaskContext.inputDriver(player).clear();
            return TaskState.SUCCESS;
        }
        var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(step.block().substring(Production.CROP.length()))).orElse(null);
        if (!(block instanceof net.minecraft.world.level.block.CropBlock kind)) return outOf(step.block());
        String dim = level.dimension().location().toString();
        if (crop == null || !(level.getBlockState(crop).getBlock() == block && kind.isMaxAge(level.getBlockState(crop)))) {
            crop = null;
            double best = Double.MAX_VALUE;
            BlockPos at = sp.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-24, -4, -24), at.offset(24, 4, 24))) {
                var st = level.getBlockState(p);
                if (st.getBlock() != block || !kind.isMaxAge(st) || AfkFarmsProtect(dim, p)) continue;
                double d = p.distSqr(at);
                if (d < best) {
                    best = d;
                    crop = p.immutable();
                }
            }
            if (crop == null) return outOf(step.block());
            chasing = null;
            navFailed = false;
        }
        if (crop.distToCenterSqr(sp.getEyePosition()) > 4.0 * 4.0) {
            chase(crop, 1);
            if (navFailed) {
                navFailed = false;
                return outOf(step.block());
            }
            return TaskState.RUNNING;
        }
        nav.cancel();
        chasing = null;
        TaskContext.inputDriver(player).clear();
        if (BlockDigger.startBreakingDetailed(sp, crop).allowed() || level.getBlockState(crop).isAir()) {
            if (level.getBlockState(crop).isAir()) {
                lootAt = crop;
                lootWait = 40;
                replant = crop;
                crop = null;
                wait = 2;
            }
        }
        return TaskState.RUNNING;
    }

    private static boolean AfkFarmsProtect(String dim, BlockPos p) {
        return com.mineagent.engine.world.AfkFarms.protects(dim, p.getX(), p.getY(), p.getZ());
    }

    /** A seed of the crop back in the farmland it came from. */
    private void replantAt(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        BlockPos at = replant;
        var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(step.block().substring(Production.CROP.length()))).orElse(null);
        Item seed = block == null ? null : block.asItem();
        if (seed == null || seed == net.minecraft.world.item.Items.AIR || !level.getBlockState(at).isAir()
                || !level.getBlockState(at.below()).is(Blocks.FARMLAND)) {
            replant = null;
            return;
        }
        if (count(sp, seed) == 0) {
            if (!pickingUp(sp)) replant = null; // its seeds come with its drops; none left: the cell stays bare
            return;
        }
        hold(sp, seed);
        sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, net.minecraft.world.phys.Vec3.atCenterOf(at.below()).add(0, 0.5, 0));
        com.mineagent.engine.act.Interaction.interactBlock(sp, at.below(), net.minecraft.world.InteractionHand.MAIN_HAND);
        TaskContext.syncInventory(sp);
        replant = null;
    }

    /** An item into its hand from its pack, as a player's hotbar swap. */
    private static void hold(ServerPlayer sp, Item item) {
        var inv = sp.getInventory();
        if (inv.getSelected().is(item)) return;
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) {
            if (!inv.getItem(i).is(item)) continue;
            if (i < 9) {
                inv.selected = i;
            } else {
                ItemStack displaced = inv.getItem(inv.selected);
                inv.setItem(inv.selected, inv.getItem(i));
                inv.setItem(i, displaced);
            }
            TaskContext.syncInventory(sp);
            return;
        }
    }

    /** The nearest one it may take: grown, not a pet, not in your AFK farm, and not one of the last two near here. */
    private net.minecraft.world.entity.Entity choosePrey(ServerPlayer sp, net.minecraft.world.entity.EntityType<?> type) {
        ServerLevel level = sp.serverLevel();
        String dim = level.dimension().location().toString();
        var all = level.getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(sp.blockPosition()).inflate(HUNT_RADIUS, 24, HUNT_RADIUS),
                e -> e.getType() == type && GameKnowledge.huntable(e)
                        && !com.mineagent.engine.world.AfkFarms.protects(dim, e.getBlockX(), e.getBlockY(), e.getBlockZ()));
        if (all.size() <= GameKnowledge.LEAVE) return null;
        net.minecraft.world.entity.Entity best = null;
        for (var e : all) {
            if (unreachable.contains(e.getUUID())) continue;
            if (best == null || e.distanceToSqr(sp) < best.distanceToSqr(sp)) best = e;
        }
        return best;
    }

    /** Toward something that moves: the way made again when it has moved or every two seconds. */
    private void chase(BlockPos p, int radius) {
        if (chasing == null || chasing.distManhattan(p) >= 2 || ++chaseTicks >= 40) {
            chasing = p.immutable();
            chaseTicks = 0;
            arrived = false;
            navFailed = false;
            if (radius <= 0) nav.navigateTo(p.getX(), p.getY(), p.getZ());
            else nav.navigateNear(p.getX(), p.getY(), p.getZ(), radius);
        }
        nav.tick();
    }

    private static String toolKind(String tool) {
        String t = tool.substring(tool.indexOf(':') + 1);
        int u = t.lastIndexOf('_');
        return u >= 0 ? t.substring(u + 1) : t;
    }

    private static boolean hasTool(ServerPlayer sp, String kind) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            if (!s.isEmpty() && (id.endsWith("_" + kind) || id.equals(kind)) && s.isDamageableItem()
                    && s.getMaxDamage() - s.getDamageValue() > 1) return true;
        }
        return false;
    }

    /**
     * The best tool of this kind the plan makes that it has the makings of now; "" while it gets to a table; null if
     * none can be made.
     */
    private String makeTool(ServerPlayer sp, String kind) {
        List<String> tools = new ArrayList<>(toolsToMake.keySet());
        tools.sort((a, b) -> ProductionPlanner.tierOf(b) - ProductionPlanner.tierOf(a));
        for (String t : tools) {
            if (toolsToMake.getOrDefault(t, 0) <= 0 || !(t.endsWith("_" + kind) || t.endsWith(":" + kind))) continue;
            var recipe = recipeFor(sp, t);
            if (recipe == null) continue;
            String r = craftOnce(sp, recipe, 1);
            if (r == null) continue;
            if (r.isEmpty()) return "";
            toolsToMake.merge(t, -1, Integer::sum);
            used.merge(t, 1, Integer::sum);
            return t;
        }
        return null;
    }

    private String recipeFor(ServerPlayer sp, String item) {
        Production.Recipe best = null;
        for (var r : GameKnowledge.book(sp).recipes(item)) if (r.kind() == Production.Kind.CRAFT) best = best == null ? r : best;
        return best == null ? null : best.id();
    }

    private TaskState craft() {
        ServerPlayer sp = sp();
        int got = count(sp, item(step.item())) - itemBefore;
        if (got >= step.count()) return TaskState.SUCCESS;
        int batchesLeft = Math.max(1, (int) Math.ceil((step.count() - got) / (double) Math.max(1, step.count() / Math.max(1, step.batches()))));
        String r = craftOnce(sp, step.recipe(), Math.min(64, batchesLeft));
        if (r == null) return fail("I couldn't craft " + ProductionPlanner.name(step.item()) + ": " + lastCraftError);
        return TaskState.RUNNING;
    }

    private String lastCraftError = "";

    /**
     * One call of its craft tool: "ok" when it crafted, "" when it is first getting to a table (or putting one down),
     * null when it can't.
     */
    private String craftOnce(ServerPlayer sp, String recipeId, int batches) {
        var args = new JsonObject();
        args.addProperty("recipe_id", recipeId);
        args.addProperty("count", Math.max(1, Math.min(64, batches)));
        args.addProperty("bypass_reuse_check", true);
        args.addProperty("purpose", "production order");
        String[] reply = {null};
        new com.mineagent.tools.crafting.CraftTool().onServerCall(record.toolCallId() + ":craft", args, player, r -> reply[0] = r);
        JsonObject o;
        try {
            o = JsonParser.parseString(reply[0] == null ? "{}" : reply[0]).getAsJsonObject();
        } catch (RuntimeException e) {
            o = new JsonObject();
        }
        if (o.has("crafted_batches") && o.get("crafted_batches").getAsInt() > 0) return "ok";
        String code = o.has("code") ? o.get("code").getAsString() : "";
        String error = o.has("error") ? o.get("error").getAsString() : reply[0] == null ? "no reply" : reply[0];
        if (code.equals("crafting_table_out_of_reach") && o.has("nearest_table")) {
            var t = o.getAsJsonObject("nearest_table");
            walkTo(new BlockPos(t.get("x").getAsInt(), t.get("y").getAsInt(), t.get("z").getAsInt()));
            return "";
        }
        if (error.contains("No crafting table")) {
            if (placeNear(sp, Blocks.CRAFTING_TABLE.defaultBlockState(), 1)) {
                wait = 2;
                return "";
            }
            lastCraftError = "I have no crafting table and nowhere to put one";
            return null;
        }
        lastCraftError = error;
        return null;
    }

    // ── Smelting ────────────────────────────────────────────────────

    private int smeltStage;
    private long smeltDeadline;
    /** Per furnace: its share of the smelt, what is loaded of it, the fuel given for it. */
    private int[] share = new int[0], loaded = new int[0], fuelled = new int[0];

    /** Furnaces set round it, loaded with the input and the fuel, emptied as they finish. */
    private TaskState smelt() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        Item out = item(step.item()), in = item(step.input()), fuel = item(step.fuel());
        if (smeltStage == 0) {
            // Where it works: here, the first time; back here after.
            if (workSpot == null) workSpot = standing(sp);
            if (!standing(sp).equals(workSpot)) return walkTo(workSpot, 0);
            var station = station(sp);
            furnaces.removeIf(f -> !level.getBlockState(f).is(station));
            for (BlockPos p : ring(workSpot, 2)) {
                if (furnaces.size() >= step.furnaces()) break;
                if (furnaces.contains(p) || !level.getBlockState(p).is(station)) continue;
                furnaces.add(p.immutable());
            }
            // Then ones already standing near (the plan counted them), nearest first.
            if (furnaces.size() < step.furnaces()) {
                List<BlockPos> near = new ArrayList<>();
                for (BlockPos p : BlockPos.betweenClosed(workSpot.offset(-12, -4, -12), workSpot.offset(12, 4, 12))) {
                    if (level.isLoaded(p) && level.getBlockState(p).is(station) && !furnaces.contains(p)) near.add(p.immutable());
                }
                near.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(workSpot)));
                for (BlockPos p : near) {
                    if (furnaces.size() >= step.furnaces()) break;
                    furnaces.add(p);
                }
            }
            // Put down from its pack, round where it works: every free spot tried (a cow on one, a fence by another), the
            // nearest ring first.
            for (int r = 2; r <= 3 && furnaces.size() < step.furnaces(); r++) {
                for (BlockPos p : ring(workSpot, r)) {
                    if (furnaces.size() >= step.furnaces() || count(sp, station.asItem()) == 0) break;
                    if (furnaces.contains(p)) continue;
                    var st = level.getBlockState(p);
                    if (!BlockHelper.isPassable(st) || !st.getFluidState().isEmpty() || !BlockHelper.canStandOn(level.getBlockState(p.below()))) continue;
                    if (Placement.placeBlock(sp, p.immutable(), station.defaultBlockState())) furnaces.add(p.immutable());
                }
            }
            if (furnaces.isEmpty()) return fail("I have no furnace to smelt " + ProductionPlanner.name(step.input()) + " in");
            // Each furnace's share, in whole pieces of fuel's worth, and the fuel for it: as the plan priced it.
            int fuelTicks = fuelTicks(sp); // a smoker or blast furnace burns it twice as fast
            var split = com.mineagent.engine.planning.production.FurnaceSplit.split(step.count(), furnaces.size(), cookTicks(sp), fuelTicks);
            while (furnaces.size() > split.furnaces()) furnaces.remove(furnaces.size() - 1);
            share = split.items().clone();
            loaded = new int[share.length];
            fuelled = new int[share.length];
            smeltStage = 1;
            smeltDeadline = level.getGameTime() + split.ticks() * 2 + 1_200L;
            return TaskState.RUNNING;
        }
        // Each pass: take what is done, top up each furnace's input to its share, and its fuel to what that burns.
        boolean anyBusy = false;
        int fuelTicks = fuelTicks(sp); // a smoker or blast furnace burns it twice as fast
        int cook = cookTicks(sp);
        for (int i = 0; i < furnaces.size(); i++) {
            BlockPos f = furnaces.get(i);
            if (!(level.getBlockEntity(f) instanceof AbstractFurnaceBlockEntity furnace)) continue;
            if (!sp.canInteractWithBlock(f, 0.0)) return walkTo(f, 1);
            ItemStack result = furnace.getItem(2);
            if (!result.isEmpty()) {
                ItemStack taken = furnace.removeItem(2, result.getCount());
                sp.getInventory().add(taken);
                if (!taken.isEmpty()) furnace.setItem(2, taken);
                furnace.awardUsedRecipesAndPopExperience(sp);
            }
            int room = 64 - furnace.getItem(0).getCount();
            int add = Math.min(room, share[i] - loaded[i]);
            if (add > 0 && count(sp, in) > 0) {
                int before = count(sp, in);
                insert(sp, furnace, in, add, Direction.UP);
                loaded[i] += before - count(sp, in);
            }
            int fuelFor = (int) Math.ceil(loaded[i] * (double) cook / fuelTicks);
            int addFuel = fuelFor - fuelled[i];
            if (addFuel > 0 && count(sp, fuel) > 0) {
                int before = count(sp, fuel);
                insert(sp, furnace, fuel, addFuel, Direction.NORTH);
                int gave = before - count(sp, fuel);
                fuelled[i] += gave;
                used.merge(step.fuel(), gave, Integer::sum);
            }
            if (!furnace.getItem(0).isEmpty() || !furnace.getItem(2).isEmpty() || loaded[i] < share[i]) anyBusy = true;
        }
        TaskContext.syncInventory(sp);
        int made = count(sp, out) - itemBefore;
        if (made >= step.count() || (!anyBusy && count(sp, in) == 0)) return TaskState.SUCCESS;
        if (level.getGameTime() > smeltDeadline) return fail("the furnaces stopped before the " + ProductionPlanner.name(step.item()) + " was done");
        wait = 20; // a look every second
        return TaskState.RUNNING;
    }

    private int fuelTicks(ServerPlayer sp) {
        int t = Math.max(1, GameKnowledge.book(sp).fuelTicks().getOrDefault(step.fuel(), 1_600));
        return station(sp) == Blocks.FURNACE ? t : Math.max(1, t / 2);
    }

    /** What this smelt is done in: a furnace, or the smoker or blast furnace its recipe is for. */
    private net.minecraft.world.level.block.Block station(ServerPlayer sp) {
        for (var r : GameKnowledge.book(sp).recipes(step.item())) {
            if (!r.id().equals(step.recipe())) continue;
            if (r.kind() == Production.Kind.SMOKE) return Blocks.SMOKER;
            if (r.kind() == Production.Kind.BLAST) return Blocks.BLAST_FURNACE;
        }
        return Blocks.FURNACE;
    }

    private int cookTicks(ServerPlayer sp) {
        for (var r : GameKnowledge.book(sp).recipes(step.item())) if (r.id().equals(step.recipe())) return Math.max(1, r.cookTicks());
        return 200;
    }

    private static void insert(ServerPlayer sp, Container into, Item item, int count, Direction face) {
        var inv = sp.getInventory();
        int left = count;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(item)) continue;
            int take = Math.min(left, s.getCount());
            ItemStack moving = s.split(take);
            ItemStack rest = HopperBlockEntity.addItem(null, into, moving, face);
            int moved = take - rest.getCount();
            if (!rest.isEmpty()) s.grow(rest.getCount());
            if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            left -= moved;
            if (!rest.isEmpty()) break;
        }
        into.setChanged();
    }

    // ── Delivery ────────────────────────────────────────────────────

    private TaskState deposit() {
        ServerPlayer sp = sp();
        Item it = item(itemId);
        if (chestPos == null) {
            int kept = Math.min(roundCount, count(sp, it));
            delivered += kept;
            deliveredBy.merge(itemId, kept, Integer::sum);
            phase = Phase.ROUND;
            return TaskState.RUNNING;
        }
        if (!sp.canInteractWithBlock(chestPos, 0.0)) return walkTo(chestPos);
        Container c = HopperBlockEntity.getContainerAt(sp.serverLevel(), chestPos);
        if (c == null) return fail("the chest at " + chestPos.toShortString() + " is gone");
        int want = Math.min(target - delivered, spare(sp, it));
        int before = want;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize() && want > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(it)) continue;
            int take = Math.min(want, s.getCount());
            ItemStack rest = HopperBlockEntity.addItem(null, c, s.split(take), null);
            if (!rest.isEmpty()) s.grow(rest.getCount());
            if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            want -= take - rest.getCount();
            if (!rest.isEmpty()) break;
        }
        c.setChanged();
        TaskContext.syncInventory(sp);
        int put = before - want;
        delivered += put;
        if (put > 0) deliveredBy.merge(itemId, put, Integer::sum);
        var loop = TaskContext.agentLoop(player);
        if (loop != null) {
            loop.noteChest(sp.serverLevel().dimension().location().toString(), chestPos.getX(), chestPos.getY(),
                    chestPos.getZ(), contents(c), sp.level().getGameTime());
        }
        if (put == 0 && room(c, it) == 0) return finish(); // full
        if (delivered < target) {
            say(String.format("%,d of %,d %s in the chest.", delivered, target, ProductionPlanner.plural(itemId, target)));
            if (put == 0) return fail("I made the " + ProductionPlanner.name(itemId) + " but couldn't put them in the chest");
        }
        phase = Phase.ROUND;
        return TaskState.RUNNING;
    }

    // ── Moving and putting things down ──────────────────────────────

    private BlockPos walkingTo;

    private TaskState walkTo(BlockPos p) {
        return walkTo(p, 2);
    }

    /** Toward {@code p}, to within {@code radius} (0: onto it); RUNNING until there, then RUNNING once more. */
    private TaskState walkTo(BlockPos p, int radius) {
        if (walkingTo == null || !walkingTo.equals(p)) {
            walkingTo = p.immutable();
            arrived = false;
            navFailed = false;
            if (radius <= 0) nav.navigateTo(p.getX(), p.getY(), p.getZ());
            else nav.navigateNear(p.getX(), p.getY(), p.getZ(), radius);
            return TaskState.RUNNING;
        }
        if (navFailed) {
            walkingTo = null;
            return fail("I can't get to " + p.toShortString());
        }
        if (arrived && !nav.isNavigating()) {
            walkingTo = null;
            return TaskState.RUNNING;
        }
        nav.tick();
        return TaskState.RUNNING;
    }

    /**
     * A block it carries put down near it: the ring {@code r} round it first, then further out to 3; only where a block
     * can go (air, grass), never over a crop or into water.
     */
    private static boolean placeNear(ServerPlayer sp, net.minecraft.world.level.block.state.BlockState state, int r) {
        ServerLevel level = sp.serverLevel();
        for (int ring = Math.max(1, r); ring <= 3; ring++) {
            for (BlockPos p : ring(standing(sp), ring)) {
                var st = level.getBlockState(p);
                if ((st.isAir() || st.canBeReplaced()) && !(st.getBlock() instanceof net.minecraft.world.level.block.CropBlock)
                        && st.getFluidState().isEmpty() && BlockHelper.canStandOn(level.getBlockState(p.below()))
                        && Placement.placeBlock(sp, p.immutable(), state)) return true;
            }
        }
        return false;
    }

    /**
     * The cell its body is in: on farmland, a path or soul sand (less than a block high) its block position is that
     * block, so the cell above.
     */
    static BlockPos standing(ServerPlayer sp) {
        BlockPos p = sp.blockPosition();
        return BlockHelper.isPassable(sp.serverLevel().getBlockState(p)) ? p : p.above();
    }

    /** The cells at Chebyshev distance {@code r} round {@code c}, at its height. */
    static List<BlockPos> ring(BlockPos c, int r) {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == r) out.add(c.offset(dx, 0, dz));
            }
        }
        return out;
    }

    // ── Counting ────────────────────────────────────────────────────

    static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
    }

    static int count(ServerPlayer sp, Item item) {
        int n = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
        return n;
    }

    /** How many more of {@code item} the container holds. */
    static int room(Container c, Item item) {
        int max = new ItemStack(item).getMaxStackSize(), n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) n += max;
            else if (s.is(item)) n += Math.max(0, max - s.getCount());
        }
        return n;
    }

    private static Map<String, Integer> contents(Container c) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty()) items.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        return items;
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** How many it has put where they go (the game tests). */
    public int delivered() {
        return delivered;
    }

    @Override
    protected String successMessage() {
        return "{\"success\":true,\"delivered\":" + delivered + ",\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected String cancelledMessage() {
        release(); // stopped: what it had counted on from its chests is free again
        return "{\"success\":false,\"delivered\":" + delivered + ",\"note\":\"stopped\"}";
    }

    @Override
    protected String timeoutMessage() {
        release();
        return "{\"success\":false,\"delivered\":" + delivered + ",\"note\":\"out of time\"}";
    }

    @Override
    protected String failureMessage() {
        return "{\"success\":false,\"delivered\":" + delivered + ",\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected void onInterrupt() {
        if (sub != null) sub.interrupt();
        if (nav != null) nav.cancel();
        // The walk it was on is gone: made again from where it stands when it carries on (a fight, then back to it).
        walkingTo = null;
        chasing = null;
        prey = null;
        TaskContext.inputDriver(player).clear();
    }

    /** What it is doing, from the facts (MA6.5): the order, the step, how far. */
    @Override
    public com.mineagent.api.task.TaskSnapshot snapshot() {
        String what = itemId == null ? ProductionPlanner.name(record.item) : ProductionPlanner.plural(itemId, Math.max(2, target));
        String doing = step == null ? phase.name().toLowerCase(java.util.Locale.ROOT) : switch (step.action()) {
            case FETCH -> "fetching " + ProductionPlanner.plural(step.item(), step.count()) + " from my chests";
            case MINE -> "mining " + ProductionPlanner.plural(step.block(), step.blocks());
            case HUNT -> "hunting " + ProductionPlanner.plural(step.block(), step.blocks());
            case SHEAR -> "shearing " + ProductionPlanner.plural(step.block(), step.blocks());
            case HARVEST -> "harvesting " + ProductionPlanner.name(step.block());
            case CRAFT -> "crafting " + ProductionPlanner.plural(step.item(), step.count());
            case SMELT -> "smelting " + ProductionPlanner.plural(step.input(), step.count());
        };
        String summary = String.format(java.util.Locale.ROOT, "making %s%s: %s, %,d of %,d in", what,
                chestPos != null ? " for the chest at " + chestPos.toShortString() : "", doing, delivered, Math.max(target, 0));
        return com.mineagent.api.task.TaskSnapshot.progress(doing, summary, delivered, Math.max(1, target),
                chestPos == null ? null : chestPos.getX(), chestPos == null ? null : chestPos.getY(), chestPos == null ? null : chestPos.getZ(),
                outcome.isEmpty() || phase != Phase.FAILED ? null : outcome, null, delivered * 64L + (step == null ? 0 : step.hashCode() & 63));
    }
}
