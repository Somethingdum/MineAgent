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
        /** Where they go; null: it keeps them. */
        public final BlockPos chest;

        public ProduceRecord(String toolCallId, String item, int count, BlockPos chest) {
            super(toolCallId);
            this.item = item;
            this.count = Math.max(0, count);
            this.chest = chest == null ? null : chest.immutable();
        }
    }

    /** The most of the order it makes a round: 8 stacks and what goes into them fit its pack. */
    static final int ROUND = 8 * 64;
    /** Up to 16 hours of game time for one order (a chest of stone bricks is about one). */
    static final long LONGEST = 16 * 72_000L;

    private enum Phase { START, ROUND, STEPS, DEPOSIT, DONE, FAILED }

    private Phase phase = Phase.START;
    private boolean begun;
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
                case START -> start();
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
        say(capital(why) + (delivered > 0 ? String.format(" (%,d %s in so far).", delivered, ProductionPlanner.plural(record.item, delivered)) : "."));
        phase = Phase.FAILED;
        return TaskState.FAILED;
    }

    // ── The order and its rounds ────────────────────────────────────

    private TaskState start() {
        ServerPlayer sp = sp();
        if (BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(record.item)).isEmpty()) {
            return fail("I don't know an item called " + ProductionPlanner.name(record.item));
        }
        target = record.count;
        if (record.chest != null) {
            Container c = HopperBlockEntity.getContainerAt(sp.serverLevel(), record.chest);
            if (c == null) return fail("there is no chest at " + record.chest.toShortString());
            int room = room(c, item(record.item));
            if (target == 0) target = room;
            else target = Math.min(target, room);
            if (target == 0) return fail("that chest is already full");
        } else if (target == 0) {
            target = 64;
        }
        var plan = ProductionPlanner.plan(GameKnowledge.book(sp), GameKnowledge.have(sp, TaskContext.agentLoop(player), record.chest),
                record.item, target);
        if (!plan.possible()) return fail(plan.summary().replaceAll("[.]$", ""));
        say("On it. " + plan.summary());
        phase = Phase.ROUND;
        return TaskState.RUNNING;
    }

    private TaskState round() {
        ServerPlayer sp = sp();
        int left = target - delivered;
        if (left <= 0) return finish();
        int carried = count(sp, item(record.item));
        roundCount = Math.min(left, ROUND);
        var plan = ProductionPlanner.plan(GameKnowledge.book(sp), GameKnowledge.have(sp, TaskContext.agentLoop(player), record.chest),
                record.item, roundCount);
        if (!plan.possible()) return fail(plan.summary().replaceAll("[.]$", ""));
        todo.clear();
        toolsToMake.clear();
        for (Step s : plan.steps()) {
            boolean tool = s.action() == Action.CRAFT && plan.toolsMade().containsKey(s.item());
            if (tool) toolsToMake.merge(s.item(), s.count(), Integer::sum);
            else todo.add(s);
        }
        if (carried >= roundCount && todo.isEmpty()) {
            phase = Phase.DEPOSIT;
            return TaskState.RUNNING;
        }
        phase = Phase.STEPS;
        step = null;
        return TaskState.RUNNING;
    }

    private TaskState finish() {
        ServerPlayer sp = sp();
        long minutes = Math.max(1, (sp.level().getGameTime() - startedAt) / 1_200L);
        String what = String.format("%,d %s", delivered, ProductionPlanner.plural(record.item, delivered));
        StringBuilder line = new StringBuilder(record.chest != null && record.count == 0 ? "Your chest is full: " + what
                : record.chest != null ? "Done: " + what + " in your chest" : "Done: " + what);
        line.append(". About ").append(minutes).append(minutes == 1 ? " minute" : " minutes");
        if (!used.isEmpty()) {
            List<String> parts = new ArrayList<>();
            used.forEach((k, v) -> parts.add(String.format("%,d %s", v, ProductionPlanner.plural(k, v))));
            line.append("; ").append(String.join(", ", parts)).append(" used");
        }
        say(line.append('.').toString());
        outcome = what;
        phase = Phase.DONE;
        return TaskState.SUCCESS;
    }

    // ── Steps ───────────────────────────────────────────────────────

    private TaskState steps() {
        if (step == null) {
            if (todo.isEmpty()) {
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
            if (!p.equals(record.chest) && c.items().getOrDefault(path, 0) > 0) {
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
                if (++stalls >= 3) {
                    return fail("I couldn't find more " + ProductionPlanner.name(step.block()) + " within 32 blocks");
                }
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
            if (!s.isEmpty() && id.endsWith("_" + kind) && s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() > 1) return true;
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
            if (toolsToMake.getOrDefault(t, 0) <= 0 || !t.endsWith("_" + kind)) continue;
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
            if (workSpot == null) workSpot = sp.blockPosition();
            if (!sp.blockPosition().equals(workSpot)) return walkTo(workSpot, 0);
            furnaces.removeIf(f -> !level.getBlockState(f).is(Blocks.FURNACE));
            for (BlockPos p : ring(workSpot, 2)) {
                if (furnaces.size() >= step.furnaces()) break;
                if (furnaces.contains(p) || !level.getBlockState(p).is(Blocks.FURNACE)) continue;
                furnaces.add(p.immutable());
            }
            while (furnaces.size() < step.furnaces() && count(sp, net.minecraft.world.item.Items.FURNACE) > 0) {
                BlockPos spot = null;
                for (BlockPos p : ring(workSpot, 2)) {
                    if (furnaces.contains(p)) continue;
                    var st = level.getBlockState(p);
                    if (BlockHelper.isPassable(st) && st.getFluidState().isEmpty()
                            && BlockHelper.canStandOn(level.getBlockState(p.below()))) {
                        spot = p.immutable();
                        break;
                    }
                }
                if (spot == null || !Placement.placeBlock(sp, spot, Blocks.FURNACE.defaultBlockState())) break;
                furnaces.add(spot);
            }
            if (furnaces.isEmpty()) return fail("I have no furnace to smelt " + ProductionPlanner.name(step.input()) + " in");
            // Each furnace's share, in whole pieces of fuel's worth, and the fuel for it: as the plan priced it.
            int fuelTicks = Math.max(1, GameKnowledge.book(sp).fuelTicks().getOrDefault(step.fuel(), 1_600));
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
        int fuelTicks = Math.max(1, GameKnowledge.book(sp).fuelTicks().getOrDefault(step.fuel(), 1_600));
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
        Item it = item(record.item);
        if (record.chest == null) {
            delivered += Math.min(roundCount, count(sp, it));
            phase = Phase.ROUND;
            return TaskState.RUNNING;
        }
        if (!sp.canInteractWithBlock(record.chest, 0.0)) return walkTo(record.chest);
        Container c = HopperBlockEntity.getContainerAt(sp.serverLevel(), record.chest);
        if (c == null) return fail("the chest at " + record.chest.toShortString() + " is gone");
        int want = Math.min(target - delivered, count(sp, it));
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
        var loop = TaskContext.agentLoop(player);
        if (loop != null) {
            loop.noteChest(sp.serverLevel().dimension().location().toString(), record.chest.getX(), record.chest.getY(),
                    record.chest.getZ(), contents(c), sp.level().getGameTime());
        }
        if (put == 0 && room(c, it) == 0) return finish(); // full
        if (delivered < target) {
            say(String.format("%,d of %,d %s in the chest.", delivered, target, ProductionPlanner.plural(record.item, target)));
            if (put == 0) return fail("I made the " + ProductionPlanner.name(record.item) + " but couldn't put them in the chest");
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

    /** A block it carries put down on the ring {@code r} round it. */
    private static boolean placeNear(ServerPlayer sp, net.minecraft.world.level.block.state.BlockState state, int r) {
        ServerLevel level = sp.serverLevel();
        for (BlockPos p : ring(sp.blockPosition(), r)) {
            var st = level.getBlockState(p);
            if (BlockHelper.isPassable(st) && st.getFluidState().isEmpty() && BlockHelper.canStandOn(level.getBlockState(p.below()))
                    && Placement.placeBlock(sp, p.immutable(), state)) return true;
        }
        return false;
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
    protected String failureMessage() {
        return "{\"success\":false,\"delivered\":" + delivered + ",\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected void onInterrupt() {
        if (sub != null) sub.interrupt();
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
    }
}
