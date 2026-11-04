package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.pathing.execute.PlayerNav;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A farm made as a player makes one (SMH plan 70, its owner: "build a base and basic farms"): a flat 9 by 9 of grass or
 * dirt near home, its middle dug out and a water bucket poured in (every block of a 9 by 9 is within 4 of it, so all of
 * it stays wet), the 80 round it tilled with a hoe from above, and seeds planted on the farmland, as many as it has
 * (wheat seeds first, then carrots, potatoes, beetroot seeds). With no seeds, grass near is broken for them first. Never
 * on blocks a player put down.
 */
public class FarmTask extends CompanionTask<FarmTask.FarmRecord> {
    public static final class FarmRecord extends TaskRecord {
        /** Near where (null: near home, else where it stands). */
        public final BlockPos near;

        public FarmRecord(String toolCallId, BlockPos near) {
            super(toolCallId);
            this.near = near == null ? null : near.immutable();
        }
    }

    /** Half the side of the farm: 4 each way from the water. */
    static final int HALF = 4;
    /** The seeds it plants, the first it has. */
    static final List<Item> SEEDS = List.of(Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS);

    private enum Phase { SITE, SEEDS, DIG, WATER, TILL, PLANT, DONE }

    private PlayerNav nav;
    private Phase phase = Phase.SITE;
    private BlockPos center;
    private List<BlockPos> cells = List.of();
    private int index, ticks, tilled, planted, grassBroken, cooldown;
    /** Cells given up (no way to them, or the hoe or seed won't take), and tries on each. */
    private final java.util.Set<BlockPos> given = new java.util.HashSet<>();
    private final java.util.Map<BlockPos, Integer> tries = new java.util.HashMap<>();
    private int walkTicks;
    private BlockPos grass, pickup;
    private int pickupTicks;
    private String outcome = "";

