package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Placement;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.planning.mining.OreHeights;
import com.mineagent.engine.planning.mining.Tunnel;
import com.mineagent.engine.planning.production.GameKnowledge;
import com.mineagent.engine.planning.production.ProductionPlanner;
import com.mineagent.engine.world.PlaceKeeper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mining on its own (SMH plan 70, MA7.8): "mine diamonds", "get me 10 iron", "mine iron here", "mine until I say
 * stop". A staircase down to the ore's best height in this world (read from the game's ore features; never straight
 * down), then a branch mine: a main tunnel two high, branches to both sides every third block, each walked back. Every
 * ore of the kind it opens is taken, and the rest of its vein; only ores it has seen, unless the switch is off. Lava and
 * water are never broken into: a block with either behind it is left standing and the tunnel turns. Torches go down as
 * the light falls. When its pack is full it goes home by the tunnel, puts the haul in the chest there, and comes back to
 * the face; a worn pickaxe is replaced from what it mined. It says what it got and how much an hour.
 */
public class MiningTask extends CompanionTask<MiningTask.MineRecord> {
    public static final class MineRecord extends TaskRecord {
        /** The ore in a person's words: "diamond", "iron", "ore" (any). */
        public final String ore;
        /** How many of what it drops; 0: until told to stop. */
        public final int count;
        /** Mine at the height it stands at ("mine iron here"), not the ore's best. */
        public final boolean here;

        public MineRecord(String toolCallId, String ore, int count, boolean here) {
            super(toolCallId);
            this.ore = ore;
            this.count = Math.max(0, count);
            this.here = here;
        }
    }

    static final long LONGEST = 16 * 72_000L;
    /** Turns in a row (lava ahead, then to the side, ...) before it gives the tunnel up. */
    static final int MAX_TURNS = 4;
    /** Ore blocks one vein may run to. */
    static final int VEIN = 32;

    private enum Phase { START, READY, TOOL, DIG, VEIN, HOME, DEPOSIT, BACK, DONE, FAILED }

    private Phase phase = Phase.START;
    private boolean begun;
    private final Set<String> ores = new HashSet<>();
    private final Set<Item> drops = new HashSet<>();
    private final Map<Item, Integer> before = new LinkedHashMap<>();
    /** What it has put in the chest at home of what it mines. */
    private final Map<Item, Integer> banked = new LinkedHashMap<>();
    /** The pickaxe tier each ore block needs to drop anything. */
    private final Map<String, Integer> oreTier = new LinkedHashMap<>();
    private int neededTier;
    /** Mining iron and coal first for the iron pickaxe the order needs (diamonds with a stone one). */
    private boolean prep;
    private boolean prepTried;
    private final Set<String> mainOres = new HashSet<>();
    private int mainY;
    private int walkTicks;
    private List<Tunnel.Step> steps = List.of();
    private int index;
    private int turns;
    private int targetY;
    private BlockPos start;
    private BlockPos face;
    private final Deque<BlockPos> vein = new ArrayDeque<>();
    private final Set<BlockPos> veinSeen = new HashSet<>();
    private int sinceTorch;
    private BlockPos lastFeet;
    private boolean finishAfterHome;
    private String stopWhy = "";
    private long startedAt;
    private CompanionTask<?> sub;
    private PlayerNav nav;
    private volatile boolean arrived;
    private volatile boolean navFailed;
    private BlockPos walkingTo;
    private BlockPos digging;
    private int digTicks, digLimit;
    private int wait;
    private int stuck;
    private String outcome = "";

