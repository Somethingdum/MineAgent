package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Placement;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.pathing.util.BlockHelper;
import com.mineagent.engine.planning.building.Blueprint;
import com.mineagent.engine.planning.production.ProductionPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Building from a blueprint (SMH plan 70, MA9.1): a site (flat ground, nothing built there, room above), the materials
 * (made first if it is short), the walls from the inside, the roof from the walls in (a jump where no face can be seen
 * otherwise), the torch, out through the doorway, the door from in front; then every block read back, and what is
 * missing placed again. What it built is marked as built, so its own paths never break it.
 */
public class BlueprintTask extends CompanionTask<BlueprintTask.BlueprintRecord> {
    public static final class BlueprintRecord extends TaskRecord {
        public final String name;
        public final String wall;
        public final String door;
        /** The corner to build from; null: it chooses the site. */
        public final BlockPos corner;

        public BlueprintRecord(String toolCallId, String name, String wall, String door, BlockPos corner) {
            super(toolCallId);
            this.name = name;
            this.wall = wall;
            this.door = door;
            this.corner = corner == null ? null : corner.immutable();
        }
    }

    private enum Phase { SITE, LEVEL, MATERIALS, INSIDE, PLACE, OUTSIDE, DOOR, CHECK, DONE, FAILED }

    private Phase phase = Phase.SITE;
    private Blueprint print;
    private BlockPos corner;
    private int index;
    private int tries;
    private int groundY = Integer.MIN_VALUE;
    private int repairs;
    private final List<Blueprint.Block> missing = new ArrayList<>();
    private CompanionTask<?> sub;
    private PlayerNav nav;
    private volatile boolean navFailed;
    private BlockPos walkingTo;
    private int walkTicks;
    private String outcome = "";

