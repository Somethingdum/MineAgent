package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.gear.Junk;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.pathing.util.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Its junk thrown away (SMH plan 70: "dumping junk items it doesn't need in lava, or the ocean"): the rock, dirt and mob
 * drops past what it keeps ({@link Junk}), never anything enchanted or named or that its stockpile keeps, thrown into
 * lava within 24 blocks from a spot two or three blocks back (never beside it), else into deep water; with neither near
 * it keeps carrying it and says so. Each toss is aimed as the game will move it ({@link com.mineagent.engine.act.TossAim}),
 * at the middle of a pool where there is one.
 */
public class DumpJunkTask extends CompanionTask<DumpJunkTask.DumpRecord> {
    public static final class DumpRecord extends TaskRecord {
        /** What it keeps whatever the junk rules say (its stockpile's targets). */
        public final java.util.Set<String> keep;

        public DumpRecord(String toolCallId, java.util.Set<String> keep) {
            super(toolCallId);
            this.keep = keep == null ? java.util.Set.of() : java.util.Set.copyOf(keep);
        }
    }

    private enum Phase { FIND, WALK, THROW, DONE }

    private PlayerNav nav;
    private Phase phase = Phase.FIND;
    private BlockPos target, stand;
    private boolean lava;
    private List<Junk.Excess> junk = List.of();
    private int index, ticks;
    private final Map<String, Integer> thrown = new LinkedHashMap<>();
    private String outcome = "";

    public DumpJunkTask(AgentPlayer player, DumpRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
    }

