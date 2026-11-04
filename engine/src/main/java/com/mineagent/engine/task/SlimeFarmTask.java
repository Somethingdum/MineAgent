package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.pathing.execute.PlayerNav;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A slime farm worked (SMH plan 70: "highly favoring of slime"): slimes spawn in a slime chunk below Y 40, but never
 * within 24 blocks of a player. So: down into the room first for what is there, every slime in it hunted (the big ones
 * split, and only the smallest drop slimeballs), what they dropped picked up; then away from the room to wait, above
 * it on the surface (or off to one side where the ground is low), a minute at a time, and down again. Until it has the
 * slimeballs asked for, or twelve rounds.
 */
public class SlimeFarmTask extends CompanionTask<SlimeFarmTask.SlimeRecord> {
    public static final class SlimeRecord extends TaskRecord {
        /** The room's corner, floor height and side, and the slimeballs wanted. */
        public final int x0, z0, y, size, balls;

        public SlimeRecord(String toolCallId, int x0, int z0, int y, int size, int balls) {
            super(toolCallId);
            this.x0 = x0;
            this.z0 = z0;
            this.y = y;
            this.size = Math.max(1, size);
            this.balls = Math.max(1, balls);
        }
    }

    /** Slimes spawn no nearer a player than this. */
    static final double NO_SPAWN = 24.0;
    /** A wait between rounds, and the rounds at most. */
    static final int WAIT = 1_200, ROUNDS = 12;

    private enum Phase { TO_WAIT, WAIT, TO_ROOM, HUNT, DONE }

    private PlayerNav nav;
    private Phase phase = Phase.TO_ROOM; // what is in the room already first, then the waits
    private BlockPos waitAt;
    private ProductionTask sub;
    private int ticks, rounds, before;
    private String outcome = "";

    public SlimeFarmTask(AgentPlayer player, SlimeRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
        before = TaskContext.serverPlayer(player).getInventory().countItem(Items.SLIME_BALL);
        record.extendDeadlineTo(Math.max(record.deadline(), TaskContext.serverPlayer(player).level().getGameTime() + 72_000L));
    }

    private int got(ServerPlayer sp) {
        return sp.getInventory().countItem(Items.SLIME_BALL) - before;
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        try {
            if (phase != Phase.HUNT && got(sp) >= record.balls) return end(true);
            ticks++;
            switch (phase) {
                case TO_WAIT -> {
                    if (waitAt == null) waitAt = waitSpot(level);
                    if (waitAt == null) return end(false, "there's nowhere far enough from the room to wait");
                    if (sp.blockPosition().distSqr(waitAt) <= 4) {
                        nav.cancel();
                        phase = Phase.WAIT;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (ticks > 3_600) return end(got(sp) > 0, "I can't get away from the room to let slimes spawn");
                    return walk(waitAt, 1);
                }
                case WAIT -> {
                    TaskContext.inputDriver(player).clear();
                    if (ticks >= WAIT) {
                        phase = Phase.TO_ROOM;
                        ticks = 0;
                    }
                    return TaskState.RUNNING;
                }
                case TO_ROOM -> {
                    if (inRoom(sp.blockPosition())) {
                        nav.cancel();
                        int left = Math.max(1, record.balls - got(sp));
                        sub = new ProductionTask(player, new ProductionTask.ProduceRecord(record.toolCallId() + ":round" + rounds,
                                "minecraft:slime_ball", left, null, false, true));
                        sub.start();
                        phase = Phase.HUNT;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (ticks > 3_600) return end(got(sp) > 0, "I can't get down to the room");
                    int mid = record.size / 2;
                    return walk(new BlockPos(record.x0 + mid, record.y, record.z0 + mid), 2);
                }
                case HUNT -> {
                    TaskState st = sub.tick();
                    if (st == TaskState.RUNNING && ticks < 2_400) return TaskState.RUNNING;
                    if (st == TaskState.RUNNING) sub.interrupt();
                    sub = null;
                    if (got(sp) >= record.balls) return end(true);
                    if (++rounds >= ROUNDS) return end(got(sp) > 0, rounds + " rounds");
                    phase = Phase.TO_WAIT;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                default -> {
                    return end(true);
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    private boolean inRoom(BlockPos p) {
        return p.getX() >= record.x0 && p.getX() < record.x0 + record.size && p.getZ() >= record.z0 && p.getZ() < record.z0 + record.size
                && p.getY() >= record.y && p.getY() <= record.y + 2;
    }

    /**
     * Where to wait: the surface over the room's middle when every cell of the room is more than 24 blocks from it;
     * else the surface further off, in rings, the nearest that is.
     */
    BlockPos waitSpot(ServerLevel level) {
        int cx = record.x0 + record.size / 2, cz = record.z0 + record.size / 2;
        for (int r = 0; r <= 48; r += 4) {
            for (int dx = -r; dx <= r; dx += 4) {
                for (int dz = -r; dz <= r; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = cx + dx, z = cz + dz;
                    if (!level.isLoaded(new BlockPos(x, record.y, z))) continue;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos p = new BlockPos(x, y, z);
                    if (!level.getFluidState(p.below()).isEmpty()) continue;
                    if (nearestRoomCell(p) > NO_SPAWN + 1.0) return p;
                }
            }
        }
        return null;
    }

    /** How far a spot is from the nearest cell of the room a slime could spawn in. */
    double nearestRoomCell(BlockPos p) {
        double nx = Math.max(record.x0, Math.min(record.x0 + record.size - 1, p.getX()));
        double nz = Math.max(record.z0, Math.min(record.z0 + record.size - 1, p.getZ()));
        double ny = Math.max(record.y, Math.min(record.y + 2, p.getY()));
        double dx = p.getX() - nx, dy = p.getY() - ny, dz = p.getZ() - nz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private TaskState walk(BlockPos to, int within) {
        if (ticks % 40 == 1) nav.navigateNear(to.getX(), to.getY(), to.getZ(), within);
        nav.tick();
        return TaskState.RUNNING;
    }

    private TaskState end(boolean ok) {
        return end(ok, null);
    }

    private TaskState end(boolean ok, String why) {
        if (nav != null) nav.cancel();
        if (sub != null) sub.interrupt();
        sub = null;
        TaskContext.inputDriver(player).clear();
        phase = Phase.DONE;
        ServerPlayer sp = TaskContext.serverPlayer(player);
        int n = got(sp);
        outcome = "Got " + n + (n == 1 ? " slimeball" : " slimeballs") + " from the slime farm" + (why == null ? "" : "; " + why);
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
        if (sub != null) sub.interrupt();
        sub = null;
        TaskContext.inputDriver(player).clear();
    }
}
