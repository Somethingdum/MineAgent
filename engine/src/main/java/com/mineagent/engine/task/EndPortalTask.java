package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Interaction;
import com.mineagent.engine.pathing.execute.PlayerNav;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * The End portal in a stronghold opened (SMH plan 70: the way to the dragon, when its owner asks): every frame round it
 * without an eye given one, as a player puts them in, from outside the ring (lava lies under its middle); with "go to
 * the End", in through it once it is open. It says how many eyes it lacks rather than guess.
 */
public class EndPortalTask extends CompanionTask<EndPortalTask.PortalRecord> {
    public static final class PortalRecord extends TaskRecord {
        /** In through it once it is open ("go to the End"); else just open it. */
        public final boolean go;

        public PortalRecord(String toolCallId, boolean go) {
            super(toolCallId);
            this.go = go;
        }
    }

    private enum Phase { FIND, FILL, ENTER, DONE }

    private PlayerNav nav;
    private Phase phase = Phase.FIND;
    private final List<BlockPos> frames = new ArrayList<>();
    private BlockPos centre;
    private int index, ticks, filled;
    private String outcome = "";

    public EndPortalTask(AgentPlayer player, PortalRecord record) {
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
            ticks++;
            if (level.dimension() == Level.END) return end(true, record.go ? "In the End." : null);
            switch (phase) {
                case FIND -> {
                    BlockPos at = sp.blockPosition();
                    int x = 0, y = 0, z = 0;
                    for (BlockPos p : BlockPos.betweenClosed(at.offset(-24, -8, -24), at.offset(24, 8, 24))) {
                        if (!level.getBlockState(p).is(Blocks.END_PORTAL_FRAME)) continue;
                        frames.add(p.immutable());
                        x += p.getX();
                        y += p.getY();
                        z += p.getZ();
                    }
                    if (frames.isEmpty()) return end(false, "I'm not by an End portal: \"find the stronghold\" first, then take me to its portal room");
                    centre = new BlockPos(Math.round((float) x / frames.size()), Math.round((float) y / frames.size()), Math.round((float) z / frames.size()));
                    int need = 0;
                    for (BlockPos f : frames) if (!level.getBlockState(f).getValue(EndPortalFrameBlock.HAS_EYE)) need++;
                    if (open(level)) {
                        phase = record.go ? Phase.ENTER : Phase.DONE;
                        if (!record.go) return end(true, null);
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    int eyes = sp.getInventory().countItem(Items.ENDER_EYE);
                    if (eyes < need) return end(false, "the portal needs " + need + (need == 1 ? " eye" : " eyes") + " of ender and I have " + eyes);
                    // Nearest first, so it walks round the ring once.
                    frames.sort(java.util.Comparator.comparingDouble(f -> f.distSqr(sp.blockPosition())));
                    phase = Phase.FILL;
                    index = 0;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case FILL -> {
                    if (open(level)) {
                        say("The End portal is open.");
                        if (!record.go) return end(true, null);
                        phase = Phase.ENTER;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    while (index < frames.size() && level.getBlockState(frames.get(index)).getValue(EndPortalFrameBlock.HAS_EYE)) index++;
                    if (index >= frames.size()) {
                        if (ticks > 40) return end(false, "every frame has its eye and it didn't open: the frames don't all face in");
                        return TaskState.RUNNING;
                    }
                    BlockPos f = frames.get(index);
                    // From outside the ring: the cell beside the frame away from the middle.
                    int ox = Math.abs(f.getX() - centre.getX()) >= 2 ? Integer.signum(f.getX() - centre.getX()) : 0;
                    int oz = Math.abs(f.getZ() - centre.getZ()) >= 2 ? Integer.signum(f.getZ() - centre.getZ()) : 0;
                    BlockPos out = f.offset(ox, 0, oz);
                    if (f.distToCenterSqr(sp.getEyePosition()) > 4.0 * 4.0) {
                        if (ticks > 600) return end(false, "I couldn't get to the frames");
                        if (ticks % 30 == 1 || !nav.isNavigating()) nav.navigateNear(out.getX(), out.getY(), out.getZ(), 0);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    TaskContext.inputDriver(player).clear();
                    if (!TaskContext.selectInventoryItem(player, "minecraft:ender_eye")) return end(false, "I ran out of eyes of ender");
                    Interaction.interactBlock(sp, f, InteractionHand.MAIN_HAND);
                    if (level.getBlockState(f).getValue(EndPortalFrameBlock.HAS_EYE)) {
                        filled++;
                        index++;
                        ticks = 0;
                    }
                    return TaskState.RUNNING;
                }
                case ENTER -> {
                    // Off the edge of the frames into the portal, as a player steps in.
                    if (ticks > 400) return end(false, "I couldn't get into the portal");
                    BlockPos in = centre;
                    double dx = in.getX() + 0.5 - sp.getX(), dz = in.getZ() + 0.5 - sp.getZ();
                    if (dx * dx + dz * dz > 3.0 * 3.0) {
                        if (ticks % 30 == 1 || !nav.isNavigating()) nav.navigateNear(in.getX(), in.getY(), in.getZ(), 2);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    sp.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
                    var input = TaskContext.inputDriver(player);
                    input.setForward(1.0f);
                    input.setJumping(sp.onGround() && ticks % 10 == 0);
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

    /** The portal formed: its black inside there among the frames. */
    private boolean open(ServerLevel level) {
        if (centre == null) return false;
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-1, -1, -1), centre.offset(1, 1, 1))) {
            if (level.getBlockState(p).is(Blocks.END_PORTAL)) return true;
        }
        return false;
    }

    private void say(String s) {
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(s);
    }

    private TaskState end(boolean ok, String why) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        phase = Phase.DONE;
        outcome = ok ? (why != null ? why : "The End portal is open" + (filled > 0 ? " (" + filled + (filled == 1 ? " eye" : " eyes") + " put in)" : ""))
                : (why == null ? "it didn't work" : why);
        say(ok ? outcome + (outcome.endsWith(".") ? "" : ".") : "I couldn't open the End portal: " + outcome + ".");
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