    public MiningTask(AgentPlayer player, MineRecord record) {
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
        if (nav == null) {
            nav = new PlayerNav(player, TaskContext.navCaches(player));
            nav.setListener(new PlayerNav.NavListener() {
                @Override public void onGoalReached() { arrived = true; }
                @Override public void onNavigationFailed(String reason) { navFailed = true; }
            });
        }
        if (!begun) {
            begun = true;
            startedAt = sp().level().getGameTime();
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
                case READY -> ready();
                case TOOL -> tool();
                case DIG -> dig();
                case VEIN -> vein();
                case HOME -> home();
                case DEPOSIT -> deposit();
                case BACK -> back();
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
        say(Character.toUpperCase(why.charAt(0)) + why.substring(1) + (gotLine().isEmpty() ? "." : " (" + gotLine() + " so far)."));
        phase = Phase.FAILED;
        return TaskState.FAILED;
    }

    // ── The order ───────────────────────────────────────────────────

    private TaskState begin() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        String w = words(record.ore);
        boolean any = w.equals("ore") || w.isEmpty();
        boolean nether = level.dimension() == net.minecraft.world.level.Level.NETHER;
        for (var key : BuiltInRegistries.BLOCK.keySet()) {
            String path = key.getPath();
            boolean ore = path.endsWith("_ore") || path.equals("ancient_debris");
            if (!ore) continue;
            if (any ? (nether == (path.startsWith("nether_") || path.equals("ancient_debris"))) : path.endsWith(w + "_ore") || path.equals(w)) {
                ores.add(key.toString());
            }
        }
        if (ores.isEmpty()) return fail("I don't know an ore called " + record.ore);
        var book = GameKnowledge.book(sp);
        neededTier = 0;
        for (var list : book.sourcesByItem().values()) {
            for (var s : list) {
                if (!ores.contains(s.block()) || s.silkTouch()) continue;
                drops.add(ProductionTask.item(s.item()));
                oreTier.merge(s.block(), s.tier(), Math::min);
                if (!any) neededTier = Math.max(neededTier, s.tier());
            }
        }
        if (any) neededTier = 2;
        // Iron and coal, for the iron pickaxe a deeper ore may need first.
        for (var key : BuiltInRegistries.BLOCK.keySet()) {
            String path = key.getPath();
            if (path.endsWith("iron_ore") || path.endsWith("coal_ore")) oreTier.putIfAbsent(key.toString(), path.contains("iron") ? 2 : 1);
        }
        drops.remove(Items.AIR);
        for (Item it : drops) before.put(it, ProductionTask.count(sp, it));
        start = sp.blockPosition().immutable();
        String first = ores.stream().filter(o -> !o.contains("deepslate")).findFirst().orElse(ores.iterator().next());
        targetY = record.here ? start.getY() : any ? Math.max(level.getMinBuildHeight() + 5, Math.min(16, start.getY())) : OreHeights.bestY(level, first);
        Direction facing = sp.getDirection();
        steps = Tunnel.plan(start.getX(), start.getY(), start.getZ(), facing.getStepX(), facing.getStepZ(), targetY);
        index = 0;
        say("On it: " + (any ? "ore" : ProductionPlanner.plural(first.replace("deepslate_", "").replace("_ore", ""), 2))
                + (record.count > 0 ? ", " + record.count + " of them" : ", until you say stop")
                + (record.here ? ", at this height" : ", at Y " + targetY + (start.getY() > targetY ? ", down a staircase" : ""))
                + "; branch tunnels every third block.");
        phase = Phase.READY;
        return TaskState.RUNNING;
    }

    // ── Ready before it goes (MA7.4) ────────────────────────────────

    private int readyStage;
    private final List<String> without = new ArrayList<>();