    public FarmTask(AgentPlayer player, FarmRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        try {
            if (cooldown > 0) {
                cooldown--;
                return TaskState.RUNNING;
            }
            switch (phase) {
                case SITE -> {
                    if (hoe(sp) < 0) return end(false, "I have no hoe to till with");
                    if (sp.getInventory().countItem(Items.WATER_BUCKET) == 0) return end(false, "I have no water bucket for it");
                    BlockPos around = record.near;
                    if (around == null) {
                        var loop = TaskContext.agentLoop(player);
                        around = loop == null ? null : loop.homeIn(level.dimension().location().toString());
                    }
                    if (around == null) around = sp.blockPosition();
                    center = site(level, around);
                    if (center == null) return end(false, "there's no flat 9 by 9 of grass or dirt near to farm on");
                    cells = new ArrayList<>();
                    for (int dx = -HALF; dx <= HALF; dx++) {
                        // Row by row, back and forth, as a player works a field.
                        for (int k = -HALF; k <= HALF; k++) {
                            int dz = (dx + HALF) % 2 == 0 ? k : -k;
                            if (dx == 0 && dz == 0) continue;
                            cells.add(center.offset(dx, 0, dz));
                        }
                    }
                    phase = seeds(sp) == null ? Phase.SEEDS : Phase.DIG;
                    return TaskState.RUNNING;
                }
                case SEEDS -> {
                    return gatherSeeds(sp, level);
                }
                case DIG -> {
                    if (level.getBlockState(center).isAir() || level.getFluidState(center).is(FluidTags.WATER)) {
                        phase = Phase.WATER;
                        return TaskState.RUNNING;
                    }
                    // From beside it (never the block under its own feet).
                    if (!near(sp, center, 4.0) || sp.blockPosition().getX() == center.getX() && sp.blockPosition().getZ() == center.getZ()) {
                        return walk(center.offset(2, 0, 0), 1);
                    }
                    nav.cancel();
                    if (ticks++ % 40 == 0) BlockDigger.startBreaking(sp, center);
                    if (ticks > 400) return end(false, "I couldn't dig out the middle for the water");
                    return TaskState.RUNNING;
                }
                case WATER -> {
                    if (level.getFluidState(center).is(FluidTags.WATER) && level.getFluidState(center).isSource()) {
                        phase = Phase.TILL;
                        index = 0;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    // In the hole it dug, the bucket poured straight down: from a step away the look would catch the
                    // next block's top and the water would run over the field.
                    double hx = sp.getX() - (center.getX() + 0.5), hz = sp.getZ() - (center.getZ() + 0.5);
                    if (hx * hx + hz * hz > 0.3 * 0.3 || sp.getY() > center.getY() + 0.5) {
                        if (++ticks > 400) return end(false, "I couldn't get into the middle to pour the water");
                        return walk(center.below(), 0);
                    }
                    nav.cancel();
                    if (!TaskContext.selectInventoryItem(player, "minecraft:water_bucket")) return end(false, "I have no water bucket for it");
                    useOnTop(sp, center.below());
                    cooldown = 5;
                    if (++ticks > 420) return end(false, "the water wouldn't go in");
                    return TaskState.RUNNING;
                }
                case TILL -> {
                    // As a player works a field: every cell within reach from where it stands, then the nearest left.
                    BlockPos c = pick(sp, level, true);
                    if (c == null) {
                        phase = Phase.PLANT;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (!near(sp, c, 4.0)) {
                        if (++walkTicks > 300) {
                            given.add(c);
                            walkTicks = 0;
                        }
                        return walk(c, 1);
                    }
                    walkTicks = 0;
                    nav.cancel();
                    BlockPos up = c.above();
                    if (!level.getBlockState(up).isAir()) { // grass or a flower on it: off first, as a player clears it
                        BlockDigger.startBreaking(sp, up);
                        cooldown = 2;
                        if (tries.merge(c, 1, Integer::sum) > 8) given.add(c);
                        return TaskState.RUNNING;
                    }
                    int h = hoe(sp);
                    if (h < 0) return end(count(level, false) > 0, "my hoe broke");
                    TaskContext.selectInventoryItem(player, BuiltInRegistries.ITEM.getKey(sp.getInventory().getItem(h).getItem()).toString());
                    useOnTop(sp, c);
                    cooldown = 2;
                    if (tries.merge(c, 1, Integer::sum) > 6) given.add(c);
                    return TaskState.RUNNING;
                }
                case PLANT -> {
                    Item seed = seeds(sp);
                    BlockPos c = seed == null ? null : pick(sp, level, false);
                    if (c == null) return end(true, null);
                    if (!near(sp, c, 4.0)) {
                        if (++walkTicks > 300) {
                            given.add(c);
                            walkTicks = 0;
                        }
                        return walk(c, 1);
                    }
                    walkTicks = 0;
                    nav.cancel();
                    TaskContext.selectInventoryItem(player, BuiltInRegistries.ITEM.getKey(seed).toString());
                    useOnTop(sp, c);
                    cooldown = 2;
                    if (tries.merge(c.above(), 1, Integer::sum) > 6) given.add(c);
                    return TaskState.RUNNING;
                }
                default -> {
                    return end(true, null);
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    private void next() {
        index++;
        ticks = 0;
    }

    private TaskState walk(BlockPos to, int within) {
        if (ticks % 20 == 0) nav.navigateNear(to.getX(), to.getY() + 1, to.getZ(), within);
        ticks++;
        nav.tick();
        return TaskState.RUNNING;
    }

    private static boolean near(ServerPlayer sp, BlockPos p, double reach) {
        return Vec3.atCenterOf(p).add(0, 0.5, 0).distanceToSqr(sp.getEyePosition()) <= reach * reach;
    }

    /** The pack slot of a hoe, or -1. */
    private static int hoe(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) if (inv.getItem(i).getItem() instanceof HoeItem) return i;
        return -1;
    }

    /** The seeds it has to plant, the first of {@link #SEEDS}, or null. */
    private static Item seeds(ServerPlayer sp) {
        for (Item s : SEEDS) if (sp.getInventory().countItem(s) > 0) return s;
        return null;
    }

    /**
     * The next cell to work: one still to till (grass or dirt, open over it) or to plant (farmland, open over it), the
     * nearest within reach first, else the nearest of all; none given up. Null when the field is done.
     */
    private BlockPos pick(ServerPlayer sp, ServerLevel level, boolean till) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos c : cells) {
            if (given.contains(c)) continue;
            var st = level.getBlockState(c);
            var up = level.getBlockState(c.above());
            boolean todo = till ? (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT)) && (up.isAir() || up.canBeReplaced() && level.getFluidState(c.above()).isEmpty())
                    : st.is(Blocks.FARMLAND) && up.isAir();
            if (!todo) continue;
            double d = Vec3.atCenterOf(c).add(0, 0.5, 0).distanceToSqr(sp.getEyePosition());
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    /** Its farmland, or its crops, counted over the field. */
    private int count(ServerLevel level, boolean crops) {
        int n = 0;
        for (BlockPos c : cells) {
            if (crops ? level.getBlockState(c.above()).getBlock() instanceof net.minecraft.world.level.block.CropBlock
                    : level.getBlockState(c).is(Blocks.FARMLAND)) n++;
        }
        return n;
    }

    /**
     * Its hand used on the top of a block, as a right click on it from above: a hoe tills it, seeds go on it, a bucket
     * pours into the cell over it. Only within a player's reach.
     */
    static boolean useOnTop(ServerPlayer sp, BlockPos pos) {
        Vec3 top = Vec3.atCenterOf(pos).add(0, 0.5, 0);
        if (top.distanceToSqr(sp.getEyePosition()) > 4.5 * 4.5) return false;
        sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, top);
        ItemStack held = sp.getItemInHand(InteractionHand.MAIN_HAND);
        BlockHitResult hit = new BlockHitResult(top, Direction.UP, pos, false);
        InteractionResult r = sp.gameMode.useItemOn(sp, sp.serverLevel(), held, InteractionHand.MAIN_HAND, hit);
        if (r == InteractionResult.PASS && !held.isEmpty()) r = sp.gameMode.useItem(sp, sp.serverLevel(), held, InteractionHand.MAIN_HAND);
        if (r.shouldSwing()) sp.swing(InteractionHand.MAIN_HAND);
        return r.consumesAction();
    }

    /**
     * The middle of a flat 9 by 9 of grass or dirt with open air over it, none of it put down by a player, nearest to
     * {@code around} within 16; null when there is none.
     */
    static BlockPos site(ServerLevel level, BlockPos around) {
        var placed = com.mineagent.engine.world.PlacedBlocks.of(level);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int r = 0; r <= 16; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = around.getX() + dx, z = around.getZ() + dz;
                    if (!level.isLoaded(new BlockPos(x, around.getY(), z))) continue;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    BlockPos c = new BlockPos(x, y, z);
                    if (!fits(level, placed, c)) continue;
                    double d = c.distSqr(around);
                    if (d < bestD) {
                        bestD = d;
                        best = c;
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static boolean fits(ServerLevel level, com.mineagent.engine.world.PlacedBlocks placed, BlockPos c) {
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                BlockPos p = c.offset(dx, 0, dz);
                if (!level.isLoaded(p)) return false;
                var st = level.getBlockState(p);
                if (!(st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT))) return false;
                if (placed.isPlaced(p.getX(), p.getY(), p.getZ())) return false;
                var up = level.getBlockState(p.above());
                if (!(up.isAir() || up.canBeReplaced() && level.getFluidState(p.above()).isEmpty())) return false;
                if (!level.getBlockState(p.above(2)).isAir() && !level.getBlockState(p.above(2)).canBeReplaced()) return false;
            }
        }
        return true;
    }

    /** No seeds: grass near broken for them (one in eight drops wheat seeds), up to 80 tufts, then on with what it got. */
    private TaskState gatherSeeds(ServerPlayer sp, ServerLevel level) {
        if (pickup != null) { // onto where the tuft was, for what it dropped
            if (sp.position().distanceToSqr(Vec3.atBottomCenterOf(pickup)) < 0.8 || ++pickupTicks > 60) {
                pickup = null;
                pickupTicks = 0;
            } else {
                if (pickupTicks % 20 == 1) nav.navigateNear(pickup.getX(), pickup.getY(), pickup.getZ(), 0);
                nav.tick();
            }
            return TaskState.RUNNING;
        }
        if (seeds(sp) != null && (sp.getInventory().countItem(Items.WHEAT_SEEDS) >= 16 || grassBroken >= 80)) {
            phase = Phase.DIG;
            ticks = 0;
            return TaskState.RUNNING;
        }
        if (grassBroken >= 80) {
            if (seeds(sp) == null) return end(false, "the grass near gave me no seeds");
            phase = Phase.DIG;
            return TaskState.RUNNING;
        }
        if (grass == null || !isGrass(level.getBlockState(grass))) {
            grass = nearestGrass(level, sp.blockPosition());
            ticks = 0;
            if (grass == null) {
                if (seeds(sp) == null) return end(false, "I have no seeds, and no grass near to get them from");
                phase = Phase.DIG;
                return TaskState.RUNNING;
            }
        }
        if (!near(sp, grass.below(), 4.0)) {
            if (++ticks > 300) {
                grass = null;
                grassBroken++;
                return TaskState.RUNNING;
            }
            return walk(grass.below(), 1);
        }
        nav.cancel();
        BlockDigger.startBreaking(sp, grass);
        grassBroken++;
        pickup = grass; // a seed drops where the tuft was: a step onto it picks it up
        pickupTicks = 0;
        grass = null;
        cooldown = 2;
        return TaskState.RUNNING;
    }

    private static boolean isGrass(net.minecraft.world.level.block.state.BlockState st) {
        return st.is(Blocks.SHORT_GRASS) || st.is(Blocks.TALL_GRASS) || st.is(Blocks.FERN);
    }

    private static BlockPos nearestGrass(ServerLevel level, BlockPos from) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(from.offset(-16, -4, -16), from.offset(16, 4, 16))) {
            if (!level.isLoaded(p) || !isGrass(level.getBlockState(p))) continue;
            double d = p.distSqr(from);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    private TaskState end(boolean ok, String why) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        phase = Phase.DONE;
        ok = ok && center != null;
        if (center != null) {
            ServerLevel lvl = TaskContext.serverPlayer(player).serverLevel();
            tilled = count(lvl, false);
            planted = count(lvl, true);
        }
        if (ok) {
            outcome = "Made a farm at " + center.getX() + ", " + center.getZ() + ": " + tilled + " farmland, "
                    + (planted == 0 ? "nothing planted yet" : planted + " planted") + ", water in the middle" + (why == null ? "" : "; " + why);
        } else {
            outcome = why == null ? "it didn't work out" : why;
        }
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(ok ? outcome + "." : "I couldn't make the farm: " + outcome + ".");
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
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
    }
}
