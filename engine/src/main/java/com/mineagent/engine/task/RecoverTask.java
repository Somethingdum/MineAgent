package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.entity.CompanionEntity;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.world.PlaceKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Your things after you die (SMH plan 70, MA10.8): it goes to where you died (the game keeps the place) before they
 * vanish, picks up what lies there, and hands it back to you, or, when you're not about, puts it in the chest at home.
 */
public class RecoverTask extends CompanionTask<RecoverTask.RecoverRecord> {
    public static final class RecoverRecord extends TaskRecord {
        public final String dim;
        public final BlockPos at;

        public RecoverRecord(String toolCallId, String dim, BlockPos at) {
            super(toolCallId);
            this.dim = dim;
            this.at = at.immutable();
        }
    }

    private enum Phase { GO, PICK, BACK, DONE, FAILED }

    private Phase phase = Phase.GO;
    private PlayerNav nav;
    private volatile boolean navFailed;
    private BlockPos walking;
    private int ticks, stuck;
    private final Map<String, Integer> before = new HashMap<>();
    private final Map<String, Integer> got = new LinkedHashMap<>();
    private final java.util.Set<java.util.UUID> skipped = new java.util.HashSet<>();
    private java.util.UUID standingOn;
    private int standingTicks;
    private String outcome = "";