    /**
     * What it should carry on a trip, first (the switch "Ready before trips"): food, torches, a spare pickaxe, each
     * made from what it carries or can see (it hunts the cows in sight, it doesn't go looking); what it can't make it
     * goes without, and says so.
     */
    private TaskState ready() {
        ServerPlayer sp = sp();
        if (sub != null) {
            TaskState st = sub.tick();
            if (st == TaskState.RUNNING) return TaskState.RUNNING;
            sub = null;
            readyStage++;
            return TaskState.RUNNING;
        }
        if (!com.mineagent.engine.settings.Switches.readyCheck()) {
            phase = Phase.TOOL;
            return TaskState.RUNNING;
        }
        switch (readyStage) {
            case 0 -> {
                int want = com.mineagent.engine.survival.HardRules.tripFood(com.mineagent.engine.settings.Switches.readyFood(), hard(sp));
                int have = food(sp);
                if (have < want) return errand(ProductionTask.FOOD, want - have, "Food for the trip first.");
                readyStage++;
            }
            case 1 -> {
                int want = com.mineagent.engine.settings.Switches.readyTorches();
                int have = ProductionTask.count(sp, Items.TORCH);
                if (have < want) return errand("minecraft:torch", want - have, null);
                readyStage++;
            }
            case 2 -> {
                if (com.mineagent.engine.settings.Switches.sparePickaxe() && pickaxes(sp) < 2) {
                    // One more than the stone ones it has: the one in hand is not the spare.
                    return errand("minecraft:stone_pickaxe", ProductionTask.count(sp, Items.STONE_PICKAXE) + 1, null);
                }
                readyStage++;
            }
            default -> {
                int food = food(sp), torches = ProductionTask.count(sp, Items.TORCH), picks = pickaxes(sp);
                if (!com.mineagent.engine.survival.HardRules.maySetOut(food, hard(sp))) {
                    return fail("on Hard, hunger kills, and I have no food for the trip and couldn't make any here");
                }
                if (food < com.mineagent.engine.survival.HardRules.tripFood(com.mineagent.engine.settings.Switches.readyFood(), hard(sp))) {
                    without.add(food == 0 ? "food" : "enough food");
                }
                if (torches < com.mineagent.engine.settings.Switches.readyTorches()) without.add(torches == 0 ? "torches" : "enough torches");
                if (com.mineagent.engine.settings.Switches.sparePickaxe() && picks < 2) without.add("a spare pickaxe");
                say("Ready: " + food + " food, " + torches + " torches, " + picks + (picks == 1 ? " pickaxe" : " pickaxes")
                        + (without.isEmpty() ? "." : "; going without " + String.join(", ", without) + ": I couldn't make them here."));
                phase = Phase.TOOL;
            }
        }
        return TaskState.RUNNING;
    }

    private static boolean hard(ServerPlayer sp) {
        return sp.serverLevel().getDifficulty() == net.minecraft.world.Difficulty.HARD;
    }

    private TaskState errand(String item, int count, String line) {
        if (line != null) say(line);
        sub = new ProductionTask(player, new ProductionTask.ProduceRecord(record.toolCallId() + ":ready" + readyStage, item, count,
                null, false, true));
        sub.start();
        return TaskState.RUNNING;
    }

