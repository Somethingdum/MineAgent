package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Placement;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.pathing.util.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A nether portal from the obsidian it carries (SMH plan 70, MA11.1): the smallest frame, 4 wide and 5 high, built as a
 * player does. With 14 obsidian the corners are obsidian too; with 10 to 13 they are any block it carries, put in to
 * hold the sides and the top and broken out once the portal is lit. Lit with flint and steel (made first if it has the
 * makings) on the top of a bottom block. Only the obsidian way for now; the bucket way (casting it from lava) is not
 * built.
 */
public class PortalTask extends CompanionTask<PortalTask.PortalRecord> {
    public static final class PortalRecord extends TaskRecord {
        public PortalRecord(String toolCallId) {
            super(toolCallId);
        }
    }

    private enum Phase { SITE, LIGHTER, GO, BUILD, LIGHT, DOWN, CLEAN, DONE, FAILED }

    /** One block of the frame: where (x along the frame, y up from the ground), and whether it is a corner. */
    private record Cell(int x, int y, boolean corner) {}

    /** The order a player builds it in: corners under the sides, bottom, sides up, corners over them, top. */
    private static final List<Cell> FRAME = List.of(
            new Cell(0, 1, true), new Cell(3, 1, true), new Cell(1, 1, false), new Cell(2, 1, false),
            new Cell(0, 2, false), new Cell(0, 3, false), new Cell(0, 4, false),
            new Cell(3, 2, false), new Cell(3, 3, false), new Cell(3, 4, false),
            new Cell(0, 5, true), new Cell(3, 5, true), new Cell(1, 5, false), new Cell(2, 5, false));

    private Phase phase = Phase.SITE;
    private BlockPos corner;
    private Direction along = Direction.EAST;
    private Direction facing = Direction.SOUTH;
    private boolean obsidianCorners;
    private BlockState filler;
    private int index, tries, ticks;
    private CompanionTask<?> sub;
    private PlayerNav nav;
    private BlockPos walkingTo;
    private String outcome = "";

    public PortalTask(AgentPlayer player, PortalRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
        walkingTo = null;
        if (sub != null) sub.resume();
    }

    private ServerPlayer sp() {
        return TaskContext.serverPlayer(player);
    }

    /** A frame cell in the world: {@code x} along the frame from the corner, {@code y} up from the ground. */
    private BlockPos at(int x, int y) {
        return corner.relative(along, x).above(y - 1);
    }

