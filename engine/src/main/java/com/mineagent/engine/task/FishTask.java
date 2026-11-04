package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Interaction;
import com.mineagent.engine.pathing.execute.PlayerNav;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * Fishing (SMH plan 70, MA8.5, food that renews): to open water near it, the rod cast, and reeled in when a fish bites
 * (the game pulls the bobber under, which the server shows as the hook dropping fast in the water), as a player
 * watches the bobber. Until it has caught what was asked for, or the time is up.
 */
public class FishTask extends CompanionTask<FishTask.FishRecord> {
    public static final class FishRecord extends TaskRecord {
        public final int catches;

        public FishRecord(String toolCallId, int catches) {
            super(toolCallId);
            this.catches = Math.max(1, Math.min(64, catches));
        }
    }

    private enum Phase { WATER, CAST, WAIT, DONE, FAILED }

    private Phase phase = Phase.WATER;
    private PlayerNav nav;
    private BlockPos water, shore;
    private int ticks, waited, caught, casts;
    private int fishBefore;
    private String outcome = "";

    public FishTask(AgentPlayer player, FishRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
        record.extendDeadlineTo(Math.max(record.deadline(), TaskContext.serverPlayer(player).level().getGameTime() + 24_000L));
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ticks++;
        try {
            switch (phase) {
                case WATER -> {
                    if (ProductionTask.count(sp, Items.FISHING_ROD) == 0) return end(false, "I have no fishing rod");
                    if (water == null && !findWater(sp)) return end(false, "there's no open water near me");
                    if (ProductionTask.standing(sp).equals(shore) || sp.blockPosition().distSqr(shore) <= 1) {
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                        fishBefore = fish(sp);
                        phase = Phase.CAST;
                        return TaskState.RUNNING;
                    }
                    if (ticks > 1_200) return end(false, "I couldn't get to the water's edge");
                    if (ticks % 20 == 1) nav.navigateTo(shore.getX(), shore.getY(), shore.getZ());
                    nav.tick();
                    return TaskState.RUNNING;
                }
                case CAST -> {
                    if (!TaskContext.selectInventoryItemForHand(player, "minecraft:fishing_rod", InteractionHand.MAIN_HAND)) {
                        return end(caught > 0, caught > 0 ? line() : "my rod broke");
                    }
                    if (sp.fishing != null) {
                        phase = Phase.WAIT;
                        waited = 0;
                        return TaskState.RUNNING;
                    }
                    sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(water).add(0, 0.4, 0));
                    Interaction.useItem(sp, InteractionHand.MAIN_HAND);
                    casts++;
                    return TaskState.RUNNING;
                }
                case WAIT -> {
                    var hook = sp.fishing;
                    if (hook == null) {
                        phase = Phase.CAST;
                        return TaskState.RUNNING;
                    }
                    waited++;
                    // A bite: the bobber pulled under while it floats in the water.
                    boolean inWater = hook.level().getFluidState(hook.blockPosition()).is(Fluids.WATER)
                            || hook.level().getFluidState(hook.blockPosition().below()).is(Fluids.WATER);
                    boolean bite = inWater && hook.getDeltaMovement().y < -0.12 && waited > 20;
                    if (bite || waited > 900 || hook.onGround()) {
                        Interaction.useItem(sp, InteractionHand.MAIN_HAND); // reel in
                        TaskContext.syncInventory(sp);
                        int now = fish(sp);
                        if (now > fishBefore) caught += now - fishBefore;
                        fishBefore = now;
                        if (caught >= record.catches) return end(true, line());
                        if (casts > record.catches * 6 + 6) return end(caught > 0, caught > 0 ? line() : "nothing would bite");
                        phase = Phase.CAST;
                    }
                    return TaskState.RUNNING;
                }
                default -> {
                    return phase == Phase.DONE ? TaskState.SUCCESS : TaskState.FAILED;
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    /** Open water within 12 blocks (five wide round the spot) and a spot on the bank three or four blocks from it. */
    private boolean findWater(ServerPlayer sp) {
        var level = sp.serverLevel();
        BlockPos at = sp.blockPosition();
        double best = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-12, -3, -12), at.offset(12, 2, 12))) {
            if (!level.getFluidState(p).is(Fluids.WATER) || !level.getBlockState(p.above()).isAir()) continue;
            // Water five wide round the spot it casts at, so the bobber lands in it.
            int around = 0;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (level.getFluidState(p.offset(dx, 0, dz)).is(Fluids.WATER)) around++;
            if (around < 25) continue;
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                for (int out = 3; out <= 4; out++) {
                    BlockPos bank = p.relative(d, out).above();
                    if (!com.mineagent.engine.pathing.util.BlockHelper.canStandOn(level.getBlockState(bank.below()))
                            || !level.getBlockState(bank).isAir() || !level.getBlockState(bank.above()).isAir()) continue;
                    double dist = bank.distSqr(at);
                    if (dist < best) {
                        best = dist;
                        water = p.immutable();
                        shore = bank.immutable();
                    }
                }
            }
        }
        return water != null;
    }

    private static int fish(ServerPlayer sp) {
        return ProductionTask.count(sp, Items.COD) + ProductionTask.count(sp, Items.SALMON)
                + ProductionTask.count(sp, Items.TROPICAL_FISH) + ProductionTask.count(sp, Items.PUFFERFISH);
    }

    private String line() {
        return "Caught " + caught + (caught == 1 ? " fish" : " fish") + ".";
    }

    private TaskState end(boolean ok, String line) {
        var sp = TaskContext.serverPlayer(player);
        if (sp != null && sp.fishing != null) sp.fishing.discard();
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        outcome = line;
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(ok ? line : "I couldn't fish: " + line + ".");
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
        var sp = TaskContext.serverPlayer(player);
        if (sp != null && sp.fishing != null) sp.fishing.discard();
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
    }
}
