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

    private enum Phase { SITE, MATERIALS, INSIDE, PLACE, OUTSIDE, DOOR, CHECK, DONE, FAILED }

    private Phase phase = Phase.SITE;
    private Blueprint print;
    private BlockPos corner;
    private int index;
    private int tries;
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
        if (print == null) print = Blueprint.hut(record.wall, record.door);
        record.extendDeadlineTo(Math.max(record.deadline(), sp().level().getGameTime() + 6 * 72_000L));
        walkingTo = null;
        if (sub != null) sub.resume();
    }

    @Override
    protected TaskState onTick() {
        try {
            return switch (phase) {
                case SITE -> site();
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

    /** Flat ground near it, nothing built there, room above: the nearest such, within 12 blocks. */
    private TaskState site() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        if (record.corner != null) {
            corner = record.corner;
        } else {
            BlockPos here = ProductionTask.standing(sp);
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (int dx = -12; dx <= 12; dx++) {
                for (int dz = -12; dz <= 12; dz++) {
                    BlockPos c = here.offset(dx, 0, dz);
                    if (!fits(level, c)) continue;
                    double d = c.offset(print.width() / 2, 0, print.depth() / 2).distSqr(here);
                    if (d < bestD) {
                        bestD = d;
                        best = c;
                    }
                }
            }
            if (best == null) return fail("there is no flat, clear ground for it within 12 blocks");
            corner = best;
        }
        say("Building a " + record.name + " at " + corner.toShortString() + ": " + materialsLine() + ".");
        phase = Phase.MATERIALS;
        return TaskState.RUNNING;
    }

    private boolean fits(ServerLevel level, BlockPos c) {
        // Never round someone standing there (its owner, a player passing).
        var box = new net.minecraft.world.phys.AABB(c.getX(), c.getY(), c.getZ(), c.getX() + print.width(), c.getY() + print.height() + 1,
                c.getZ() + print.depth());
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, box, p -> p != sp()).isEmpty()) return false;
        var placed = com.mineagent.engine.world.PlacedBlocks.of(level);
        String dim = level.dimension().location().toString();
        for (int x = 0; x < print.width(); x++) {
            for (int z = 0; z < print.depth(); z++) {
                BlockPos ground = c.offset(x, -1, z);
                if (!BlockHelper.canStandOn(level.getBlockState(ground))) return false;
                for (int y = 0; y <= print.height(); y++) {
                    BlockPos p = c.offset(x, y, z);
                    var st = level.getBlockState(p);
                    if (!(st.isAir() || st.canBeReplaced()) || !st.getFluidState().isEmpty() || placed.isPlaced(p.getX(), p.getY(), p.getZ())
                            || com.mineagent.engine.world.AfkFarms.protects(dim, p.getX(), p.getY(), p.getZ())) return false;
                }
            }
        }
        return true;
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
            if (st != TaskState.SUCCESS) return fail("I couldn't get the materials");
        }
        for (Map.Entry<String, Integer> e : print.materials().entrySet()) {
            int have = ProductionTask.count(sp, ProductionTask.item(e.getKey()));
            if (have >= e.getValue()) continue;
            sub = new ProductionTask(player, new ProductionTask.ProduceRecord(record.toolCallId() + ":" + e.getKey(), e.getKey(),
                    e.getValue() - have, null));
            sub.start();
            return TaskState.RUNNING;
        }
        phase = Phase.INSIDE;
        index = 0;
        return TaskState.RUNNING;
    }

    /** Each block in order: placed, or placed at the top of a jump when it is above its eyes' reach of a face. */
    private TaskState place() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        var input = TaskContext.inputDriver(player);
        while (index < print.blocks().size()) {
            Blueprint.Block b = print.blocks().get(index);
            if (b.fromOutside()) {
                index++;
                continue;
            }
            BlockPos p = at(b.x(), b.y(), b.z());
            Block want = block(b.id());
            if (level.getBlockState(p).is(want)) {
                index++;
                tries = 0;
                continue;
            }
            int feet = ProductionTask.standing(sp).getY();
            boolean high = p.getY() >= feet + 2;
            if (high) input.setJumping(true);
            if ((!high || !sp.onGround() && sp.getY() >= feet + 0.9) && Placement.placeBlock(sp, p, want.defaultBlockState())) {
                input.setJumping(false);
                index++;
                tries = 0;
                return TaskState.RUNNING;
            }
            if (++tries > 80) {
                // Not this one now: the check finds it, and puts it in again.
                input.setJumping(false);
                index++;
                tries = 0;
            }
            return TaskState.RUNNING;
        }
        input.clear();
        phase = Phase.OUTSIDE;
        return TaskState.RUNNING;
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
        String line = missing.isEmpty()
                ? "Built the " + record.name + " at " + corner.toShortString() + ": " + materialsLine() + "; every block checked."
                : "Built the " + record.name + " at " + corner.toShortString() + ", but " + missing.size() + " blocks are missing; I couldn't place them.";
        say(line);
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.journal().record("built", line, level.getGameTime());
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
        if (navFailed && ++tries > 5) return fail("I couldn't get to " + p.toShortString());
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