    private static int food(ServerPlayer sp) {
        int n = 0;
        var foods = GameKnowledge.foods().keySet();
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && foods.contains(BuiltInRegistries.ITEM.getKey(s.getItem()).toString())) n += s.getCount();
        }
        return n;
    }

    private static int pickaxes(ServerPlayer sp) {
        int n = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(net.minecraft.tags.ItemTags.PICKAXES) && s.getMaxDamage() - s.getDamageValue() > 2) n++;
        }
        return n;
    }

    /** A pickaxe good enough for the ore first (a stone one for iron; for diamonds, an iron one), made if it can be. */
    private TaskState tool() {
        ServerPlayer sp = sp();
        if (sub != null) {
            TaskState st = sub.tick();
            if (st == TaskState.RUNNING) return TaskState.RUNNING;
            sub = null;
            if (bestPickaxe(sp) < Math.max(1, neededTier)) {
                return fail("I need a better pickaxe for " + record.ore + " and couldn't make one");
            }
        }
        if (bestPickaxe(sp) >= Math.max(1, neededTier)) {
            phase = Phase.DIG;
            return TaskState.RUNNING;
        }
        String make = neededTier >= 4 ? "minecraft:diamond_pickaxe" : neededTier == 3 ? "minecraft:iron_pickaxe" : "minecraft:stone_pickaxe";
        if (neededTier == 3 && !prepTried) {
            // Forethought (the owner: "go get me 3 diamonds. But ... it's pickaxe is stone"): with nothing seen to make
            // an iron pickaxe from, iron and coal first, at iron's height, then the pickaxe, then on.
            prepTried = true;
            var have = GameKnowledge.have(sp, TaskContext.agentLoop(player), null);
            var plan = ProductionPlanner.plan(GameKnowledge.book(sp), have, make, 1, Set.of(com.mineagent.engine.planning.production.Production.UNSEEN));
            if (!plan.possible() && bestPickaxe(sp) >= 2) {
                mainOres.addAll(ores);
                mainY = targetY;
                ores.clear();
                for (String o : oreTier.keySet()) if (o.endsWith("iron_ore") || o.endsWith("coal_ore")) ores.add(o);
                for (String o : ores) before.putIfAbsent(ProductionTask.item(o.contains("iron") ? "minecraft:raw_iron" : "minecraft:coal"), 0);
                prep = true;
                targetY = OreHeights.bestY(sp.serverLevel(), "minecraft:iron_ore");
                BlockPos at = sp.blockPosition();
                Direction f = sp.getDirection();
                steps = Tunnel.plan(at.getX(), at.getY(), at.getZ(), f.getStepX(), f.getStepZ(), targetY);
                index = 0;
                say("I need an iron pickaxe for that and have nothing to make one from: iron and coal first, at Y " + targetY + ".");
                phase = Phase.DIG;
                return TaskState.RUNNING;
            }
        }
        say("First a " + ProductionPlanner.name(make) + ".");
        sub = new ProductionTask(player, new ProductionTask.ProduceRecord(record.toolCallId() + ":pickaxe", make, 1, null));
        sub.start();
        return TaskState.RUNNING;
    }

    private static int bestPickaxe(ServerPlayer sp) {
        int best = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.is(net.minecraft.tags.ItemTags.PICKAXES) || s.getMaxDamage() - s.getDamageValue() <= 2) continue;
            best = Math.max(best, ProductionPlanner.tierOf(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()));
        }
        return best;
    }

    // ── Digging the tunnel ──────────────────────────────────────────

    private TaskState dig() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        if (prep) {
            int iron = ProductionTask.count(sp, Items.RAW_IRON) + ProductionTask.count(sp, Items.IRON_INGOT);
            boolean fuel = ProductionTask.count(sp, Items.COAL) + ProductionTask.count(sp, Items.CHARCOAL) > 0;
            if (iron >= 3 && fuel) {
                // Enough for the pickaxe: back to the order, from here.
                prep = false;
                ores.clear();
                ores.addAll(mainOres);
                targetY = mainY;
                BlockPos at = sp.blockPosition();
                Direction f = sp.getDirection();
                steps = Tunnel.plan(at.getX(), at.getY(), at.getZ(), f.getStepX(), f.getStepZ(), targetY);
                index = 0;
                phase = Phase.TOOL;
                return TaskState.RUNNING;
            }
        } else if (record.count > 0 && gained() >= record.count) {
            stopWhy = "";
            return goHome(true);
        }
        if (prep && bestPickaxe(sp) < 2) {
            phase = Phase.TOOL;
            return TaskState.RUNNING;
        }
        if (!prep && bestPickaxe(sp) < Math.max(1, neededTier)) {
            phase = Phase.TOOL;
            return TaskState.RUNNING;
        }
        if (full(sp)) {
            dropJunk(sp);
            if (full(sp)) {
                say("My pack is full: taking it home.");
                return goHome(false);
            }
        }
        if (index >= steps.size()) {
            // The end of the tunnel: another one from here, turned left.
            if (!turn(sp)) {
                stopWhy = "I've dug out all round here";
                return goHome(true);
            }
            return TaskState.RUNNING;
        }
        Tunnel.Step s = steps.get(index);
        BlockPos feet = new BlockPos(s.x(), s.y(), s.z());
        if (s.kind() == Tunnel.Kind.RETURN) {
            if (sp.blockPosition().equals(feet)) {
                index++;
                return TaskState.RUNNING;
            }
            return walk(feet);
        }
        for (int[] c : s.clear()) {
            BlockPos p = new BlockPos(c[0], c[1], c[2]);
            if (open(level, p)) continue;
            if (unsafe(level, p)) return blocked(sp);
            int r = breakBlock(sp, p);
            if (r < 0) return blocked(sp);
            return TaskState.RUNNING; // breaking it; one block at a time
        }
        // A drop under the next step (a cave below the tunnel): filled from its cobblestone, or not stepped on (MA8.4).
        if (!sp.blockPosition().equals(feet) && !com.mineagent.engine.pathing.util.BlockHelper.canStandOn(level.getBlockState(feet.below()))) {
            if (unsafe(level, feet.below())) return blocked(sp);
            var fill = ProductionTask.count(sp, Items.COBBLESTONE) > 0 ? Blocks.COBBLESTONE
                    : ProductionTask.count(sp, Items.COBBLED_DEEPSLATE) > 0 ? Blocks.COBBLED_DEEPSLATE : null;
            if (fill == null || !Placement.placeBlock(sp, feet.below(), fill.defaultBlockState())) return blocked(sp);
            return TaskState.RUNNING;
        }
        if (!sp.blockPosition().equals(feet)) return walk(feet);
        // Here: what it opened, and the light.
        turns = 0;
        face = feet;
        look(level, s);
        if (++sinceTorch >= 6 && lastFeet != null && dark(level, feet)) {
            if (ProductionTask.count(sp, Items.TORCH) > 0 && Placement.placeBlock(sp, lastFeet, Blocks.TORCH.defaultBlockState())) sinceTorch = 0;
        }
        lastFeet = feet;
        index++;
        if (!vein.isEmpty()) phase = Phase.VEIN;
        return TaskState.RUNNING;
    }

    /** Lava or water ahead: a branch is given up there; the stairs or the main tunnel turn left. */
    private TaskState blocked(ServerPlayer sp) {
        digging = null;
        int back = Tunnel.returnOf(steps, index);
        if (back >= 0) {
            index = back;
            return TaskState.RUNNING;
        }
        if (!turn(sp)) {
            stopWhy = "lava or water on every side here";
            return goHome(true);
        }
        return TaskState.RUNNING;
    }

    /** A new tunnel from where it stands, a quarter turn left; false after {@link #MAX_TURNS} in a row. */
    private boolean turn(ServerPlayer sp) {
        if (++turns > MAX_TURNS) return false;
        BlockPos at = sp.blockPosition();
        int dx, dz;
        if (index < steps.size() && index > 0) {
            Tunnel.Step a = steps.get(index - 1), b = steps.get(index);
            dx = Integer.signum(b.x() - a.x());
            dz = Integer.signum(b.z() - a.z());
        } else {
            Direction f = sp.getDirection();
            dx = f.getStepX();
            dz = f.getStepZ();
        }
        if (dx == 0 && dz == 0) {
            dx = 1;
        }
        int nx = dz, nz = -dx; // left
        steps = Tunnel.plan(at.getX(), at.getY(), at.getZ(), nx, nz, targetY);
        index = 0;
        return true;
    }

    /** The blocks round the cells it just opened: ore of the kind is followed. */
    private void look(ServerLevel level, Tunnel.Step s) {
        boolean sightOnly = com.mineagent.engine.settings.Switches.oreSightOnly();
        for (int[] c : s.clear()) {
            BlockPos p = new BlockPos(c[0], c[1], c[2]);
            for (Direction d : Direction.values()) queueIfOre(level, p.relative(d));
            if (!sightOnly) {
                // Ore sight off: the ore within 2 blocks round, seen or not.
                for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, -2, -2), p.offset(2, 2, 2))) queueIfOre(level, q.immutable());
            }
        }
    }

    private void queueIfOre(ServerLevel level, BlockPos p) {
        if (veinSeen.contains(p)) return;
        if (!ores.contains(BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString())) return;
        veinSeen.add(p.immutable());
        vein.add(p.immutable());
    }

    /** The vein: each ore block broken from where it can reach it, and the ones touching it after. */
    private TaskState vein() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        if (vein.isEmpty() || veinSeen.size() > 4096) {
            if (veinSeen.size() > 4096) veinSeen.clear();
            phase = Phase.DIG;
            return TaskState.RUNNING;
        }
        BlockPos p = vein.peek();
        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString();
        // Not one it may take (gold with a stone pickaxe gives nothing: left for later), lava behind it, or out of reach.
        if (!ores.contains(id) || oreTier.getOrDefault(id, 0) > bestPickaxe(sp) || unsafe(level, p)
                || p.distToCenterSqr(sp.getEyePosition()) > 4.4 * 4.4) {
            vein.poll();
            digging = null;
            return TaskState.RUNNING;
        }
        int r = breakBlock(sp, p);
        if (r == 0) return TaskState.RUNNING;
        vein.poll();
        if (r > 0 && veinSeen.size() < 4096) {
            int n = 0;
            for (Direction d : Direction.values()) {
                if (n++ < VEIN) queueIfOre(level, p.relative(d));
            }
            wait = 4; // its drop lands and is picked up
        }
        return TaskState.RUNNING;
    }

    // ── Home and back ───────────────────────────────────────────────

    private TaskState goHome(boolean finish) {
        finishAfterHome = finish;
        digging = null;
        walkingTo = null;
        phase = Phase.HOME;
        return TaskState.RUNNING;
    }

    private BlockPos home(ServerPlayer sp) {
        var loop = TaskContext.agentLoop(player);
        if (loop != null) {
            var here = PlaceKeeper.point(sp);
            var h = loop.placeKeeper().memory().find("home", here);
            if (h.isPresent() && h.get().at().dim().equals(here.dim())) return new BlockPos(h.get().at().x(), h.get().at().y(), h.get().at().z());
        }
        return start;
    }

    private TaskState home() {
        ServerPlayer sp = sp();
        BlockPos h = home(sp);
        if (sp.blockPosition().distSqr(h) <= 9) {
            nav.cancel();
            walkingTo = null;
            phase = Phase.DEPOSIT;
            return TaskState.RUNNING;
        }
        return walk(h, 2);
    }

    /** The haul into the chest by home: everything but its tools, torches, food, a stack of cobblestone and wood for tools. */
    private TaskState deposit() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        BlockPos h = home(sp);
        BlockPos chest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(h.offset(-6, -3, -6), h.offset(6, 3, 6))) {
            var st = level.getBlockState(p);
            if (!(st.is(Blocks.CHEST) || st.is(Blocks.TRAPPED_CHEST) || st.is(Blocks.BARREL))) continue;
            double d = p.distSqr(sp.blockPosition());
            if (d < best) {
                best = d;
                chest = p.immutable();
            }
        }
        if (chest != null && !sp.canInteractWithBlock(chest, 0.0)) return walk(chest, 1);
        int moved = 0;
        boolean leftOver = false;
        if (chest != null) {
            Container c = HopperBlockEntity.getContainerAt(level, chest);
            if (c != null) {
                int keepCobble = 64;
                var inv = sp.getInventory();
                for (int i = 0; i < 36 && i < inv.getContainerSize(); i++) {
                    ItemStack s = inv.getItem(i);
                    if (s.isEmpty() || keep(s)) continue;
                    if (s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE)) {
                        int keepHere = Math.min(keepCobble, s.getCount());
                        keepCobble -= keepHere;
                        if (keepHere == s.getCount()) continue;
                        ItemStack part = s.split(s.getCount() - keepHere);
                        ItemStack rest = HopperBlockEntity.addItem(null, c, part, null);
                        moved += part.getCount() - rest.getCount();
                        s.grow(rest.getCount());
                        continue;
                    }
                    int n = s.getCount();
                    Item what = s.getItem();
                    ItemStack rest = HopperBlockEntity.addItem(null, c, s.copy(), null);
                    moved += n - rest.getCount();
                    if (before.containsKey(what)) banked.merge(what, n - rest.getCount(), Integer::sum);
                    inv.setItem(i, rest);
                    if (!rest.isEmpty()) {
                        leftOver = true; // the chest is full
                        break;
                    }
                }
                c.setChanged();
                TaskContext.syncInventory(sp);
                var loop = TaskContext.agentLoop(player);
                if (loop != null) {
                    Map<String, Integer> items = new LinkedHashMap<>();
                    for (int i = 0; i < c.getContainerSize(); i++) {
                        ItemStack s = c.getItem(i);
                        if (!s.isEmpty()) items.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
                    }
                    loop.noteChest(level.dimension().location().toString(), chest.getX(), chest.getY(), chest.getZ(), items, level.getGameTime());
                }
            }
        }
        if (finishAfterHome) {
            if (leftOver) stopWhy = (stopWhy.isEmpty() ? "" : stopWhy + "; ") + "the chest at home is full, so I'm carrying the rest";
            return finish();
        }
        if (chest == null || moved == 0 || full(sp)) {
            stopWhy = chest == null ? "my pack is full and there's no chest at home" : "my pack is full and so is the chest at home";
            return finish();
        }
        say("Back to the tunnel.");
        walkingTo = null;
        phase = Phase.BACK;
        return TaskState.RUNNING;
    }

    private TaskState back() {
        ServerPlayer sp = sp();
        if (face == null || sp.blockPosition().equals(face)) {
            walkingTo = null;
            phase = Phase.DIG;
            return TaskState.RUNNING;
        }
        return walk(face);
    }

    private TaskState finish() {
        ServerPlayer sp = sp();
        long ticks = Math.max(1, sp.level().getGameTime() - startedAt);
        long minutes = Math.max(1, ticks / 1_200L);
        String got = gotLine();
        int n = gained();
        double perHour = n * 72_000.0 / ticks;
        String line = (got.isEmpty() ? "No " + record.ore + " found" : "Mined " + got) + " in about " + minutes + (minutes == 1 ? " minute" : " minutes")
                + (n > 0 ? String.format(java.util.Locale.ROOT, " (%.0f an hour)", perHour) : "")
                + (stopWhy.isEmpty() ? "" : "; " + stopWhy) + ".";
        say(line);
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.journal().record("mined", line, sp.level().getGameTime());
        outcome = line;
        phase = Phase.DONE;
        return TaskState.SUCCESS;
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private int gained() {
        ServerPlayer sp = sp();
        int n = 0;
        for (var e : before.entrySet()) {
            if (prep || !drops.contains(e.getKey())) continue;
            n += Math.max(0, ProductionTask.count(sp, e.getKey()) - e.getValue() + banked.getOrDefault(e.getKey(), 0));
        }
        return n;
    }

    private String gotLine() {
        ServerPlayer sp = sp();
        List<String> parts = new ArrayList<>();
        for (var e : before.entrySet()) {
            int n = ProductionTask.count(sp, e.getKey()) - e.getValue() + banked.getOrDefault(e.getKey(), 0);
            if (n > 0) parts.add(n + " " + ProductionPlanner.plural(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), n));
        }
        return String.join(", ", parts);
    }

    /** What it keeps when it empties its pack: tools and weapons, torches, food, wood for new tools. */
    private boolean keep(ItemStack s) {
        if (s.isDamageableItem() || s.is(Items.TORCH) || s.has(net.minecraft.core.component.DataComponents.FOOD)) return true;
        return s.is(net.minecraft.tags.ItemTags.PLANKS) || s.is(Items.STICK) || s.is(Items.CRAFTING_TABLE)
                || s.is(Items.FURNACE) || s.is(Items.COAL) && prep;
    }

    /** A person's words for an ore as the game names it: "diamonds" diamond, "lapis lazuli" lapis, "netherite" ancient debris. */
    static String words(String said) {
        String w = said == null ? "" : said.toLowerCase(java.util.Locale.ROOT).strip().replaceAll(" ores?$", "").replace(' ', '_');
        if (w.startsWith("lapis")) return "lapis";
        if (w.equals("netherite") || w.contains("debris")) return "ancient_debris";
        if (w.equals("ores")) return "ore";
        if (w.endsWith("s") && !w.equals("redstone")) w = w.substring(0, w.length() - 1);
        return w;
    }

    private static boolean full(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < 36 && i < inv.getContainerSize(); i++) if (inv.getItem(i).isEmpty()) return false;
        return true;
    }

    /** Rock it has no use for (more than a stack of cobblestone, dirt, gravel, diorite...) left behind in the tunnel. */
    private void dropJunk(ServerPlayer sp) {
        var inv = sp.getInventory();
        int cobble = 0;
        for (int i = 0; i < 36 && i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            boolean stone = s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE);
            boolean junk = s.is(Items.DIRT) || s.is(Items.GRAVEL) || s.is(Items.ANDESITE) || s.is(Items.DIORITE) || s.is(Items.GRANITE)
                    || s.is(Items.TUFF) || s.is(Items.NETHERRACK) || s.is(Items.CALCITE);
            if (stone && cobble < 64) {
                cobble += s.getCount();
                if (cobble <= 64) continue;
            }
            if (stone || junk) {
                sp.drop(inv.removeItem(i, s.getCount()), false);
            }
        }
        TaskContext.syncInventory(sp);
    }

    private static boolean open(ServerLevel level, BlockPos p) {
        var st = level.getBlockState(p);
        return (st.isAir() || st.canBeReplaced() || !st.isSolid() && st.getCollisionShape(level, p).isEmpty()) && st.getFluidState().isEmpty();
    }

    /** Lava or water in it or touching it: never broken into. */
    static boolean unsafe(ServerLevel level, BlockPos p) {
        if (!level.getFluidState(p).isEmpty()) return true;
        for (Direction d : Direction.values()) if (!level.getFluidState(p.relative(d)).isEmpty()) return true;
        return false;
    }

    private static boolean dark(ServerLevel level, BlockPos p) {
        return level.getBrightness(LightLayer.BLOCK, p) < 8 && level.getBrightness(LightLayer.SKY, p) < 8;
    }

    /** 1 when the block is gone, 0 while breaking it, -1 when it can't be broken. */
    private int breakBlock(ServerPlayer sp, BlockPos p) {
        ServerLevel level = sp.serverLevel();
        if (open(level, p)) {
            if (p.equals(digging)) digging = null;
            return 1;
        }
        if (!p.equals(digging)) {
            int limit = BlockDigger.expectedBreakTicks(sp, p);
            var a = BlockDigger.startBreakingDetailed(sp, p);
            if (!a.allowed()) {
                if (++stuck > 3) {
                    stuck = 0;
                    return -1;
                }
                return 0;
            }
            stuck = 0;
            digging = p.immutable();
            digTicks = 0;
            digLimit = limit == Integer.MAX_VALUE ? 600 : limit;
        }
        if (++digTicks > digLimit) {
            BlockDigger.abortBreaking(sp, p);
            digging = null;
            return -1;
        }
        return open(level, p) ? 1 : 0;
    }

    private TaskState walk(BlockPos p) {
        return walk(p, 0);
    }

    private TaskState walk(BlockPos p, int radius) {
        boolean same = walkingTo != null && walkingTo.equals(p);
        if (same) walkTicks++;
        boolean ended = same && walkTicks > 3 && !nav.isNavigating() && !navFailed;
        if (ended || same && walkTicks > 600) {
            // Its walk ended short of the spot, or is taking too long: once more, then it counts as stuck.
            walkTicks = 0;
            walkingTo = null;
            if (++stuck > 5) {
                stuck = 0;
                if (phase == Phase.DIG) return blocked(sp());
                return fail("I can't get to " + p.toShortString());
            }
        }
        if (walkingTo == null || !walkingTo.equals(p)) {
            walkingTo = p.immutable();
            walkTicks = 0;
            arrived = false;
            navFailed = false;
            if (radius <= 0) nav.navigateTo(p.getX(), p.getY(), p.getZ());
            else nav.navigateNear(p.getX(), p.getY(), p.getZ(), radius);
            return TaskState.RUNNING;
        }
        if (navFailed) {
            walkingTo = null;
            navFailed = false;
            if (++stuck > 5) {
                stuck = 0;
                if (phase == Phase.DIG) return blocked(sp());
                return fail("I can't get to " + p.toShortString());
            }
            return TaskState.RUNNING;
        }
        nav.tick();
        return TaskState.RUNNING;
    }

    @Override
    protected String successMessage() {
        return "{\"success\":true,\"mined\":" + gained() + ",\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected String failureMessage() {
        return "{\"success\":false,\"mined\":" + gained() + ",\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected void onInterrupt() {
        if (sub != null) sub.interrupt();
        if (nav != null) nav.cancel();
        if (digging != null) BlockDigger.abortBreaking(sp(), digging);
        digging = null;
        walkingTo = null; // made again from where it stands when it carries on
        TaskContext.inputDriver(player).clear();
    }

    /** What it is doing, from the facts (MA6.5). */
    @Override
    public com.mineagent.api.task.TaskSnapshot snapshot() {
        String doing = switch (phase) {
            case READY -> "getting ready for the trip";
            case TOOL -> "making a pickaxe";
            case DIG, VEIN -> prep ? "mining iron and coal for an iron pickaxe" : "branch mining at Y " + targetY;
            case HOME, DEPOSIT -> "taking the haul home";
            case BACK -> "going back to the tunnel";
            default -> phase.name().toLowerCase(java.util.Locale.ROOT);
        };
        String got = begun ? gotLine() : "";
        String summary = "mining " + record.ore + (record.count > 0 ? " (" + record.count + ")" : "") + ": " + doing
                + (got.isEmpty() ? "" : "; " + got + " so far");
        int n = begun ? gained() : 0;
        return com.mineagent.api.task.TaskSnapshot.progress(doing, summary, n, record.count > 0 ? record.count : -1,
                face == null ? null : face.getX(), face == null ? null : face.getY(), face == null ? null : face.getZ(),
                null, null, n * 1_000L + index);
    }
}