    public BlueprintTask(AgentPlayer player, BlueprintRecord record) {
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
                @Override public void onGoalReached() { }
                @Override public void onNavigationFailed(String reason) { navFailed = true; }
            });
        }
        if (print == null) {
            print = switch (record.name == null ? "hut" : record.name) {
                case "dirt_shelter" -> com.mineagent.engine.planning.building.MindcraftBlueprints.dirtShelter();
                case "small_wood_house" -> com.mineagent.engine.planning.building.MindcraftBlueprints.smallWoodHouse(record.wall, record.door);
                case "hut_home" -> Blueprint.homeHut(wallNow = record.wall, record.door);
                default -> Blueprint.hut(record.wall, record.door);
            };
        }
        record.extendDeadlineTo(Math.max(record.deadline(), sp().level().getGameTime() + 6 * 72_000L));
        walkingTo = null;
        if (sub != null) sub.resume();
    }

    @Override
    protected TaskState onTick() {
        try {
            return switch (phase) {
                case SITE -> site();
                case LEVEL -> level();
                case MATERIALS -> materials();
                case INSIDE -> walk(at(print.insideX(), 1, print.insideZ()), Phase.PLACE);
                case PLACE -> place();
                case OUTSIDE -> walk(at(print.doorX(), 1, print.doorZ() - 1), Phase.DOOR);
                case DOOR -> door();
                case CHECK -> check();
                case DONE -> TaskState.SUCCESS;
                case FAILED -> TaskState.FAILED;
            };
        } catch (RuntimeException e) {
            return fail("something went wrong: " + e.getMessage());
        }
    }

    private BlockPos at(int x, int y, int z) {
        return corner.offset(x, y - 1, z);
    }

    private TaskState fail(String why) {
        if (sub != null) sub.interrupt();
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        outcome = why;
        say("I couldn't build the " + record.name + ": " + why + ".");
        phase = Phase.FAILED;
        return TaskState.FAILED;
    }

    /**
     * Ground near it to build on, nothing built there, room above: within 20 blocks and two up or down, the nearest
     * that takes least levelling (a few natural blocks in the way dug out, a few holes in the floor filled; real ground
     * is seldom flat). A build of its own half done there is picked up, not built beside.
     */
    private TaskState site() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        if (record.corner != null) {
            corner = record.corner;
            fits(level, corner);
            toClear = new ArrayList<>(lastClear);
            toFill = new ArrayList<>(lastFill);
        } else {
            BlockPos here = ProductionTask.standing(sp);
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (int dy : new int[] {0, -1, 1, -2, 2}) {
                for (int dx = -SITE_REACH; dx <= SITE_REACH; dx++) {
                    for (int dz = -SITE_REACH; dz <= SITE_REACH; dz++) {
                        BlockPos c = here.offset(dx, dy, dz);
                        int done = fits(level, c);
                        if (done < 0) continue;
                        double d = c.offset(print.width() / 2, 0, print.depth() / 2).distSqr(here) - 1_000.0 * done
                                + 40.0 * (lastClear.size() + lastFill.size()) + 30.0 * Math.abs(dy);
                        if (d < bestD) {
                            bestD = d;
                            best = c.immutable();
                            toClear = new ArrayList<>(lastClear);
                            toFill = new ArrayList<>(lastFill);
                        }
                    }
                }
            }
            if (best == null) return fail("there is no ground I can build it on within " + SITE_REACH + " blocks");
            corner = best;
        }
        say("Building a " + shown() + " at " + corner.toShortString() + ": " + materialsLine()
                + (toClear.isEmpty() && toFill.isEmpty() ? "" : " (the ground levelled first)") + ".");
        phase = toClear.isEmpty() && toFill.isEmpty() ? Phase.MATERIALS : Phase.LEVEL;
        levelIndex = 0;
        levelTicks = 0;
        return TaskState.RUNNING;
    }

    /** How far round it looks for ground to build on. */
    static final int SITE_REACH = 20;
    /** The most natural blocks it digs out of the way, and holes in the floor it fills, to build somewhere. */
    static final int MOST_CLEAR = 12, MOST_FILL = 8;
    /** The site's levelling: blocks to dig out, holes to fill; and the last {@link #fits} found. */
    private List<BlockPos> toClear = new ArrayList<>(), toFill = new ArrayList<>();
    private final List<BlockPos> lastClear = new ArrayList<>(), lastFill = new ArrayList<>();
    private int levelIndex, levelTicks;

    /**
     * The site levelled: each block in the way dug out, then each hole in the floor filled with what it carries to
     * build with (dirt, cobblestone), from a spot in reach; one it can't do in fifteen seconds is left (the build goes
     * round or over it).
     */
    private TaskState level() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        int total = toClear.size() + toFill.size();
        while (levelIndex < total) {
            boolean clearing = levelIndex < toClear.size();
            BlockPos p = clearing ? toClear.get(levelIndex) : toFill.get(levelIndex - toClear.size());
            var st = level.getBlockState(p);
            boolean done = clearing ? st.isAir() || st.canBeReplaced() : BlockHelper.canStandOn(st);
            if (done || ++levelTicks > 300) {
                levelIndex++;
                levelTicks = 0;
                continue;
            }
            if (p.distToCenterSqr(sp.getEyePosition()) > 4.3 * 4.3 || sp.blockPosition().equals(p) || sp.blockPosition().below().equals(p)) {
                if (walkingTo == null || !walkingTo.equals(p) || !nav.isNavigating() && levelTicks % 20 == 1) {
                    walkingTo = p;
                    nav.navigateNear(p.getX(), p.getY(), p.getZ(), 2);
                }
                nav.tick();
                return TaskState.RUNNING;
            }
            nav.cancel();
            TaskContext.inputDriver(player).clear();
            if (clearing) {
                if (!BlockDigger.breaking(sp, p) && levelTicks % 20 == 1) BlockDigger.startBreaking(sp, p);
            } else if (!Placement.placeAnySupportBlock(sp, p) && levelTicks > 60) {
                levelIndex++; // nothing to fill it with, or no face to put it against: built over
                levelTicks = 0;
            }
            return TaskState.RUNNING;
        }
        nav.cancel();
        walkingTo = null;
        phase = Phase.MATERIALS;
        return TaskState.RUNNING;
    }

    /**
     * Whether the blueprint fits with its corner at {@code c}: -1 when it doesn't; else how many of its blocks are there
     * already (its own build, half done), every other cell clear.
     */
    private int fits(ServerLevel level, BlockPos c) {
        var placed = com.mineagent.engine.world.PlacedBlocks.of(level);
        String dim = level.dimension().location().toString();
        java.util.Map<Long, String> wants = new java.util.HashMap<>();
        for (Blueprint.Block b : print.blocks()) wants.put(BlockPos.asLong(b.x(), b.y() - 1, b.z()), b.id());
        lastClear.clear();
        lastFill.clear();
        int done = 0;
        for (int x = 0; x < print.width(); x++) {
            for (int z = 0; z < print.depth(); z++) {
                BlockPos ground = c.offset(x, -1, z);
                var gst = level.getBlockState(ground);
                if (!BlockHelper.canStandOn(gst)) {
                    // A hole in the floor, filled: on firm ground under it, never water or lava.
                    if (!gst.getFluidState().isEmpty() || !(gst.isAir() || gst.canBeReplaced())
                            || !BlockHelper.canStandOn(level.getBlockState(ground.below()))) return -1;
                    lastFill.add(ground.immutable());
                    if (lastFill.size() > MOST_FILL) return -1;
                }
                for (int y = 0; y <= print.height(); y++) {
                    BlockPos p = c.offset(x, y, z);
                    var st = level.getBlockState(p);
                    String want = wants.get(BlockPos.asLong(x, y, z));
                    if (want != null && st.is(block(want)) || st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock && want == null
                            && wants.containsKey(BlockPos.asLong(x, y - 1, z))) {
                        if (want != null) done++;
                        continue;
                    }
                    if (!st.getFluidState().isEmpty() || placed.isPlaced(p.getX(), p.getY(), p.getZ())
                            || com.mineagent.engine.world.AfkFarms.protects(dim, p.getX(), p.getY(), p.getZ())) return -1;
                    if (st.isAir() || st.canBeReplaced()) continue;
                    // Natural ground or leaves in the way: dug out (never a log, a chest, anything with something in
                    // it, or a block someone put down).
                    if (!clearable(level, p, st)) return -1;
                    lastClear.add(p.immutable());
                    if (lastClear.size() > MOST_CLEAR) return -1;
                }
            }
        }
        // Never round someone standing there (its owner, a player passing), nor round an animal (a cow walled in is
        // one it can't get to, and nobody wants it there). Looked at last: the blocks rule out most places first.
        var box = new net.minecraft.world.phys.AABB(c.getX(), c.getY(), c.getZ(), c.getX() + print.width(), c.getY() + print.height() + 1,
                c.getZ() + print.depth());
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, box, p -> p != sp()).isEmpty()) return -1;
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box.inflate(1.0),
                e -> !(e instanceof net.minecraft.world.entity.player.Player) && e.isAlive()).isEmpty()) return -1;
        return done;
    }

    /** Dirt, grass, sand, gravel, stone and the like, or leaves: the land, cheap to dig, nothing kept in it. */
    private static boolean clearable(ServerLevel level, BlockPos p, net.minecraft.world.level.block.state.BlockState st) {
        if (level.getBlockEntity(p) != null || st.is(net.minecraft.tags.BlockTags.LOGS)) return false;
        float hard = st.getDestroySpeed(level, p);
        if (hard < 0 || hard > 3.0f) return false;
        if (st.is(net.minecraft.tags.BlockTags.LEAVES)) return true;
        return BlockHelper.kindOf(st) == com.mineagent.engine.pathing.util.BreakCost.Kind.NATURAL;
    }

    /** How many times it has asked for each material: twice and still short, it says so instead of asking again. */
    private final java.util.Map<String, Integer> asked = new java.util.HashMap<>();
    /** The material it is making now, and the fittings it goes without (none of them to be had here). */
    private String making;
    private final java.util.Set<String> goneWithout = new java.util.LinkedHashSet<>();
    /** What its home hut's walls are of now: the stone asked for, or dirt when no stone is to be had. */
    private String wallNow;

    /**
     * Its own home hut's walls of dirt, when the stone for them can't be had (dug anywhere, by hand if need be): true
     * when it switched, before anything was built.
     */
    private boolean wallsOfDirt(String short_) {
        if (!"hut_home".equals(record.name) || wallNow == null || !wallNow.equals(short_) || "minecraft:dirt".equals(wallNow)) return false;
        wallNow = "minecraft:dirt";
        asked.clear();
        print = Blueprint.homeHut(wallNow, record.door).without(goneWithout);
        say("There's no more stone to be had here: the hut's walls are dirt.");
        return true;
    }

    /** The materials for the blocks not yet where the blueprint has them. */
    private Map<String, Integer> stillNeeded(ServerLevel level) {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (Blueprint.Block b : print.blocks()) {
            if (level.getBlockState(at(b.x(), b.y(), b.z())).is(block(b.id()))) continue;
            out.merge(b.id().replace("wall_torch", "torch"), 1, Integer::sum);
        }
        return out;
    }

    private String materialsLine() {
        List<String> parts = new ArrayList<>();
        for (var e : print.materials().entrySet()) parts.add(e.getValue() + " " + ProductionPlanner.plural(e.getKey(), e.getValue()));
        return String.join(", ", parts);
    }

    /** What it is short of, made first, one item at a time. */
    private TaskState materials() {
        ServerPlayer sp = sp();
        if (sub != null) {
            TaskState st = sub.tick();
            if (st == TaskState.RUNNING) return TaskState.RUNNING;
            sub = null;
            if (st != TaskState.SUCCESS) {
                // A fitting (a door, the table, the furnace, the torch) none of which is to be had here (the sixth
                // benchmark run: no wood left near its base for a door, and no hut at all): the hut goes up without
                // it; the shell itself it can't do without.
                if (!Blueprint.fitting(making)) {
                    if (wallsOfDirt(making)) return TaskState.RUNNING;
                    return fail("I couldn't get the materials");
                }
                goneWithout.add(making);
                print = print.without(goneWithout);
            }
        }
        // What the blocks not yet in place need (a build picked up again needs only the rest), and a job to have that
        // many in its pack: a production job's count is how many to have, not how many more to make.
        for (Map.Entry<String, Integer> e : stillNeeded(sp.serverLevel()).entrySet()) {
            int have = ProductionTask.count(sp, ProductionTask.item(e.getKey()));
            if (have >= e.getValue()) continue;
            if (asked.getOrDefault(e.getKey(), 0) >= 2) {
                if (!Blueprint.fitting(e.getKey())) {
                    if (wallsOfDirt(e.getKey())) return TaskState.RUNNING;
                    return fail("I couldn't get " + e.getValue() + " " + ProductionPlanner.plural(e.getKey(), e.getValue()));
                }
                goneWithout.add(e.getKey());
                print = print.without(goneWithout);
                return TaskState.RUNNING;
            }
            asked.merge(e.getKey(), 1, Integer::sum);
            making = e.getKey();
            sub = new ProductionTask(player, new ProductionTask.ProduceRecord(record.toolCallId() + ":" + e.getKey(), e.getKey(),
                    e.getValue(), null));
            sub.start();
            return TaskState.RUNNING;
        }
        phase = Phase.INSIDE;
        index = 0;
        return TaskState.RUNNING;
    }

    /**
     * Each block in order: placed from the ground when it can be (a block over its own head can only go in that way),
     * else at the top of a jump; a block that won't go from here is tried from the other spots inside, one by one, and
     * left for the check only when none will do.
     */
    private TaskState place() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        var input = TaskContext.inputDriver(player);
        if (moving != null) {
            // On its way to another spot inside: there first.
            if (ProductionTask.standing(sp).equals(moving)) {
                nav.cancel();
                moving = null;
                tries = 0;
            } else {
                if (walkingTo == null || !walkingTo.equals(moving) || !nav.isNavigating() && ++walkTicks > 20) {
                    walkingTo = moving;
                    walkTicks = 0;
                    nav.navigateTo(moving.getX(), moving.getY(), moving.getZ());
                }
                nav.tick();
                if (++moveTicks > 100) {
                    nav.cancel();
                    moving = null; // it can't get there: tries the next one
                    tries = 0;
                }
                return TaskState.RUNNING;
            }
        }
        while (index < print.blocks().size()) {
            Blueprint.Block b = print.blocks().get(index);
            if (b.fromOutside()) {
                index++;
                continue;
            }
            BlockPos p = at(b.x(), b.y(), b.z());
            Block want = block(b.id());
            if (level.getBlockState(p).is(want)) {
                next();
                continue;
            }
            // The ground it jumps from, kept while it is in the air (its block position rises with it).
            if (sp.onGround() || groundY == Integer.MIN_VALUE) groundY = ProductionTask.standing(sp).getY();
            int feet = groundY;
            boolean high = p.getY() >= feet + 2;
            tries++;
            // From the ground first, a few ticks; then at the top of a jump, for a block above its eyes.
            if (sp.onGround() && Placement.placeBlock(sp, p, want.defaultBlockState())) {
                input.setJumping(false);
                next();
                return TaskState.RUNNING;
            }
            if (high && tries > 3) {
                input.setJumping(true);
                if (!sp.onGround() && sp.getY() >= feet + 0.9 && Placement.placeBlock(sp, p, want.defaultBlockState())) {
                    input.setJumping(false);
                    next();
                    return TaskState.RUNNING;
                }
            }
            if (tries > 30) {
                input.setJumping(false);
                if (sp.onGround()) {
                    String why = Placement.whyNot(sp, p, want.defaultBlockState());
                    System.out.println("[MineAgent] builder: " + b.x() + "," + b.y() + "," + b.z() + " " + b.id()
                            + " not from " + rel(ProductionTask.standing(sp)) + ": " + (why == null ? "it didn't take" : why));
                }
                // Another spot inside, the next it hasn't tried for this block.
                if (spotTried < SPOTS.length) {
                    int[] s2 = SPOTS[spotTried++];
                    moving = at(s2[0], 1, s2[1]);
                    moveTicks = 0;
                    tries = 0;
                    return TaskState.RUNNING;
                }
                // None will do: the check finds it.
                next();
            }
            return TaskState.RUNNING;
        }
        input.clear();
        phase = Phase.OUTSIDE;
        return TaskState.RUNNING;
    }

    /** The spots inside it may stand on to place a block: the middle, then the four beside it. */
    private static final int[][] SPOTS = {{2, 2}, {1, 2}, {3, 2}, {2, 1}, {2, 3}};
    private BlockPos moving;
    private int moveTicks, spotTried;

    /** On to the next block: its tries and spots start again. */
    private void next() {
        index++;
        tries = 0;
        spotTried = 0;
    }

    /** Its name as said: the home hut is a hut ("Built the hut at ..."). */
    private String shown() {
        return "hut_home".equals(record.name) ? "hut" : record.name.replace('_', ' ');
    }

    /** A place as an offset from the corner, for the report. */
    private String rel(BlockPos p) {
        BlockPos d = p.subtract(corner);
        return d.getX() + "," + (d.getY() + 1) + "," + d.getZ();
    }

    /** The door, from in front of the doorway. */
    private TaskState door() {
        ServerPlayer sp = sp();
        for (Blueprint.Block b : print.blocks()) {
            if (!b.fromOutside()) continue;
            BlockPos p = at(b.x(), b.y(), b.z());
            Block want = block(b.id());
            if (sp.serverLevel().getBlockState(p).is(want)) continue;
            if (!Placement.placeBlock(sp, p, want.defaultBlockState()) && ++tries < 40) return TaskState.RUNNING;
        }
        tries = 0;
        phase = Phase.CHECK;
        return TaskState.RUNNING;
    }

    /** Every block read back; what is missing put in again once, from wherever it stands; then the report. */
    private TaskState check() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        missing.clear();
        var placed = com.mineagent.engine.world.PlacedBlocks.of(level);
        for (Blueprint.Block b : print.blocks()) {
            BlockPos p = at(b.x(), b.y(), b.z());
            if (!level.getBlockState(p).is(block(b.id()))) missing.add(b);
            else placed.mark(p); // built: its own paths never break it
        }
        if (!missing.isEmpty() && repairs++ < 2) {
            for (Blueprint.Block b : missing) Placement.placeBlock(sp, at(b.x(), b.y(), b.z()), block(b.id()).defaultBlockState());
            return TaskState.RUNNING;
        }
        List<String> where = new ArrayList<>();
        for (Blueprint.Block b : missing) {
            if (where.size() == 6) {
                where.add("...");
                break;
            }
            where.add(ProductionPlanner.name(b.id()) + " at " + b.x() + "," + b.y() + "," + b.z());
        }
        List<String> without = new ArrayList<>();
        for (String w : goneWithout) without.add(ProductionPlanner.name(w));
        String still = without.isEmpty() ? "" : " It has no " + String.join(" or ", without) + " yet: none to be had here.";
        String line = missing.isEmpty()
                ? "Built the " + shown() + " at " + corner.toShortString() + ": " + materialsLine() + "; every block checked." + still
                : "Built the " + shown() + " at " + corner.toShortString() + ", but " + missing.size()
                        + " blocks are missing; I couldn't place them (" + String.join("; ", where) + ").";
        say(line);
        var loop = TaskContext.agentLoop(player);
        if (loop != null) {
            loop.journal().record("built", line, level.getGameTime());
            // The blueprint kept with where it stands, so "repair the hut" builds it back from it (MA9.1).
            loop.placeKeeper().memory().name("build", record.name + "|" + (wallNow == null ? record.wall : wallNow).replace("minecraft:", "") + "|" + record.door.replace("minecraft:", ""),
                    new com.mineagent.engine.world.RouteMemory.Point(level.dimension().location().toString(), corner.getX(), corner.getY(), corner.getZ()),
                    level.getGameTime());
        }
        outcome = line;
        phase = missing.isEmpty() ? Phase.DONE : Phase.FAILED;
        return missing.isEmpty() ? TaskState.SUCCESS : TaskState.FAILED;
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(id));
    }

    private TaskState walk(BlockPos p, Phase next) {
        ServerPlayer sp = sp();
        if (ProductionTask.standing(sp).equals(p)) {
            nav.cancel();
            walkingTo = null;
            phase = next;
            return TaskState.RUNNING;
        }
        if (walkingTo == null || !walkingTo.equals(p) || !nav.isNavigating() && ++walkTicks > 20) {
            walkingTo = p;
            walkTicks = 0;
            navFailed = false;
            nav.navigateTo(p.getX(), p.getY(), p.getZ());
        }
        if (navFailed && ++tries > 5) {
            // No way in (or round to the front): it places what it can from where it stands, and the check says what
            // is missing. A repair mostly goes from outside anyway: the holes are in the walls.
            System.out.println("[MineAgent] build: no way to " + p.toShortString() + "; placing from where it stands");
            nav.cancel();
            walkingTo = null;
            tries = 0;
            phase = next;
            return TaskState.RUNNING;
        }
        nav.tick();
        return TaskState.RUNNING;
    }

    @Override
    protected String successMessage() {
        return "{\"success\":true,\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected String failureMessage() {
        return "{\"success\":false,\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected void onInterrupt() {
        if (sub != null) sub.interrupt();
        if (nav != null) nav.cancel();
        walkingTo = null;
        TaskContext.inputDriver(player).clear();
    }
}