    /** What it carries that may be thrown: nothing enchanted, named or damaged-and-worth-mending. */
    public static Map<String, Integer> throwable(ServerPlayer sp) {
        Map<String, Integer> out = new LinkedHashMap<>();
        var inv = sp.getInventory();
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || st.isEnchanted() || st.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) continue;
            out.merge(BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
        }
        return out;
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        try {
            ticks++;
            switch (phase) {
                case FIND -> {
                    junk = Junk.excess(throwable(sp), record.keep);
                    if (junk.isEmpty()) return end(true, "nothing in my pack is junk");
                    if (!find(level, sp, true) && !find(level, sp, false)) return end(false, "there's no lava or deep water near to throw it in, so I'm keeping it");
                    phase = Phase.WALK;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case WALK -> {
                    if (sp.blockPosition().distSqr(stand) <= 1) {
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                        phase = Phase.THROW;
                        index = 0;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (ticks > 900) return end(false, "I couldn't get near the " + (lava ? "lava" : "water"));
                    if (ticks % 40 == 1 || !nav.isNavigating()) nav.navigateNear(stand.getX(), stand.getY(), stand.getZ(), 0);
                    nav.tick();
                    return TaskState.RUNNING;
                }
                case THROW -> {
                    if (index >= junk.size()) return end(true, null);
                    if (ticks % 3 != 0) return TaskState.RUNNING; // a throw at a time, as a player tosses them
                    Junk.Excess e = junk.get(index);
                    int left = e.count() - thrown.getOrDefault(e.item(), 0);
                    if (left <= 0) {
                        index++;
                        return TaskState.RUNNING;
                    }
                    // Facing it, at the pitch whose toss comes down in it (act/TossAim: as the game moves a tossed item).
                    sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
                    double dx = target.getX() + 0.5 - sp.getX(), dz = target.getZ() + 0.5 - sp.getZ();
                    double pitch = com.mineagent.engine.act.TossAim.pitchFor(Math.sqrt(dx * dx + dz * dz),
                            sp.getEyeY() - com.mineagent.engine.act.TossAim.BELOW_EYES - (target.getY() + com.mineagent.engine.act.TossAim.FLUID_TOP));
                    if (Double.isNaN(pitch)) {
                        if (ticks > 60) return end(false, "I couldn't throw it that far from here");
                        nav.navigateNear(stand.getX(), stand.getY(), stand.getZ(), 0); // back to its spot
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    sp.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
                    sp.setXRot((float) pitch);
                    var inv = sp.getInventory();
                    for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) {
                        ItemStack st = inv.getItem(i);
                        if (st.isEmpty() || st.isEnchanted() || st.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                                || !BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(e.item())) continue;
                        int n = Math.min(left, st.getCount());
                        ItemStack out = inv.removeItem(i, n);
                        sp.drop(out, false, false);
                        thrown.merge(e.item(), n, Integer::sum);
                        break;
                    }
                    if (thrown.getOrDefault(e.item(), 0) >= e.count() || sp.getInventory().countItem(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(e.item()))) == 0) index++;
                    TaskContext.syncInventory(sp);
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

    /**
     * Lava (or deep water) within 24, and a spot two to three and a half blocks from it to throw from: ground to stand on,
     * room for it, no lava beside the spot itself, and nothing between its eyes and the target. The middle of a pool
     * before its edge (a toss that lands a little long or short still lands in it), then the nearest.
     */
    private boolean find(ServerLevel level, ServerPlayer sp, boolean wantLava) {
        BlockPos at = sp.blockPosition();
        List<BlockPos> cells = new ArrayList<>();
        Map<BlockPos, Double> score = new java.util.HashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-24, -6, -24), at.offset(24, 6, 24))) {
            if (!fluid(level, p, wantLava)) continue;
            if (!level.getBlockState(p.above()).isAir()) continue; // open over it, so a throw can land in it
            int round = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if ((dx != 0 || dz != 0) && fluid(level, p.offset(dx, 0, dz), wantLava)) round++;
            BlockPos q = p.immutable();
            cells.add(q);
            score.put(q, Math.sqrt(q.distSqr(at)) + (8 - round) * 4.0);
        }
        cells.sort(java.util.Comparator.comparingDouble(score::get));
        for (int i = 0; i < Math.min(64, cells.size()); i++) {
            BlockPos s = standFor(level, sp, cells.get(i));
            if (s == null) continue;
            target = cells.get(i);
            stand = s;
            lava = wantLava;
            return true;
        }
        return false;
    }

    /** Lava, or a source of water with water under it (deep enough that what is thrown in sinks or floats off). */
    private static boolean fluid(ServerLevel level, BlockPos p, boolean wantLava) {
        var f = level.getFluidState(p);
        return wantLava ? f.is(FluidTags.LAVA) : f.is(FluidTags.WATER) && f.isSource() && level.getFluidState(p.below()).is(FluidTags.WATER);
    }

    /** The best throw is from about this far: a level toss from a block above the surface comes down here. */
    static final double THROW_FROM = 2.75;

    private static BlockPos standFor(ServerLevel level, ServerPlayer sp, BlockPos t) {
        BlockPos best = null;
        double bestOff = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double h = Math.sqrt(dx * dx + dz * dz);
                if (h < 2.0 || h > 3.5 || Math.abs(h - THROW_FROM) >= bestOff) continue;
                for (int dy = 1; dy >= 0; dy--) {
                    BlockPos s = t.offset(dx, dy, dz);
                    if (!BlockHelper.isPassable(level.getBlockState(s)) || !BlockHelper.isPassable(level.getBlockState(s.above()))
                            || !BlockHelper.canStandOn(level.getBlockState(s.below())) || !level.getFluidState(s).isEmpty()) continue;
                    boolean lavaBeside = false;
                    for (BlockPos n : BlockPos.betweenClosed(s.offset(-1, -1, -1), s.offset(1, 1, 1))) {
                        if (level.getFluidState(n).is(FluidTags.LAVA)) lavaBeside = true;
                    }
                    if (lavaBeside) continue;
                    Vec3 eye = Vec3.atBottomCenterOf(s).add(0, 1.62, 0);
                    var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, Vec3.atCenterOf(t).add(0, 0.5, 0),
                            net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, sp));
                    if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS && !hit.getBlockPos().equals(t)) continue;
                    best = s.immutable();
                    bestOff = Math.abs(h - THROW_FROM);
                    break;
                }
            }
        }
        return best;
    }

    private TaskState end(boolean ok, String why) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        phase = Phase.DONE;
        List<String> parts = new ArrayList<>();
        thrown.forEach((item, n) -> parts.add(n + " " + com.mineagent.engine.planning.production.ProductionPlanner.plural(item, n)));
        outcome = parts.isEmpty() ? (why == null ? "nothing thrown" : why)
                : "Threw " + String.join(", ", parts) + " into the " + (lava ? "lava" : "water") + (why == null ? "" : "; " + why);
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(outcome + ".");
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