    /** Where it stands to build: two blocks in front of the frame's middle. */
    private BlockPos stand() {
        return corner.relative(along, 1).relative(facing.getOpposite(), 2);
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        ticks++;
        try {
            switch (phase) {
                case SITE -> {
                    int obsidian = ProductionTask.count(sp, Items.OBSIDIAN);
                    if (obsidian < 10) return end(false, "a portal takes 10 obsidian at the least, and I have " + obsidian);
                    obsidianCorners = obsidian >= 14;
                    // Blocks to stand on for the top rows (3), and for the corners when it has fewer than 14 obsidian (4).
                    filler = fillerBlock(sp, obsidianCorners ? 3 : 7);
                    if (filler == null) return end(false, "I need " + (obsidianCorners ? 3 : 7)
                            + " plain blocks as well (cobblestone or dirt), to stand on" + (obsidianCorners ? "" : " and hold the corners"));
                    if (!site(level, sp)) return end(false, "there is no flat, open ground for a portal within 8 blocks");
                    phase = Phase.LIGHTER;
                    return TaskState.RUNNING;
                }
                case LIGHTER -> {
                    if (sub != null) {
                        TaskState st = sub.tick();
                        if (st == TaskState.RUNNING) return TaskState.RUNNING;
                        sub = null;
                    }
                    if (ProductionTask.count(sp, Items.FLINT_AND_STEEL) > 0 || ProductionTask.count(sp, Items.FIRE_CHARGE) > 0) {
                        phase = Phase.GO;
                        return TaskState.RUNNING;
                    }
                    if (tries++ > 0) return end(false, "I have no flint and steel and couldn't make one");
                    sub = new ProductionTask(player, new ProductionTask.ProduceRecord(record.toolCallId() + ":lighter",
                            "minecraft:flint_and_steel", 1, null, false, true));
                    sub.start();
                    return TaskState.RUNNING;
                }
                case GO -> {
                    tries = 0;
                    BlockPos s = stand();
                    if (ProductionTask.standing(sp).distSqr(s) <= 1) {
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                        phase = Phase.BUILD;
                        return TaskState.RUNNING;
                    }
                    if (ticks > 1_200) return end(false, "I couldn't get to the spot in front of it");
                    if (walkingTo == null || !nav.isNavigating() && ticks % 20 == 0) {
                        walkingTo = s;
                        nav.navigateTo(s.getX(), s.getY(), s.getZ());
                    }
                    nav.tick();
                    return TaskState.RUNNING;
                }
                case BUILD -> {
                    while (index < FRAME.size()) {
                        Cell c = FRAME.get(index);
                        BlockPos p = at(c.x(), c.y());
                        BlockState want = c.corner() && !obsidianCorners ? filler : Blocks.OBSIDIAN.defaultBlockState();
                        if (level.getBlockState(p).is(want.getBlock())) {
                            index++;
                            tries = 0;
                            continue;
                        }
                        // Its eyes must be above the face it puts the block on (the top of the one under it): up the
                        // pillar in front, a block at a time, as the rows rise.
                        int need = heightFor(c.y());
                        if (raised < need) return pillarUp(sp, level);
                        if (sp.onGround() && Placement.placeBlock(sp, p, want)) {
                            index++;
                            tries = 0;
                            return TaskState.RUNNING;
                        }
                        if (++tries > 40) {
                            String why = Placement.whyNot(sp, p, want);
                            return end(false, "I couldn't put the frame's block in at " + p.toShortString() + (why == null ? "" : ": " + why));
                        }
                        return TaskState.RUNNING;
                    }
                    phase = Phase.LIGHT;
                    tries = 0;
                    return TaskState.RUNNING;
                }
                case LIGHT -> {
                    if (lit(level)) {
                        phase = Phase.DOWN;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (++tries > 60) return end(false, "the frame is up, but it wouldn't light");
                    if (tries % 10 != 1) return TaskState.RUNNING;
                    String lighter = ProductionTask.count(sp, Items.FLINT_AND_STEEL) > 0 ? "minecraft:flint_and_steel" : "minecraft:fire_charge";
                    if (!TaskContext.selectInventoryItemForHand(player, lighter, InteractionHand.MAIN_HAND)) {
                        return end(false, "I have nothing to light it with");
                    }
                    // The top of a bottom block, inside the frame: fire goes in, the portal opens.
                    BlockPos base = at(1, 1);
                    var hit = new BlockHitResult(Vec3.atCenterOf(base).add(0, 0.5, 0), Direction.UP, base, false);
                    sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, hit.getLocation());
                    sp.gameMode.useItemOn(sp, level, sp.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                    sp.swing(InteractionHand.MAIN_HAND);
                    TaskContext.syncInventory(sp);
                    return TaskState.RUNNING;
                }
                case DOWN -> {
                    // Off the pillar, away from the portal (three blocks down is no fall damage).
                    BlockPos off = stand().relative(facing.getOpposite(), 2);
                    if (raised == 0 || sp.onGround() && sp.getBlockY() <= stand().getY() && ProductionTask.standing(sp).distSqr(off) <= 2) {
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                        phase = Phase.CLEAN;
                        tries = 0;
                        return TaskState.RUNNING;
                    }
                    if (ticks > 400) return end(true, litLine() + " I couldn't get down from the blocks I stood on.");
                    if (walkingTo == null || !walkingTo.equals(off) || !nav.isNavigating() && ticks % 20 == 0) {
                        walkingTo = off;
                        nav.navigateTo(off.getX(), off.getY(), off.getZ());
                    }
                    nav.tick();
                    return TaskState.RUNNING;
                }
                case CLEAN -> {
                    // The pillar it stood on, taken down from the top.
                    for (int h = raised - 1; h >= 0; h--) {
                        BlockPos p = stand().above(h);
                        if (!level.getBlockState(p).is(filler.getBlock())) continue;
                        if (com.mineagent.engine.task.BlockDigger.breakBlock(sp, p) || ++tries > 300) tries = 0;
                        return TaskState.RUNNING;
                    }
                    return end(true, litLine());
                }
                default -> {
                    return phase == Phase.DONE ? TaskState.SUCCESS : TaskState.FAILED;
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    /** How many blocks up the pillar it stands. */
    private int raised;

    /** The pillar's height for a frame row: eyes above the top of the block under it (eyes are 1.62 up). */
    static int heightFor(int row) {
        return Math.max(0, (int) Math.ceil(row + 0.2 - 2.62 + 1.0) - 1);
    }

    /** One block up: a jump, and at its top a block where its feet were. */
    private TaskState pillarUp(ServerPlayer sp, ServerLevel level) {
        var input = TaskContext.inputDriver(player);
        BlockPos under = stand().above(raised);
        if (!level.getBlockState(under).isAir()) {
            // It is in: once it stands on it, a block higher.
            input.setJumping(false);
            if (sp.onGround() && sp.getY() >= under.getY() + 0.9) raised++;
            return TaskState.RUNNING;
        }
        input.setJumping(true);
        if (!sp.onGround() && sp.getY() >= under.getY() + 1.0) {
            if (Placement.placeBlock(sp, under, filler)) input.setJumping(false);
        }
        if (++tries > 200) return end(false, "I couldn't build up to reach the top of the frame");
        return TaskState.RUNNING;
    }

    private String litLine() {
        return "The portal is lit at " + at(1, 2).toShortString() + ".";
    }

    /** The portal is open: portal blocks inside the frame. */
    private boolean lit(ServerLevel level) {
        return level.getBlockState(at(1, 2)).is(Blocks.NETHER_PORTAL);
    }

    /** A plain full block it carries {@code need} of, for the pillar (and the corners). */
    private static BlockState fillerBlock(ServerPlayer sp, int need) {
        for (var b : List.of(Blocks.COBBLESTONE, Blocks.DIRT, Blocks.COBBLED_DEEPSLATE, Blocks.STONE, Blocks.NETHERRACK,
                Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS)) {
            if (ProductionTask.count(sp, b.asItem()) >= need) return b.defaultBlockState();
        }
        return null;
    }

    /**
     * A frame site near it: four columns of open air five high on solid ground, and open, standing ground two blocks in
     * front. The frame runs east-west or north-south, whichever fits nearest.
     */
    private boolean site(ServerLevel level, ServerPlayer sp) {
        BlockPos here = ProductionTask.standing(sp);
        BlockPos best = null;
        Direction bestAlong = null;
        double bestD = Double.MAX_VALUE;
        for (Direction a : new Direction[] {Direction.EAST, Direction.SOUTH}) {
            Direction f = a == Direction.EAST ? Direction.SOUTH : Direction.EAST;
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos c = here.offset(dx, 0, dz);
                    if (!fits(level, c, a, f)) continue;
                    double d = c.distSqr(here);
                    if (d < bestD) {
                        bestD = d;
                        best = c;
                        bestAlong = a;
                    }
                }
            }
        }
        if (best == null) return false;
        corner = best;
        along = bestAlong;
        facing = along == Direction.EAST ? Direction.SOUTH : Direction.EAST;
        return true;
    }

    private static boolean fits(ServerLevel level, BlockPos c, Direction along, Direction facing) {
        String dim = level.dimension().location().toString();
        // Never round someone standing there, nor where it will stand.
        BlockPos far = c.relative(along, 3).above(5);
        var box = new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(c), Vec3.atLowerCornerOf(far).add(1, 1, 1))
                .expandTowards(Vec3.atLowerCornerOf(facing.getOpposite().getNormal()).scale(3));
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, box, p -> !com.mineagent.engine.MineAgentEngine
                .isCompanionPlayer(p.getUUID())).isEmpty()) return false;
        for (int x = 0; x < 4; x++) {
            BlockPos col = c.relative(along, x);
            if (!BlockHelper.canStandOn(level.getBlockState(col.below()))) return false;
            for (int y = 0; y < 5; y++) {
                BlockPos p = col.above(y);
                var st = level.getBlockState(p);
                if (!st.isAir() || com.mineagent.engine.world.AfkFarms.protects(dim, p.getX(), p.getY(), p.getZ())) return false;
            }
        }
        // Standing room in front.
        BlockPos stand = c.relative(along, 1).relative(facing.getOpposite(), 2);
        return BlockHelper.canStandOn(level.getBlockState(stand.below())) && level.getBlockState(stand).isAir()
                && level.getBlockState(stand.above()).isAir()
                && level.getBlockState(c.relative(along, 1).relative(facing.getOpposite())).isAir();
    }

    private TaskState end(boolean ok, String line) {
        if (sub != null) sub.interrupt();
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        outcome = line;
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(ok ? line : "I couldn't build the portal: " + line + ".");
        phase = ok ? Phase.DONE : Phase.FAILED;
        return ok ? TaskState.SUCCESS : TaskState.FAILED;
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