    public RecoverTask(AgentPlayer player, RecoverRecord record) {
        super(player, record);
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
        walking = null;
        if (!counted) {
            // What it carried, and what of theirs lay there, at the start: what it picks up on the way counts too.
            counted = true;
            ServerPlayer sp = sp();
            snapshot(sp);
            for (ItemEntity e : sp.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(record.at).inflate(8, 4, 8),
                    x -> x.isAlive() && com.mineagent.engine.survival.Manners.mayTake(x, sp))) {
                theirs.add(BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString());
            }
        }
    }

    private boolean counted;
    private final java.util.Set<String> theirs = new java.util.HashSet<>();

    private ServerPlayer sp() {
        return TaskContext.serverPlayer(player);
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        if (!level.dimension().location().toString().equals(record.dim)) return end(false, "your things are in another dimension");
        ticks++;
        switch (phase) {
            case GO -> {
                if (sp.blockPosition().distSqr(record.at) <= 9) {
                    phase = Phase.PICK;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                if (ticks > 3_600) return end(false, "I couldn't get to where you died");
                go(record.at, 2);
                return TaskState.RUNNING;
            }
            case PICK -> {
                ItemEntity near = null;
                for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(record.at).inflate(8, 4, 8),
                        x -> x.isAlive() && !skipped.contains(x.getUUID()) && com.mineagent.engine.survival.Manners.mayTake(x, sp))) {
                    if (near == null || e.distanceToSqr(sp) < near.distanceToSqr(sp)) near = e;
                }
                // One it stands on and can't take (a full pack): left, not waited on.
                if (near != null && near.distanceToSqr(sp) < 2.25 && !near.hasPickUpDelay()) {
                    if (near.getUUID().equals(standingOn)) {
                        if (++standingTicks > 40) skipped.add(near.getUUID());
                    } else {
                        standingOn = near.getUUID();
                        standingTicks = 0;
                    }
                }
                if (near == null || ticks > 1_200) {
                    tally(sp);
                    if (got.isEmpty()) return end(false, "nothing of yours was left there");
                    phase = Phase.BACK;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                go(near.blockPosition(), 0);
                return TaskState.RUNNING;
            }
            case BACK -> {
                return back(sp);
            }
            default -> {
                return phase == Phase.DONE ? TaskState.SUCCESS : TaskState.FAILED;
            }
        }
    }

    /** To you if you're here and alive, else into the chest at home. */
    private TaskState back(ServerPlayer sp) {
        ServerPlayer owner = ((CompanionEntity) player).serverPlayerOwner();
        var loop = TaskContext.agentLoop(player);
        boolean here = owner != null && owner.isAlive() && owner.serverLevel() == sp.serverLevel() && (loop == null || !loop.ownerAfk());
        if (here) {
            if (owner.distanceTo(sp) > 2.5) {
                if (ticks > 2_400) return end(false, "I couldn't get to you");
                go(owner.blockPosition(), 2);
                return TaskState.RUNNING;
            }
            sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, owner.getEyePosition());
            handOver(sp, null);
            return end(true, "I got your things back: " + line() + ".");
        }
        if (!chestLooked) {
            chestLooked = true;
            chestAt = chestNear(sp, home(sp));
            if (chestAt == null && owner != null && owner.getRespawnPosition() != null
                    && owner.getRespawnDimension() == sp.serverLevel().dimension()) {
                chestAt = chestNear(sp, owner.getRespawnPosition()); // by their bed
            }
        }
        BlockPos chest = chestAt;
        if (chest == null) return end(true, "I've got your things (" + line() + "); there's no chest at home, so I'm keeping them for you.");
        if (!sp.canInteractWithBlock(chest, 0.0)) {
            if (ticks > 2_400) return end(false, "I couldn't get to the chest at home");
            go(chest, 1);
            return TaskState.RUNNING;
        }
        Container c = HopperBlockEntity.getContainerAt(sp.serverLevel(), chest);
        if (c == null) return end(false, "the chest at home is gone");
        handOver(sp, c);
        return end(true, "I got your things back and put them in the chest at home: " + line() + ".");
    }

    /** The chest it puts their things in, looked for once. */
    private boolean chestLooked;
    private BlockPos chestAt;

    /** What it picked up of theirs, handed over (into {@code chest}, or tossed to them). */
    private void handOver(ServerPlayer sp, Container chest) {
        var inv = sp.getInventory();
        Map<String, Integer> left = new HashMap<>(got);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            int owe = left.getOrDefault(id, 0);
            if (owe <= 0) continue;
            ItemStack out = inv.removeItem(i, Math.min(owe, s.getCount()));
            left.merge(id, -out.getCount(), Integer::sum);
            if (chest != null) {
                ItemStack rest = HopperBlockEntity.addItem(null, chest, out, null);
                if (!rest.isEmpty()) sp.getInventory().add(rest);
            } else {
                sp.drop(out, false, true);
            }
        }
        if (chest != null) chest.setChanged();
        TaskContext.syncInventory(sp);
    }

    /** The nearest chest or barrel within 6 blocks of {@code at}, or null. */
    private static BlockPos chestNear(ServerPlayer sp, BlockPos at) {
        if (at == null) return null;
        BlockPos best = null;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-6, -3, -6), at.offset(6, 3, 6))) {
            var st = sp.serverLevel().getBlockState(p);
            if ((st.is(Blocks.CHEST) || st.is(Blocks.BARREL) || st.is(Blocks.TRAPPED_CHEST))
                    && (best == null || p.distSqr(at) < best.distSqr(at))) best = p.immutable();
        }
        return best;
    }

    /** Home as it remembers it, or null. */
    private BlockPos home(ServerPlayer sp) {
        var loop = TaskContext.agentLoop(player);
        if (loop != null) {
            var here = PlaceKeeper.point(sp);
            var h = loop.placeKeeper().memory().find("home", here);
            if (h.isPresent() && h.get().at().dim().equals(here.dim())) return new BlockPos(h.get().at().x(), h.get().at().y(), h.get().at().z());
        }
        return null;
    }

    private void snapshot(ServerPlayer sp) {
        before.clear();
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) before.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
    }

    private void tally(ServerPlayer sp) {
        got.clear();
        Map<String, Integer> now = new HashMap<>();
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) now.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        now.forEach((id, n) -> {
            int d = n - before.getOrDefault(id, 0);
            if (d > 0 && theirs.contains(id)) got.put(id, d);
        });
    }

    private String line() {
        java.util.List<String> parts = new java.util.ArrayList<>();
        got.forEach((id, n) -> parts.add(n + " " + com.mineagent.engine.planning.production.ProductionPlanner.plural(id, n)));
        return String.join(", ", parts);
    }

    private void go(BlockPos p, int radius) {
        if (walking == null || !walking.equals(p) || !nav.isNavigating() && ++stuck % 20 == 0) {
            walking = p.immutable();
            navFailed = false;
            if (radius <= 0) nav.navigateTo(p.getX(), p.getY(), p.getZ());
            else nav.navigateNear(p.getX(), p.getY(), p.getZ(), radius);
        }
        nav.tick();
    }

    private TaskState end(boolean ok, String line) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        outcome = line;
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(ok ? line : "I couldn't get your things back: " + line + ".");
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
        if (nav != null) nav.cancel();
        walking = null;
        TaskContext.inputDriver(player).clear();
    }
}
